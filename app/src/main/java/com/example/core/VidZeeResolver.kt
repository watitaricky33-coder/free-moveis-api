package com.example.core

import com.example.model.MediaType
import com.example.model.ProbeStatus
import com.example.model.StreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object VidZeeResolver {
    const val API_BASE = "https://core.vidzee.wtf"
    const val EMBED_ORIGIN = "https://player.vidzee.wtf"
    const val STREAM_REFERER = "$EMBED_ORIGIN/"
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val DEFAULT_SERVERS = listOf("dcloud", "tik", "ipcloud", "v6:Hindi")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val GIF87_MAGIC = "GIF87a".toByteArray(StandardCharsets.US_ASCII)
    private val GIF89_MAGIC = "GIF89a".toByteArray(StandardCharsets.US_ASCII)

    fun encodeParam(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    fun buildStreamUrl(
        tmdbId: String,
        type: MediaType,
        season: Int? = null,
        episode: Int? = null,
        sr: String,
        apiBase: String = API_BASE
    ): String {
        val path = if (type == MediaType.TV) {
            "/streams/tv/${encodeParam(tmdbId)}/${encodeParam((season ?: 1).toString())}/${encodeParam((episode ?: 1).toString())}"
        } else {
            "/streams/movie/${encodeParam(tmdbId)}"
        }
        return "$apiBase$path?s=${encodeParam(sr)}&e=0"
    }

    fun normalizeStreams(rawJson: String?, sr: String): List<StreamItem> {
        if (rawJson.isNullOrBlank()) return emptyList()
        val obj: JsonObject = runCatching {
            json.parseToJsonElement(rawJson).jsonObject
        }.getOrNull() ?: return emptyList()

        val url = runCatching { obj["url"]?.jsonPrimitive?.content?.trim() }.getOrNull().orEmpty()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return emptyList()
        }

        val language = runCatching { obj["language"]?.jsonPrimitive?.content?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: "unknown"

        val upstreamHeaders = buildMap {
            val hObj = runCatching { obj["headers"]?.jsonObject }.getOrNull()
            if (hObj != null) {
                for ((k, v) in hObj) {
                    val valStr = runCatching { v.jsonPrimitive.content }.getOrNull()
                    if (!valStr.isNullOrBlank()) put(k, valStr)
                }
            }
        }

        val mergedHeaders = buildMap {
            put("Referer", STREAM_REFERER)
            putAll(upstreamHeaders)
        }

        val quality = "720p"
        val displayTitle = "VidZee $sr - $quality - $language"

        return listOf(
            StreamItem(
                id = "vidzee-$sr-${url.hashCode()}",
                name = displayTitle,
                title = displayTitle,
                url = url,
                originalUrl = url,
                quality = quality,
                provider = "VidZee",
                server = sr,
                language = language,
                type = "hls",
                headers = mergedHeaders,
                probeStatus = ProbeStatus.UNCHECKED
            )
        )
    }

    fun isEnglishish(language: String?): Boolean {
        val l = language?.trim()?.lowercase().orEmpty()
        if (l.isEmpty() || l == "unknown" || l == "original" || l == "default" || l == "auto") {
            return true
        }
        return Regex("english|^en$|^eng$").containsMatchIn(l)
    }

    fun firstPlaylistEntry(text: String?, baseUrl: String): String? {
        if (text.isNullOrBlank()) return null
        val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return null
        for (rawLine in text.lineSequence()) {
            val entry = rawLine.trim()
            if (entry.isEmpty() || entry.startsWith("#")) continue
            val resolved = runCatching { baseUri.resolve(entry).toString() }.getOrNull()
            if (resolved != null) return resolved
        }
        return null
    }

    fun looksLikeImage(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.isEmpty()) return false
        return startsWith(bytes, PNG_MAGIC) ||
            startsWith(bytes, JPEG_MAGIC) ||
            startsWith(bytes, GIF87_MAGIC) ||
            startsWith(bytes, GIF89_MAGIC)
    }

    private fun startsWith(data: ByteArray, prefix: ByteArray): Boolean {
        if (data.size < prefix.size) return false
        for (i in prefix.indices) {
            if (data[i] != prefix[i]) return false
        }
        return true
    }

    suspend fun fetchServerStreams(
        client: OkHttpClient,
        tmdbId: String,
        type: MediaType,
        season: Int?,
        episode: Int?,
        sr: String,
        apiBase: String = API_BASE
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        val requestUrl = buildStreamUrl(tmdbId, type, season, episode, sr, apiBase)
        val req = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", UA)
            .header("Referer", STREAM_REFERER)
            .header("Origin", EMBED_ORIGIN)
            .get()
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string()
                normalizeStreams(body, sr)
            }
        }.getOrElse { emptyList() }
    }

    suspend fun getAllStreams(
        client: OkHttpClient,
        tmdbId: String,
        type: MediaType,
        season: Int?,
        episode: Int?,
        servers: List<String> = DEFAULT_SERVERS
    ): List<StreamItem> = coroutineScope {
        servers.map { sr ->
            async { fetchServerStreams(client, tmdbId, type, season, episode, sr) }
        }.awaitAll().flatten()
    }

    suspend fun probePlayability(
        client: OkHttpClient,
        url: String,
        extraHeaders: Map<String, String>
    ): ProbeStatus = withContext(Dispatchers.IO) {
        runCatching {
            var playlist = fetchText(client, url, extraHeaders) ?: return@withContext ProbeStatus.UNREACHABLE
            if (!playlist.contains("#EXTM3U")) return@withContext ProbeStatus.UNREACHABLE

            var entryUrl = firstPlaylistEntry(playlist, url) ?: return@withContext ProbeStatus.UNREACHABLE
            if (playlist.contains("#EXT-X-STREAM-INF")) {
                playlist = fetchText(client, entryUrl, extraHeaders) ?: return@withContext ProbeStatus.UNREACHABLE
                if (!playlist.contains("#EXTM3U")) return@withContext ProbeStatus.UNREACHABLE
                entryUrl = firstPlaylistEntry(playlist, entryUrl) ?: return@withContext ProbeStatus.UNREACHABLE
            }

            val segReqBuilder = Request.Builder()
                .url(entryUrl)
                .header("User-Agent", UA)
                .header("Referer", STREAM_REFERER)
                .header("Range", "bytes=0-15")
            for ((k, v) in extraHeaders) {
                if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Range", ignoreCase = true)) {
                    segReqBuilder.header(k, v)
                }
            }

            client.newCall(segReqBuilder.get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ProbeStatus.UNREACHABLE
                val bytes = resp.body?.bytes() ?: return@withContext ProbeStatus.UNREACHABLE
                if (looksLikeImage(bytes)) ProbeStatus.IMAGE_WRAPPED else ProbeStatus.OPENABLE
            }
        }.getOrElse { ProbeStatus.UNREACHABLE }
    }

    private fun fetchText(
        client: OkHttpClient,
        url: String,
        extraHeaders: Map<String, String>
    ): String? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", STREAM_REFERER)
        for ((k, v) in extraHeaders) {
            if (!k.equals("User-Agent", ignoreCase = true)) {
                builder.header(k, v)
            }
        }
        return client.newCall(builder.get().build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }

    fun pickRecommendedStream(streams: List<StreamItem>): StreamItem? {
        val openable = streams.filter { it.probeStatus == ProbeStatus.OPENABLE }
        val pool = openable.ifEmpty { streams.filter { it.probeStatus != ProbeStatus.IMAGE_WRAPPED && it.probeStatus != ProbeStatus.UNREACHABLE } }
        return pool.firstOrNull { isEnglishish(it.language) } ?: pool.firstOrNull()
    }
}
