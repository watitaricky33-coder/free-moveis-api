package com.example.core

import com.example.model.StreamItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalStreamProxyServer(
    private val client: OkHttpClient,
    private val isCacheDisabled: () -> Boolean
) {
    companion object {
        const val DEFAULT_UA = VidZeeResolver.UA
        private const val MAX_CACHE_ENTRIES = 64
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun urlEncode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

        fun urlDecode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.toString())

        fun inferContentType(upstreamCT: String?, targetUrl: String): String {
            val bad = upstreamCT.isNullOrBlank() ||
                upstreamCT.contains("application/octet-stream", ignoreCase = true) ||
                upstreamCT.contains("application/x-zip", ignoreCase = true) ||
                upstreamCT.contains("application/zip", ignoreCase = true)
            if (!bad) return upstreamCT!!
            val clean = targetUrl.substringBefore('?').lowercase()
            return when {
                clean.endsWith(".mkv") -> "video/x-matroska"
                clean.endsWith(".mp4") -> "video/mp4"
                clean.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
                clean.endsWith(".ts") -> "video/mp2t"
                clean.endsWith(".vtt") -> "text/vtt"
                else -> "application/octet-stream"
            }
        }

        fun extractOriginalUrl(proxyUrl: String): String {
            return runCatching {
                val uri = URI(proxyUrl)
                val query = uri.rawQuery
                if (!query.isNullOrBlank()) {
                    val params = parseQueryParams(query)
                    val urlParam = params["url"]
                    if (!urlParam.isNullOrBlank()) return urlParam
                }
                proxyUrl
            }.getOrDefault(proxyUrl)
        }

        fun parseQueryParams(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrBlank()) return emptyMap()
            val out = linkedMapOf<String, String>()
            for (pair in rawQuery.split('&')) {
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val key = urlDecode(pair.substring(0, idx))
                    val value = urlDecode(pair.substring(idx + 1))
                    out[key] = value
                }
            }
            return out
        }

        fun rewriteM3u8(
            content: String,
            targetUrl: String,
            baseProxyUrl: String,
            headers: Map<String, String>
        ): String {
            val baseUri = runCatching { URI(targetUrl) }.getOrNull()
            val headersJson = json.encodeToString(headers)
            val encodedHeaders = urlEncode(headersJson)
            val lines = content.split("\n")
            val out = ArrayList<String>(lines.size)
            var nextLineIsVariantPlaylist = false

            for (rawLine in lines) {
                val line = rawLine.trimEnd('\r')
                when {
                    line.startsWith("#") -> {
                        if (line.startsWith("#EXT-X-STREAM-INF:")) {
                            nextLineIsVariantPlaylist = true
                            out.add(line)
                        } else if (line.startsWith("#EXT-X-KEY:") || line.startsWith("#EXT-X-MAP:")) {
                            val uriRegex = Regex("URI=\"([^\"]+)\"")
                            val match = uriRegex.find(line)
                            if (match != null && baseUri != null) {
                                val resolved = runCatching { baseUri.resolve(match.groupValues[1]).toString() }
                                    .getOrNull()
                                if (resolved != null) {
                                    val proxied = "$baseProxyUrl/ts-proxy?url=${urlEncode(resolved)}&headers=$encodedHeaders"
                                    out.add(line.replaceRange(match.groups[1]!!.range, proxied))
                                } else {
                                    out.add(line)
                                }
                            } else {
                                out.add(line)
                            }
                        } else if (line.startsWith("#EXT-X-MEDIA:") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF:")) {
                            val uriRegex = Regex("URI=\"([^\"]+)\"")
                            val match = uriRegex.find(line)
                            if (match != null && baseUri != null) {
                                val resolved = runCatching { baseUri.resolve(match.groupValues[1]).toString() }
                                    .getOrNull()
                                if (resolved != null) {
                                    val proxied = "$baseProxyUrl/m3u8-proxy?url=${urlEncode(resolved)}&headers=$encodedHeaders"
                                    out.add(line.replaceRange(match.groups[1]!!.range, proxied))
                                } else {
                                    out.add(line)
                                }
                            } else {
                                out.add(line)
                            }
                        } else {
                            out.add(line)
                        }
                    }

                    line.trim().isNotEmpty() -> {
                        val trimmed = line.trim()
                        val resolved = if (baseUri != null) {
                            runCatching { baseUri.resolve(trimmed).toString() }.getOrNull()
                        } else null

                        if (resolved != null) {
                            val isM3u8 = nextLineIsVariantPlaylist ||
                                Regex("\\.m3u8(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(resolved)
                            nextLineIsVariantPlaylist = false
                            val endpoint = if (isM3u8) "m3u8-proxy" else "ts-proxy"
                            out.add("$baseProxyUrl/$endpoint?url=${urlEncode(resolved)}&headers=$encodedHeaders")
                        } else {
                            nextLineIsVariantPlaylist = false
                            out.add(line)
                        }
                    }

                    else -> out.add(line)
                }
            }
            return out.joinToString("\n")
        }

        fun buildProxiedStreamUrl(
            originalUrl: String,
            headers: Map<String, String>,
            baseProxyUrl: String
        ): String {
            val cleanOriginal = extractOriginalUrl(originalUrl)
            val hParam = if (headers.isNotEmpty()) {
                "&headers=${urlEncode(json.encodeToString(headers))}"
            } else ""

            val host = runCatching { URI(cleanOriginal).host?.lowercase().orEmpty() }.getOrDefault("")
            val isDirectFile = host.contains("pixeldrain.") ||
                host == "video-downloads.googleusercontent.com" ||
                Regex("\\.(mp4|mkv)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(cleanOriginal)

            val route = if (isDirectFile) "ts-proxy" else "m3u8-proxy"
            return "$baseProxyUrl/$route?url=${urlEncode(cleanOriginal)}$hParam"
        }

        fun applyProxyToStreams(streams: List<StreamItem>, baseProxyUrl: String): List<StreamItem> {
            return streams.map { s ->
                val proxied = buildProxiedStreamUrl(s.originalUrl, s.headers, baseProxyUrl)
                s.copy(url = proxied, proxiedUrl = proxied, isProxied = true)
            }
        }
    }

    private data class CachedBytes(val data: ByteArray, val contentType: String)

    private val cache: MutableMap<String, CachedBytes> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedBytes>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBytes>?): Boolean {
                return size > MAX_CACHE_ENTRIES
            }
        }
    )

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    @Volatile
    var port: Int = 0
        private set

    fun baseUrl(): String = "http://127.0.0.1:$port"

    fun isRunning(): Boolean = running.get() && port > 0

    @Synchronized
    fun start(preferredPort: Int = 0): Int {
        if (running.get() && port > 0) return port
        val socket = runCatching {
            ServerSocket(preferredPort, 32, InetAddress.getByName("127.0.0.1"))
        }.getOrElse {
            ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        }
        serverSocket = socket
        port = socket.localPort
        running.set(true)
        val pool = Executors.newCachedThreadPool()
        executor = pool
        pool.submit {
            while (running.get() && !socket.isClosed) {
                val clientSocket = runCatching { socket.accept() }.getOrNull() ?: break
                pool.submit { handleClient(clientSocket) }
            }
        }
        return port
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        port = 0
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 20_000
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawTarget = parts[1]

            val reqHeaders = linkedMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    reqHeaders[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
            }

            if (method != "GET" && method != "HEAD") {
                writeJson(output, 405, """{"error":"method not allowed"}""")
                return
            }

            val qIdx = rawTarget.indexOf('?')
            val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
            val query = if (qIdx >= 0) parseQueryParams(rawTarget.substring(qIdx + 1)) else emptyMap()
            val hostHeader = reqHeaders["host"] ?: "127.0.0.1:$port"
            val currentBase = "http://$hostHeader"

            when (path) {
                "/api/health", "/health" -> {
                    writeJson(output, 200, """{"ok":true,"service":"tmdb-embed-android-proxy"}""")
                }
                "/m3u8-proxy" -> handleM3u8Proxy(output, query, currentBase)
                "/ts-proxy" -> handleTsProxy(output, query, reqHeaders["range"], method == "HEAD")
                "/sub-proxy" -> handleSubProxy(output, query)
                else -> writeJson(output, 404, """{"error":"not found"}""")
            }
        }
    }

    private fun handleM3u8Proxy(output: OutputStream, query: Map<String, String>, currentBase: String) {
        val targetUrl = query["url"]
        if (targetUrl.isNullOrBlank()) {
            writeJson(output, 400, """{"error":"URL parameter required"}""")
            return
        }
        val headers = parseHeadersJson(query["headers"])
        val req = buildUpstreamRequest(targetUrl, headers, range = null, head = false)
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    writeJson(output, resp.code, """{"error":"M3U8 fetch failed: ${resp.code}"}""")
                    return
                }
                val text = resp.body?.string().orEmpty()
                val rewritten = rewriteM3u8(text, targetUrl, currentBase, headers)
                val bytes = rewritten.toByteArray(StandardCharsets.UTF_8)
                writeHeaders(
                    output = output,
                    statusCode = 200,
                    statusText = "OK",
                    contentType = "application/vnd.apple.mpegurl",
                    contentLength = bytes.size.toLong(),
                    extra = mapOf(
                        "Cache-Control" to "no-cache, no-store, must-revalidate",
                        "Access-Control-Allow-Origin" to "*"
                    )
                )
                output.write(bytes)
                output.flush()
            }
        }.onFailure { e ->
            writeJson(output, 500, """{"error":"${e.message?.replace("\"", "'") ?: "proxy error"}"}""")
        }
    }

    private fun handleTsProxy(
        output: OutputStream,
        query: Map<String, String>,
        range: String?,
        headOnly: Boolean
    ) {
        val targetUrl = query["url"]
        if (targetUrl.isNullOrBlank()) {
            writeJson(output, 400, """{"error":"URL parameter required"}""")
            return
        }
        val headers = parseHeadersJson(query["headers"])

        if (!headOnly && range == null && !isCacheDisabled()) {
            val cached = cache[targetUrl]
            if (cached != null) {
                writeHeaders(
                    output = output,
                    statusCode = 200,
                    statusText = "OK",
                    contentType = cached.contentType,
                    contentLength = cached.data.size.toLong(),
                    extra = mapOf(
                        "Accept-Ranges" to "bytes",
                        "Cache-Control" to "public, max-age=3600",
                        "Access-Control-Allow-Origin" to "*"
                    )
                )
                output.write(cached.data)
                output.flush()
                return
            }
        }

        val req = buildUpstreamRequest(targetUrl, headers, range = range, head = headOnly)
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    writeJson(output, resp.code, """{"error":"TS fetch failed: ${resp.code}"}""")
                    return
                }
                val ct = inferContentType(resp.header("Content-Type"), targetUrl)
                val cl = resp.header("Content-Length")?.toLongOrNull()
                val cr = resp.header("Content-Range")
                val statusCode = if (resp.code == 206) 206 else 200
                val statusText = if (statusCode == 206) "Partial Content" else "OK"
                val extra = linkedMapOf(
                    "Accept-Ranges" to (resp.header("Accept-Ranges") ?: "bytes"),
                    "Cache-Control" to "public, max-age=3600",
                    "Access-Control-Allow-Origin" to "*"
                )
                if (!cr.isNullOrBlank()) extra["Content-Range"] = cr

                writeHeaders(
                    output = output,
                    statusCode = statusCode,
                    statusText = statusText,
                    contentType = ct,
                    contentLength = cl,
                    extra = extra
                )
                if (!headOnly) {
                    val bodyStream = resp.body?.byteStream()
                    if (bodyStream != null) {
                        val shouldBufferForCache = range == null && !isCacheDisabled() && (cl == null || cl <= 4 * 1024 * 1024)
                        val mem = if (shouldBufferForCache) ByteArrayOutputStream() else null
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val read = bodyStream.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            mem?.write(buf, 0, read)
                        }
                        if (mem != null && mem.size() > 0 && mem.size() <= 4 * 1024 * 1024) {
                            cache[targetUrl] = CachedBytes(mem.toByteArray(), ct)
                        }
                    }
                }
                output.flush()
            }
        }.onFailure { e ->
            writeJson(output, 500, """{"error":"${e.message?.replace("\"", "'") ?: "ts proxy error"}"}""")
        }
    }

    private fun handleSubProxy(output: OutputStream, query: Map<String, String>) {
        val targetUrl = query["url"]
        if (targetUrl.isNullOrBlank()) {
            writeJson(output, 400, """{"error":"url parameter required"}""")
            return
        }
        val headers = parseHeadersJson(query["headers"])
        val req = buildUpstreamRequest(targetUrl, headers, range = null, head = false)
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    writeJson(output, resp.code, """{"error":"subtitle fetch failed: ${resp.code}"}""")
                    return
                }
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                writeHeaders(
                    output = output,
                    statusCode = 200,
                    statusText = "OK",
                    contentType = resp.header("Content-Type") ?: "text/vtt",
                    contentLength = bytes.size.toLong(),
                    extra = mapOf(
                        "Cache-Control" to "public, max-age=3600",
                        "Access-Control-Allow-Origin" to "*"
                    )
                )
                output.write(bytes)
                output.flush()
            }
        }.onFailure { e ->
            writeJson(output, 500, """{"error":"${e.message?.replace("\"", "'") ?: "sub proxy error"}"}""")
        }
    }

    private fun buildUpstreamRequest(
        targetUrl: String,
        headers: Map<String, String>,
        range: String?,
        head: Boolean
    ): Request {
        val builder = Request.Builder().url(targetUrl)
        builder.header("User-Agent", DEFAULT_UA)
        for ((k, v) in headers) {
            if (!k.equals("Host", ignoreCase = true) && !k.equals("User-Agent", ignoreCase = true)) {
                builder.header(k, v)
            }
        }
        if (!headers.keys.any { it.equals("Referer", ignoreCase = true) }) {
            builder.header("Referer", VidZeeResolver.STREAM_REFERER)
        }
        if (!range.isNullOrBlank()) {
            builder.header("Range", range)
        }
        return if (head) builder.head().build() else builder.get().build()
    }

    private fun parseHeadersJson(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj: JsonObject = json.parseToJsonElement(raw).jsonObject
            obj.mapNotNull { (k, v) ->
                runCatching { k to v.jsonPrimitive.content }.getOrNull()
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun writeHeaders(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        contentLength: Long?,
        extra: Map<String, String>
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n")
        sb.append("Content-Type: ").append(contentType).append("\r\n")
        if (contentLength != null) {
            sb.append("Content-Length: ").append(contentLength).append("\r\n")
        }
        sb.append("Connection: close\r\n")
        for ((k, v) in extra) {
            sb.append(k).append(": ").append(v).append("\r\n")
        }
        sb.append("\r\n")
        output.write(sb.toString().toByteArray(StandardCharsets.US_ASCII))
    }

    private fun writeJson(output: OutputStream, statusCode: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writeHeaders(
            output = output,
            statusCode = statusCode,
            statusText = if (statusCode == 200) "OK" else "Error",
            contentType = "application/json",
            contentLength = bytes.size.toLong(),
            extra = mapOf("Access-Control-Allow-Origin" to "*")
        )
        output.write(bytes)
        output.flush()
    }
}
