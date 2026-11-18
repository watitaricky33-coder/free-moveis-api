package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ProviderCatalog
import com.example.data.AuthRepository
import com.example.data.ConfigRepository
import com.example.data.StreamRepository
import com.example.model.AppConfig
import com.example.model.AuthState
import com.example.model.FunctionalCheckState
import com.example.model.MediaType
import com.example.model.ProviderStatusItem
import com.example.model.RuntimeMetrics
import com.example.model.StreamResolveResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StreamSearchUiState {
    data object Idle : StreamSearchUiState
    data object Loading : StreamSearchUiState
    data class Success(val result: StreamResolveResult) : StreamSearchUiState
    data class Error(val message: String) : StreamSearchUiState
}

data class AdminFeedback(
    val message: String = "",
    val isError: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configRepo = ConfigRepository(application.applicationContext)
    private val authRepo = AuthRepository(application.applicationContext)
    val streamRepo = StreamRepository()

    val config: StateFlow<AppConfig> = configRepo.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConfigRepository.defaultConfig())

    val authState: StateFlow<AuthState> = authRepo.authStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState())

    val metrics: StateFlow<RuntimeMetrics> = streamRepo.metrics

    private val _searchState = MutableStateFlow<StreamSearchUiState>(StreamSearchUiState.Idle)
    val searchState: StateFlow<StreamSearchUiState> = _searchState.asStateFlow()

    private val _tmdbIdInput = MutableStateFlow("550")
    val tmdbIdInput: StateFlow<String> = _tmdbIdInput.asStateFlow()

    private val _mediaType = MutableStateFlow(MediaType.MOVIE)
    val mediaType: StateFlow<MediaType> = _mediaType.asStateFlow()

    private val _seasonInput = MutableStateFlow("1")
    val seasonInput: StateFlow<String> = _seasonInput.asStateFlow()

    private val _episodeInput = MutableStateFlow("1")
    val episodeInput: StateFlow<String> = _episodeInput.asStateFlow()

    private val _selectedProvider = MutableStateFlow("all")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _probePlayability = MutableStateFlow(true)
    val probePlayability: StateFlow<Boolean> = _probePlayability.asStateFlow()

    private val _providerStatuses = MutableStateFlow<List<ProviderStatusItem>>(emptyList())
    val providerStatuses: StateFlow<List<ProviderStatusItem>> = _providerStatuses.asStateFlow()

    private val _functionalCheckSummary = MutableStateFlow("")
    val functionalCheckSummary: StateFlow<String> = _functionalCheckSummary.asStateFlow()

    private val _isRunningChecks = MutableStateFlow(false)
    val isRunningChecks: StateFlow<Boolean> = _isRunningChecks.asStateFlow()

    private val _adminFeedback = MutableStateFlow(AdminFeedback())
    val adminFeedback: StateFlow<AdminFeedback> = _adminFeedback.asStateFlow()

    private val _lockoutSecondsRemaining = MutableStateFlow(0L)
    val lockoutSecondsRemaining: StateFlow<Long> = _lockoutSecondsRemaining.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    init {
        viewModelScope.launch {
            authRepo.ensureSeeded()
            config.collect { cfg ->
                streamRepo.ensureProxyStarted(cfg)
                if (_providerStatuses.value.isEmpty()) {
                    _providerStatuses.value = streamRepo.buildProviderStatusList(cfg)
                } else {
                    val existing = _providerStatuses.value.associateBy { it.name }
                    _providerStatuses.value = streamRepo.buildProviderStatusList(cfg).map { fresh ->
                        val prev = existing[fresh.name]
                        if (prev != null) {
                            fresh.copy(
                                functionalState = prev.functionalState,
                                notes = if (prev.functionalState != FunctionalCheckState.IDLE) prev.notes else fresh.notes
                            )
                        } else fresh
                    }
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                val lockedUntil = authState.value.lockedUntilMs
                val remain = ((lockedUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _lockoutSecondsRemaining.value = remain
                delay(1000L)
            }
        }
    }

    fun toggleTheme() {
        _darkTheme.value = !_darkTheme.value
    }

    fun updateTmdbId(value: String) {
        _tmdbIdInput.value = value
    }

    fun updateMediaType(type: MediaType) {
        _mediaType.value = type
        if (type == MediaType.TV && _tmdbIdInput.value == "550") {
            _tmdbIdInput.value = "1396"
        } else if (type == MediaType.MOVIE && _tmdbIdInput.value == "1396") {
            _tmdbIdInput.value = "550"
        }
    }

    fun updateSeason(value: String) {
        _seasonInput.value = value
    }

    fun updateEpisode(value: String) {
        _episodeInput.value = value
    }

    fun updateSelectedProvider(provider: String) {
        _selectedProvider.value = provider
    }

    fun updateProbePlayability(enabled: Boolean) {
        _probePlayability.value = enabled
    }

    fun applyQuickPreset(tmdbId: String, type: MediaType, season: String = "1", episode: String = "1") {
        _tmdbIdInput.value = tmdbId
        _mediaType.value = type
        _seasonInput.value = season
        _episodeInput.value = episode
        resolveStreams()
    }

    fun resolveStreams() {
        val tmdbId = _tmdbIdInput.value.trim()
        val type = _mediaType.value
        val season = _seasonInput.value.trim().toIntOrNull()
        val episode = _episodeInput.value.trim().toIntOrNull()

        if (!Regex("^\\d+$").matches(tmdbId)) {
            _searchState.value = StreamSearchUiState.Error("TMDB ID must be numeric (e.g. 550 or 1396)")
            return
        }
        if (type == MediaType.TV && (season == null || season <= 0 || episode == null || episode <= 0)) {
            _searchState.value = StreamSearchUiState.Error("TV shows require positive numeric season and episode")
            return
        }

        _searchState.value = StreamSearchUiState.Loading
        viewModelScope.launch {
            runCatching {
                streamRepo.resolveStreams(
                    tmdbId = tmdbId,
                    mediaType = type,
                    season = season,
                    episode = episode,
                    providerFilter = _selectedProvider.value,
                    config = config.value,
                    runPlayabilityProbe = _probePlayability.value
                )
            }.onSuccess { result ->
                _searchState.value = StreamSearchUiState.Success(result)
            }.onFailure { err ->
                _searchState.value = StreamSearchUiState.Error(err.message ?: "Failed to resolve streams")
            }
        }
    }

    fun refreshStatus() {
        streamRepo.refreshUptime(config.value)
        _providerStatuses.value = streamRepo.buildProviderStatusList(config.value)
    }

    fun restartLocalProxy() {
        streamRepo.restartLocalProxy(config.value)
        _adminFeedback.value = AdminFeedback("Local /m3u8-proxy restarted on port ${streamRepo.proxyServer.port}")
    }

    fun runFunctionalChecks() {
        if (_isRunningChecks.value) return
        _isRunningChecks.value = true
        _functionalCheckSummary.value = "Running checks against TMDB 293660..."
        viewModelScope.launch {
            val cfg = config.value
            val currentList = streamRepo.buildProviderStatusList(cfg).toMutableList()
            _providerStatuses.value = currentList
            var completed = 0
            var passed = 0

            for (idx in currentList.indices) {
                val item = currentList[idx]
                currentList[idx] = item.copy(functionalState = FunctionalCheckState.RUNNING, notes = "Checking...")
                _providerStatuses.value = currentList.toList()

                val (state, note) = streamRepo.checkProviderFunctional(item.name, cfg, "293660")
                if (state == FunctionalCheckState.OK) passed++
                completed++
                currentList[idx] = item.copy(functionalState = state, notes = note)
                _providerStatuses.value = currentList.toList()
                _functionalCheckSummary.value = "Progress: $completed/${currentList.size} (passed: $passed)"
            }
            _functionalCheckSummary.value = "Checks complete: $passed/${currentList.size} passed"
            _isRunningChecks.value = false
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            when (val res = authRepo.login(username, password)) {
                is AuthRepository.LoginResult.Success -> {
                    _adminFeedback.value = AdminFeedback("Authenticated as $username")
                }
                is AuthRepository.LoginResult.InvalidCredentials -> {
                    _adminFeedback.value = AdminFeedback(
                        "Invalid credentials (${res.remainingAttempts} attempts left)",
                        isError = true
                    )
                }
                is AuthRepository.LoginResult.Locked -> {
                    _adminFeedback.value = AdminFeedback(
                        "Locked due to excessive attempts. Try again in ${res.retryAfterSeconds}s",
                        isError = true
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
            _adminFeedback.value = AdminFeedback("Logged out")
        }
    }

    fun changePassword(oldPw: String, newPw: String) {
        viewModelScope.launch {
            authRepo.changePassword(oldPw, newPw)
                .onSuccess {
                    _adminFeedback.value = AdminFeedback("Password updated")
                }
                .onFailure { err ->
                    _adminFeedback.value = AdminFeedback(err.message ?: "Update failed", isError = true)
                }
        }
    }

    fun saveConfig(updated: AppConfig) {
        viewModelScope.launch {
            configRepo.saveConfig(updated)
            _adminFeedback.value = AdminFeedback("Overrides saved")
        }
    }

    fun toggleProviderEnabled(name: String) {
        val cfg = config.value
        val nextEnabled = !ProviderCatalog.isProviderEnabled(cfg, name)
        var nextCfg = ProviderCatalog.withProviderEnabled(cfg, name, nextEnabled)
        if (!nextEnabled) {
            nextCfg = nextCfg.copy(defaultProviders = nextCfg.defaultProviders.filterNot { it.equals(name, true) })
        }
        saveConfig(nextCfg)
    }

    fun toggleProviderDefault(name: String) {
        val cfg = config.value
        val lower = name.lowercase()
        val existing = cfg.defaultProviders.map { it.lowercase() }
        val nextDefaults = if (lower in existing) existing - lower else existing + lower
        val nextCfg = ProviderCatalog.withProviderEnabled(
            cfg.copy(defaultProviders = nextDefaults),
            lower,
            enabled = if (lower !in existing) true else ProviderCatalog.isProviderEnabled(cfg, lower)
        )
        saveConfig(nextCfg)
    }

    fun resetConfigToDefaults() {
        viewModelScope.launch {
            configRepo.resetToDefaults()
            _adminFeedback.value = AdminFeedback("All settings reset to defaults")
        }
    }

    fun clearFeedback() {
        _adminFeedback.value = AdminFeedback()
    }

    fun configPrettyJson(): String = configRepo.toPrettyJson(config.value)

    override fun onCleared() {
        super.onCleared()
        streamRepo.proxyServer.stop()
    }
}
