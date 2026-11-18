package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.MainViewModel
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.StatusScreen
import com.example.ui.screens.StreamsScreen
import com.example.ui.theme.TmdbEmbedTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data object StreamsRoute

@Serializable
data object StatusRoute

@Serializable
data object AdminRoute

@Serializable
data class PlayerRoute(
    val streamUrl: String,
    val streamTitle: String,
    val headersJson: String
)

private data class TopLevelNavItem(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val darkTheme by vm.darkTheme.collectAsStateWithLifecycle()
            TmdbEmbedTheme(darkTheme = darkTheme) {
                TmdbEmbedApp(vm = vm, darkTheme = darkTheme)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun TmdbEmbedApp(
    vm: MainViewModel,
    darkTheme: Boolean
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isPlayerRoute = currentDest?.hasRoute<PlayerRoute>() == true

    val activity = LocalContext.current as? Activity
    val widthSizeClass = activity?.let { calculateWindowSizeClass(it).widthSizeClass }
        ?: WindowWidthSizeClass.Compact
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    val tmdbId by vm.tmdbIdInput.collectAsStateWithLifecycle()
    val mediaType by vm.mediaType.collectAsStateWithLifecycle()
    val season by vm.seasonInput.collectAsStateWithLifecycle()
    val episode by vm.episodeInput.collectAsStateWithLifecycle()
    val selectedProvider by vm.selectedProvider.collectAsStateWithLifecycle()
    val probePlayability by vm.probePlayability.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val providerStatuses by vm.providerStatuses.collectAsStateWithLifecycle()
    val checkSummary by vm.functionalCheckSummary.collectAsStateWithLifecycle()
    val isRunningChecks by vm.isRunningChecks.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()
    val lockoutSeconds by vm.lockoutSecondsRemaining.collectAsStateWithLifecycle()
    val adminFeedback by vm.adminFeedback.collectAsStateWithLifecycle()

    val navItems = listOf(
        TopLevelNavItem(
            label = "Streams",
            route = StreamsRoute,
            selectedIcon = Icons.Filled.PlayCircle,
            unselectedIcon = Icons.Outlined.PlayCircle,
            testTag = "nav_streams"
        ),
        TopLevelNavItem(
            label = "Status",
            route = StatusRoute,
            selectedIcon = Icons.Filled.Dns,
            unselectedIcon = Icons.Outlined.Dns,
            testTag = "nav_status"
        ),
        TopLevelNavItem(
            label = "Admin",
            route = AdminRoute,
            selectedIcon = Icons.Filled.AdminPanelSettings,
            unselectedIcon = Icons.Outlined.AdminPanelSettings,
            testTag = "nav_admin"
        )
    )

    fun navigateTopLevel(route: Any) {
        if (route is StatusRoute) vm.refreshStatus()
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!isPlayerRoute) {
                TopAppBar(
                    title = {
                        Text(
                            text = "TMDB Embed API",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = vm::toggleTheme,
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!isPlayerRoute && !useRail) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    navItems.forEach { item ->
                        val selected = when (item.route) {
                            is StreamsRoute -> currentDest?.hasRoute<StreamsRoute>() == true
                            is StatusRoute -> currentDest?.hasRoute<StatusRoute>() == true
                            is AdminRoute -> currentDest?.hasRoute<AdminRoute>() == true
                            else -> false
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isPlayerRoute && useRail) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.weight(0.1f))
                    navItems.forEach { item ->
                        val selected = when (item.route) {
                            is StreamsRoute -> currentDest?.hasRoute<StreamsRoute>() == true
                            is StatusRoute -> currentDest?.hasRoute<StatusRoute>() == true
                            is AdminRoute -> currentDest?.hasRoute<AdminRoute>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            NavHost(
                navController = navController,
                startDestination = StreamsRoute,
                modifier = Modifier.weight(1f)
            ) {
                composable<StreamsRoute> {
                    StreamsScreen(
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        season = season,
                        episode = episode,
                        selectedProvider = selectedProvider,
                        probePlayability = probePlayability,
                        config = config,
                        metrics = metrics,
                        searchState = searchState,
                        onTmdbIdChange = vm::updateTmdbId,
                        onMediaTypeChange = vm::updateMediaType,
                        onSeasonChange = vm::updateSeason,
                        onEpisodeChange = vm::updateEpisode,
                        onProviderChange = vm::updateSelectedProvider,
                        onProbeToggle = vm::updateProbePlayability,
                        onProxyToggle = { enabled ->
                            vm.saveConfig(config.copy(enableProxy = enabled))
                        },
                        onQuickPreset = vm::applyQuickPreset,
                        onResolve = vm::resolveStreams,
                        onPlayStream = { stream ->
                            navController.navigate(
                                PlayerRoute(
                                    streamUrl = stream.url,
                                    streamTitle = stream.title,
                                    headersJson = Json.encodeToString(stream.headers)
                                )
                            )
                        }
                    )
                }

                composable<StatusRoute> {
                    StatusScreen(
                        metrics = metrics,
                        providers = providerStatuses,
                        checkSummary = checkSummary,
                        isRunningChecks = isRunningChecks,
                        onRefreshStatus = vm::refreshStatus,
                        onRestartProxy = vm::restartLocalProxy,
                        onRunFunctionalChecks = vm::runFunctionalChecks
                    )
                }

                composable<AdminRoute> {
                    AdminScreen(
                        authState = authState,
                        lockoutSecondsRemaining = lockoutSeconds,
                        config = config,
                        feedback = adminFeedback,
                        configPrettyJson = vm.configPrettyJson(),
                        onLogin = vm::login,
                        onLogout = vm::logout,
                        onChangePassword = vm::changePassword,
                        onSaveConfig = vm::saveConfig,
                        onToggleProviderEnabled = vm::toggleProviderEnabled,
                        onToggleProviderDefault = vm::toggleProviderDefault,
                        onResetDefaults = vm::resetConfigToDefaults,
                        onRestartProxy = vm::restartLocalProxy,
                        onClearFeedback = vm::clearFeedback
                    )
                }

                composable<PlayerRoute> { entry ->
                    val route = entry.toRoute<PlayerRoute>()
                    PlayerScreen(
                        streamUrl = route.streamUrl,
                        streamTitle = route.streamTitle,
                        headersJson = route.headersJson,
                        baseClient = vm.streamRepo.httpClient,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
