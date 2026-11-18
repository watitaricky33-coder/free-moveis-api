package com.example.data

import com.example.core.LocalStreamProxyServer
import com.example.core.ProviderCatalog
import com.example.core.StreamFilters
import com.example.core.VidZeeResolver
import com.example.model.AppConfig
import com.example.model.FunctionalCheckState
import com.example.model.MediaType
import com.example.model.ProbeStatus
import com.example.model.ProviderStatusItem
import com.example.model.RuntimeMetrics
import com.example.model.StreamItem
import com.example.model.StreamResolveResult
import com.example.model.TmdbTitlePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class StreamRepository {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    private var cacheDisabledFlag: Boolean = false

    val proxyServer = LocalStreamProxyServer(
        client = httpClient,
        isCacheDisabled = { cacheDisabledFlag }
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _metrics = MutableStateFlow(RuntimeMetrics())
    val metrics: StateFlow<RuntimeMetrics> = _metrics.asStateFlow()

    fun ensureProxyStarted(config: AppConfig) {
        cacheDisabledFlag = config.disableCache
        val activePort = runCatching {
            if (!proxyServer.isRunning()) proxyServer.start(0) else proxyServer.port
        }.getOrDefault(0)
        _metrics.update {
            it.copy(
                localProxyPort = activePort,
                localProxyRunning = proxyServer.isRunning(),
                activeBackendMode = if (config.apiBaseUrl.isBlank()) "On-Device + /m3u8-proxy" else "Remote (${config.apiBaseUrl})",
                uptimeSeconds = (System.currentTimeMillis() - it.startTimeMs) / 1000L
            )
        }
    }

    fun refreshUptime(config: AppConfig) {
        ensureProxyStarted(config)
        _metrics.update {
            it.copy(
                uptimeSeconds = (System.currentTimeMillis() - it.startTimeMs) / 1000L,
                localProxyPort = proxyServer.port,
                localProxyRunning = proxyServer.isRunning(),
                activeBackendMode = if (config.apiBaseUrl.isBlank()) "On-Device + /m3u8-proxy" else "Remote (${config.apiBaseUrl})"
            )
        }
    }

    fun restartLocalProxy(config: AppConfig) {
        proxyServer.stop()
        ensureProxyStarted(config)
    }

    fun buildProviderStatusList(config: AppConfig): List<ProviderStatusItem> {
        val defaults = config.defaultProviders.map { it.lowercase() }.toSet()
        return ProviderCatalog.PROVIDERS.map { spec ->
            val enabled = ProviderCatalog.isProviderEnabled(config, spec.name)
            val cookieOk = !spec.cookieRequired || config.febboxCookies.isNotEmpty()
            ProviderStatusItem(
                name = spec.name,
                enabled = enabled,
                isDefault = spec.name in defaults,
                cookieRequired = spec.cookieRequired,
                cookieOk = cookieOk,
                functionalState = FunctionalCheckState.IDLE,
                notes = spec.defaultStatusNote
            )
        }
    }

    suspend fun resolveStreams(
        tmdbId: String,
        mediaType: MediaType,
        season: Int?,
        episode: Int?,
        providerFilter: String?,
        config: AppConfig,
        runPlayabilityProbe: Boolean = true
    ): StreamResolveResult = withContext(Dispatchers.IO) {
        ensureProxyStarted(config)
        _metrics.update {
            it.copy(
                requestsTotal = it.requestsTotal + 1,
                streamRequests = it.streamRequests + 1,
                lastRequestAtMs = System.currentTimeMillis(),
                uptimeSeconds = (System.currentTimeMillis() - it.startTimeMs) / 1000L
            )
        }

        val tmdbKey = pickRandomTmdbKey(config)
        val preview = if (tmdbKey != null) fetchTmdbPreview(tmdbId, mediaType, tmdbKey) else null
        if (preview?.imdbId != null) {
            _metrics.update { it.copy(tmdbToImdbLookups = it.tmdbToImdbLookups + 1) }
        }

        val baseRemote = config.apiBaseUrl.trim().trimEnd('/')
        if (baseRemote.isNotEmpty()) {
            val remoteResult = fetchFromRemoteBackend(
                baseRemote = baseRemote,
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
                providerFilter = providerFilter,
                config = config,
                preview = preview
            )
            if (remoteResult != null) {
                return@withContext remoteResult
            }
        }

        val targetProviders: List<String> = when {
            !providerFilter.isNullOrBlank() && !providerFilter.equals("all", ignoreCase = true) -> {
                listOf(providerFilter.lowercase())
            }
            config.defaultProviders.isNotEmpty() -> {
                config.defaultProviders.map { it.lowercase() }
                    .filter { ProviderCatalog.isProviderEnabled(config, it) }
            }
            else -> {
                ProviderCatalog.PROVIDER_NAMES.filter { ProviderCatalog.isProviderEnabled(config, it) }
            }
        }

        val timings = linkedMapOf<String, Long?>()
        val rawStreams = coroutineScope {
            targetProviders.map { provName ->
                async {
                    val start = System.currentTimeMillis()
                    _metrics.update { m ->
                        val nextCalls = m.providerCalls.toMutableMap()
                        nextCalls[provName] = (nextCalls[provName] ?: 0) + 1
                        m.copy(providerCalls = nextCalls)
                    }
                    val streams = when (provName) {
                        "vidzee" -> VidZeeResolver.getAllStreams(
                            client = httpClient,
                            tmdbId = tmdbId,
                            type = mediaType,
                            season = season,
                            episode = episode
                        )
                        else -> emptyList()
                    }
                    timings[provName] = System.currentTimeMillis() - start
                    streams
                }
            }.awaitAll().flatten()
        }

        val filterLabel = providerFilter?.takeIf { it.isNotBlank() && !it.equals("all", true) } ?: "aggregate"
        var filtered = StreamFilters.applyFilters(
            streams = rawStreams,
            filterLabel = filterLabel,
            minQualitiesRaw = config.minQualitiesRaw,
            excludeCodecsRaw = config.excludeCodecsRaw
        )

        if (runPlayabilityProbe && !config.disableUrlValidation && filtered.isNotEmpty()) {
            filtered = coroutineScope {
                filtered.map { stream ->
                    async {
                        val status = VidZeeResolver.probePlayability(httpClient, stream.originalUrl, stream.headers)
                        stream.copy(probeStatus = status)
                    }
                }.awaitAll()
            }
        }

        val finalStreams = if (config.enableProxy && proxyServer.isRunning()) {
            LocalStreamProxyServer.applyProxyToStreams(filtered, proxyServer.baseUrl())
        } else {
            filtered
        }

        _metrics.update { it.copy(streamsReturned = it.streamsReturned + finalStreams.size) }

        StreamResolveResult(
            tmdbId = tmdbId,
            imdbId = preview?.imdbId,
            titlePreview = preview,
            streams = finalStreams,
            providerTimingsMs = timings,
            recommendedStream = VidZeeResolver.pickRecommendedStream(finalStreams),
            resolvedViaRemoteBackend = false
        )
    }

    suspend fun checkProviderFunctional(
        providerName: String,
        config: AppConfig,
        testTmdbId: String = "293660"
    ): Pair<FunctionalCheckState, String> = withContext(Dispatchers.IO) {
        if (!ProviderCatalog.isProviderEnabled(config, providerName)) {
            return@withContext FunctionalCheckState.SKIPPED to "Disabled"
        }
        if (providerName.equals("showbox", ignoreCase = true) &&
            (config.febboxCookies.isEmpty() || config.tmdbApiKeys.isEmpty())
        ) {
            return@withContext FunctionalCheckState.NONE to "Missing FebBox cookie / TMDB key"
        }
        val res = resolveStreams(
            tmdbId = testTmdbId,
            mediaType = MediaType.MOVIE,
            season = null,
            episode = null,
            providerFilter = providerName,
            config = config,
            runPlayabilityProbe = false
        )
        if (res.streams.isNotEmpty()) {
            FunctionalCheckState.OK to "${res.streams.size} streams"
        } else {
            FunctionalCheckState.NONE to "No streams returned"
        }
    }

    private fun pickRandomTmdbKey(config: AppConfig): String? {
        if (config.tmdbApiKeys.isEmpty()) return null
        return config.tmdbApiKeys.random()
    }

    private fun fetchTmdbPreview(tmdbId: String, mediaType: MediaType, apiKey: String): TmdbTitlePreview? {
        val tmdbPathType = if (mediaType == MediaType.MOVIE) "movie" else "tv"
        val detailsReq = Request.Builder()
            .url("https://api.themoviedb.org/3/$tmdbPathType/$tmdbId?api_key=$apiKey")
            .get()
            .build()
        val extReq = Request.Builder()
            .url("https://api.themoviedb.org/3/$tmdbPathType/$tmdbId/external_ids?api_key=$apiKey")
            .get()
            .build()

        val detailsObj = runCatching {
            httpClient.newCall(detailsReq).execute().use { resp ->
                if (!resp.isSuccessful) null
                else json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            }
        }.getOrNull() ?: return null

        val imdbId = runCatching {
            httpClient.newCall(extReq).execute().use { resp ->
                if (!resp.isSuccessful) null
                else json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject["imdb_id"]?.jsonPrimitive?.content
            }
        }.getOrNull()

        val title = runCatching {
            detailsObj["title"]?.jsonPrimitive?.content
                ?: detailsObj["name"]?.jsonPrimitive?.content
                ?: "TMDB #$tmdbId"
        }.getOrDefault("TMDB #$tmdbId")

        val dateStr = runCatching {
            detailsObj["release_date"]?.jsonPrimitive?.content
                ?: detailsObj["first_air_date"]?.jsonPrimitive?.content
                ?: ""
        }.getOrDefault("")
        val year = dateStr.take(4)
        val overview = runCatching {
            detailsObj["overview"]?.jsonPrimitive?.content.orEmpty()
        }.getOrDefault("")

        return TmdbTitlePreview(
            tmdbId = tmdbId,
            title = title,
            releaseYear = year,
            overview = overview,
            imdbId = imdbId
        )
    }

    private fun fetchFromRemoteBackend(
        baseRemote: String,
        tmdbId: String,
        mediaType: MediaType,
        season: Int?,
        episode: Int?,
        providerFilter: String?,
        config: AppConfig,
        preview: TmdbTitlePreview?
    ): StreamResolveResult? {
        val providerSegment = providerFilter?.takeIf { it.isNotBlank() && !it.equals("all", true) }
            ?.let { "/$it" } ?: ""
        val query = if (mediaType == MediaType.TV) {
            "?season=${season ?: 1}&episode=${episode ?: 1}"
        } else ""
        val url = "$baseRemote/api/streams$providerSegment/${mediaType.apiValue}/$tmdbId$query"
        val req = Request.Builder().url(url).get().build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val streamsArr: JsonArray = root["streams"]?.jsonArray ?: JsonArray(emptyList())
                val parsedStreams = streamsArr.mapIndexedNotNull { idx, elem ->
                    val obj: JsonObject = runCatching { elem.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
                    val streamUrl = obj["url"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: "Stream ${idx + 1}"
                    val title = obj["title"]?.jsonPrimitive?.content ?: name
                    val quality = obj["quality"]?.jsonPrimitive?.content ?: "720p"
                    val provider = obj["provider"]?.jsonPrimitive?.content ?: "Remote"
                    val headersMap = runCatching {
                        obj["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
                    }.getOrDefault(emptyMap())
                    val isAlreadyProxied = streamUrl.contains("/m3u8-proxy") || streamUrl.contains("/ts-proxy")
                    StreamItem(
                        id = "remote-$idx-${streamUrl.hashCode()}",
                        name = name,
                        title = title,
                        url = streamUrl,
                        originalUrl = LocalStreamProxyServer.extractOriginalUrl(streamUrl),
                        quality = quality,
                        provider = provider,
                        language = "Auto",
                        headers = headersMap,
                        proxiedUrl = if (isAlreadyProxied) streamUrl else null,
                        probeStatus = ProbeStatus.OPENABLE,
                        isProxied = isAlreadyProxied
                    )
                }

                val finalStreams = if (config.enableProxy && proxyServer.isRunning()) {
                    parsedStreams.map { item ->
                        if (item.isProxied) item
                        else {
                            val proxied = LocalStreamProxyServer.buildProxiedStreamUrl(
                                originalUrl = item.originalUrl,
                                headers = item.headers,
                                baseProxyUrl = proxyServer.baseUrl()
                            )
                            item.copy(url = proxied, proxiedUrl = proxied, isProxied = true)
                        }
                    }
                } else {
                    parsedStreams
                }

                val imdbId = runCatching { root["imdbId"]?.jsonPrimitive?.content }.getOrNull() ?: preview?.imdbId
                _metrics.update { it.copy(streamsReturned = it.streamsReturned + finalStreams.size) }

                StreamResolveResult(
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    titlePreview = preview,
                    streams = finalStreams,
                    providerTimingsMs = emptyMap(),
                    recommendedStream = VidZeeResolver.pickRecommendedStream(finalStreams),
                    resolvedViaRemoteBackend = true
                )
            }
        }.onFailure { e ->
            _metrics.update { it.copy(lastError = "Remote backend error: ${e.message}") }
        }.getOrNull()
    }
}
