package com.example

import com.example.core.LocalStreamProxyServer
import com.example.core.ProviderCatalog
import com.example.core.StreamFilters
import com.example.core.VidZeeResolver
import com.example.data.ConfigRepository
import com.example.model.AppConfig
import com.example.model.MediaType
import com.example.model.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbEmbedCoreTest {

    @Test
    fun buildStreamUrl_movieAndTv_encodesServerCorrectly() {
        assertEquals(
            "https://core.vidzee.wtf/streams/movie/550?s=dcloud&e=0",
            VidZeeResolver.buildStreamUrl("550", MediaType.MOVIE, sr = "dcloud")
        )
        assertEquals(
            "https://core.vidzee.wtf/streams/tv/1396/1/1?s=dcloud&e=0",
            VidZeeResolver.buildStreamUrl("1396", MediaType.TV, season = 1, episode = 1, sr = "dcloud")
        )
        assertEquals(
            "https://core.vidzee.wtf/streams/movie/550?s=v6%3AHindi&e=0",
            VidZeeResolver.buildStreamUrl("550", MediaType.MOVIE, sr = "v6:Hindi")
        )
    }

    @Test
    fun normalizeStreams_mapsPlaintextAndRejectsEncryptedOrInvalid() {
        val payload = """{"language":"Auto","url":"https://cdn2.1shows.app/movie/abc/index.m3u8","headers":{}}"""
        val streams = VidZeeResolver.normalizeStreams(payload, "dcloud")
        assertEquals(1, streams.size)
        assertEquals("dcloud", streams[0].server)
        assertEquals("Auto", streams[0].language)
        assertEquals("https://player.vidzee.wtf/", streams[0].headers["Referer"])

        assertTrue(VidZeeResolver.normalizeStreams("""{"error":"Unknown server"}""", "nope").isEmpty())
        assertTrue(VidZeeResolver.normalizeStreams("""{"c":"encrypted_blob"}""", "dcloud").isEmpty())
        assertTrue(VidZeeResolver.normalizeStreams("""{"url":"not-a-url"}""", "dcloud").isEmpty())
        assertTrue(VidZeeResolver.normalizeStreams(null, "dcloud").isEmpty())
    }

    @Test
    fun looksLikeImage_detectsPngAndJpegHeaders_allowsMpegTs() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val ts = byteArrayOf(0x47, 0x40, 0x00, 0x10)

        assertTrue(VidZeeResolver.looksLikeImage(png))
        assertTrue(VidZeeResolver.looksLikeImage(jpeg))
        assertFalse(VidZeeResolver.looksLikeImage(ts))
        assertFalse(VidZeeResolver.looksLikeImage(ByteArray(0)))
        assertFalse(VidZeeResolver.looksLikeImage(null))
    }

    @Test
    fun firstPlaylistEntry_resolvesRelativeAndAbsoluteUrls() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1441498\nindex-v1-a1.m3u8"
        assertEquals(
            "https://cdn1.1shows.app/e/Abc/index-v1-a1.m3u8",
            VidZeeResolver.firstPlaylistEntry(master, "https://cdn1.1shows.app/e/Abc/master.m3u8")
        )

        val variant = "#EXTM3U\n#EXTINF:10.010,\nhttps://p1.ipstatp.com/origin/x/abc"
        assertEquals(
            "https://p1.ipstatp.com/origin/x/abc",
            VidZeeResolver.firstPlaylistEntry(variant, "https://cdn1.1shows.app/e/Abc/index-v1-a1.m3u8")
        )
        assertNull(VidZeeResolver.firstPlaylistEntry("#EXTM3U\n#EXT-X-ENDLIST\n", "https://x.test/a.m3u8"))
    }

    @Test
    fun isEnglishish_prefersEnglishAndAuto() {
        assertTrue(VidZeeResolver.isEnglishish("English"))
        assertTrue(VidZeeResolver.isEnglishish("Auto"))
        assertTrue(VidZeeResolver.isEnglishish("unknown"))
        assertTrue(VidZeeResolver.isEnglishish(""))
        assertFalse(VidZeeResolver.isEnglishish("Hindi"))
        assertFalse(VidZeeResolver.isEnglishish("Vietnamese"))
    }

    @Test
    fun rewriteM3u8_routesVariantPlaylistsAndSegmentsThroughProxyWithHeaders() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:10.0,
            https://p1.ipstatp.com/origin/x/seg1
            seg2.ts
        """.trimIndent()

        val rewritten = LocalStreamProxyServer.rewriteM3u8(
            content = playlist,
            targetUrl = "https://cdn1.1shows.app/e/Abc/index.m3u8",
            baseProxyUrl = "http://127.0.0.1:8787",
            headers = mapOf("Referer" to "https://player.vidzee.wtf/")
        )

        assertTrue(rewritten.contains("http://127.0.0.1:8787/ts-proxy?url=https%3A%2F%2Fcdn1.1shows.app%2Fe%2FAbc%2Fkey.bin"))
        assertTrue(rewritten.contains("http://127.0.0.1:8787/ts-proxy?url=https%3A%2F%2Fp1.ipstatp.com%2Forigin%2Fx%2Fseg1"))
        assertTrue(rewritten.contains("http://127.0.0.1:8787/ts-proxy?url=https%3A%2F%2Fcdn1.1shows.app%2Fe%2FAbc%2Fseg2.ts"))
        assertTrue(rewritten.contains("headers="))
    }

    @Test
    fun providerCatalog_and_configNormalization_matchUpstreamRules() {
        assertEquals(7, ProviderCatalog.PROVIDER_NAMES.size)
        assertFalse("vidzeecrypto" in ProviderCatalog.PROVIDER_NAMES)
        assertEquals("enableVidzeeProvider", ProviderCatalog.enableFlagFor("vidzee"))
        assertEquals("ENABLE_MP4HYDRA_PROVIDER", ProviderCatalog.enableEnvFor("mp4hydra"))

        assertEquals(listOf("abc", "def"), ConfigRepository.parseCookies("""["ui=abc","def"]"""))
        assertEquals(listOf("abc"), ConfigRepository.parseCookies("ui=abc;abc"))
        assertEquals(listOf("a", "b", "c"), ConfigRepository.normalizeTmdbKeys(listOf("a, b\nc;a")))
        assertTrue(ConfigRepository.normalizeTmdbKeys(emptyList(), legacyKey = "legacy", clearedByUser = true).isEmpty())
        assertEquals(listOf("legacy"), ConfigRepository.normalizeTmdbKeys(emptyList(), legacyKey = "legacy", clearedByUser = false))
    }

    @Test
    fun streamFilters_filtersMinQualityAndExcludedCodecs() {
        val s1 = StreamItem("1", "s1", "s1", "https://x/1.m3u8", "https://x/1.m3u8", "480p", "VidZee")
        val s2 = StreamItem("2", "s2", "s2", "https://x/2.m3u8", "https://x/2.m3u8", "1080p", "VidZee", codecs = listOf("DV"))
        val s3 = StreamItem("3", "s3", "s3", "https://x/3.m3u8", "https://x/3.m3u8", "1080p", "VidZee", codecs = listOf("H264"))

        val filtered = StreamFilters.applyFilters(
            streams = listOf(s1, s2, s3),
            filterLabel = "aggregate",
            minQualitiesRaw = "720p",
            excludeCodecsRaw = """{"excludeDV":true,"excludeHDR":false}"""
        )
        assertEquals(1, filtered.size)
        assertEquals("3", filtered[0].id)
    }

    @Test
    fun defaultConfig_enablesProxyByDefault() {
        val cfg: AppConfig = ConfigRepository.defaultConfig("")
        assertTrue(cfg.enableProxy)
    }
}
