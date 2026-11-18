package com.example.core

import com.example.model.ExcludeCodecsConfig
import com.example.model.StreamItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object StreamFilters {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseQuality(qualityString: String?): Int {
        if (qualityString.isNullOrBlank()) return 0
        val q = qualityString.lowercase()
        if ("auto" in q) return 1080
        if ("4k" in q || "2160" in q) return 2160
        if ("1440" in q) return 1440
        if ("1080" in q) return 1080
        if ("720" in q) return 720
        if ("576" in q) return 576
        if ("480" in q) return 480
        if ("360" in q) return 360
        if ("240" in q) return 240
        val kbps = Regex("(\\d+)k").find(q)
        if (kbps != null) {
            return (kbps.groupValues[1].toIntOrNull() ?: 0) / 1000
        }
        if ("hd" in q) return 720
        if ("sd" in q) return 480
        if ("cam" in q) return 100
        if ("ts" in q) return 200
        if ("dvdscr" in q) return 350
        if ("scr" in q) return 300
        if ("r5" in q || "r6" in q) return 400
        if ("org" in q) return 4320
        return 0
    }

    fun parseMinQualities(raw: String?): Map<String, String>? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) {
            return runCatching {
                val obj = json.parseToJsonElement(trimmed).jsonObject
                obj.mapValues { it.value.jsonPrimitive.content }
            }.getOrNull()
        }
        return mapOf("default" to trimmed)
    }

    fun parseExcludeCodecs(raw: String?): ExcludeCodecsConfig? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        return runCatching { json.decodeFromString<ExcludeCodecsConfig>(trimmed) }.getOrNull()
    }

    fun filterByMinQuality(streams: List<StreamItem>, minQualitySetting: String?): List<StreamItem> {
        if (minQualitySetting.isNullOrBlank() || minQualitySetting.equals("all", ignoreCase = true)) {
            return streams
        }
        val minNumeric = parseQuality(minQualitySetting)
        if (minNumeric == 0) return streams
        return streams.filter { parseQuality(it.quality) >= minNumeric }
    }

    fun filterByCodecs(streams: List<StreamItem>, exclude: ExcludeCodecsConfig?): List<StreamItem> {
        if (exclude == null || (!exclude.excludeDV && !exclude.excludeHDR)) return streams
        return streams.filter { stream ->
            if (stream.codecs.isEmpty()) return@filter true
            if (exclude.excludeDV && "DV" in stream.codecs) return@filter false
            if (exclude.excludeHDR && stream.codecs.any { it in setOf("HDR", "HDR10", "HDR10+") }) {
                return@filter false
            }
            true
        }
    }

    fun applyFilters(
        streams: List<StreamItem>,
        filterLabel: String,
        minQualitiesRaw: String?,
        excludeCodecsRaw: String?
    ): List<StreamItem> {
        val minMap = parseMinQualities(minQualitiesRaw)
        val minQuality = minMap?.get(filterLabel.lowercase()) ?: minMap?.get("default")
        val exclude = parseExcludeCodecs(excludeCodecsRaw)
        return filterByCodecs(filterByMinQuality(streams, minQuality), exclude)
    }
}
