package com.example.model

import kotlinx.serialization.Serializable

enum class MediaType(val apiValue: String, val vidzeePath: String, val label: String) {
    MOVIE(apiValue = "movie", vidzeePath = "movie", label = "Movie"),
    TV(apiValue = "series", vidzeePath = "tv", label = "TV Show")
}

enum class ProbeStatus(val label: String) {
    UNCHECKED("Unchecked"),
    PROBING("Probing..."),
    OPENABLE("Playable HLS"),
    IMAGE_WRAPPED("PNG-Wrapped (Skipped)"),
    UNREACHABLE("Unreachable")
}

@Serializable
data class StreamItem(
    val id: String,
    val name: String,
    val title: String,
    val url: String,
    val originalUrl: String,
    val quality: String = "720p",
    val provider: String,
    val server: String = "",
    val language: String = "unknown",
    val type: String = "hls",
    val headers: Map<String, String> = emptyMap(),
    val codecs: List<String> = emptyList(),
    val proxiedUrl: String? = null,
    val probeStatus: ProbeStatus = ProbeStatus.UNCHECKED,
    val isProxied: Boolean = false
)

enum class FunctionalCheckState {
    IDLE, RUNNING, OK, NONE, SKIPPED, FAILED
}

data class ProviderStatusItem(
    val name: String,
    val enabled: Boolean,
    val isDefault: Boolean,
    val cookieRequired: Boolean,
    val cookieOk: Boolean,
    val functionalState: FunctionalCheckState = FunctionalCheckState.IDLE,
    val notes: String = ""
)

@Serializable
data class ExcludeCodecsConfig(
    val excludeDV: Boolean = false,
    val excludeHDR: Boolean = false
)

@Serializable
data class AppConfig(
    val configVersion: Int = 1,
    val apiBaseUrl: String = "",
    val port: Int = 8787,
    val defaultRegion: String? = null,
    val defaultProviders: List<String> = emptyList(),
    val minQualitiesRaw: String? = "all",
    val excludeCodecsRaw: String? = "{\"excludeDV\":false,\"excludeHDR\":false}",
    val tmdbApiKeys: List<String> = emptyList(),
    val febboxCookies: List<String> = emptyList(),
    val enable4khdhubProvider: Boolean = true,
    val enableMoviesmodProvider: Boolean = true,
    val enableMp4hydraProvider: Boolean = true,
    val enableShowboxProvider: Boolean = true,
    val enableUhdmoviesProvider: Boolean = true,
    val enableVidzeeProvider: Boolean = true,
    val enableVixsrcProvider: Boolean = true,
    val disableCache: Boolean = false,
    val enablePStreamApi: Boolean = true,
    val enableProxy: Boolean = true,
    val disableUrlValidation: Boolean = false,
    val disable4khdhubUrlValidation: Boolean = false,
    val showboxCacheDir: String? = null
)

data class RuntimeMetrics(
    val startTimeMs: Long = System.currentTimeMillis(),
    val uptimeSeconds: Long = 0L,
    val requestsTotal: Int = 0,
    val streamRequests: Int = 0,
    val providerCalls: Map<String, Int> = emptyMap(),
    val streamsReturned: Int = 0,
    val tmdbToImdbLookups: Int = 0,
    val lastRequestAtMs: Long? = null,
    val localProxyPort: Int = 0,
    val localProxyRunning: Boolean = false,
    val activeBackendMode: String = "On-Device",
    val lastError: String? = null
)

data class TmdbTitlePreview(
    val tmdbId: String,
    val title: String,
    val releaseYear: String,
    val overview: String,
    val imdbId: String?
)

data class AuthState(
    val isAuthenticated: Boolean = false,
    val username: String = "admin",
    val remainingAttempts: Int = 5,
    val lockedUntilMs: Long = 0L,
    val sessionExpiresAtMs: Long = 0L,
    val isUsingDefaultPassword: Boolean = true
)

data class StreamResolveResult(
    val tmdbId: String,
    val imdbId: String?,
    val titlePreview: TmdbTitlePreview?,
    val streams: List<StreamItem>,
    val providerTimingsMs: Map<String, Long?>,
    val recommendedStream: StreamItem?,
    val resolvedViaRemoteBackend: Boolean
)
