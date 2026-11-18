package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.ProviderCatalog
import com.example.model.AppConfig
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarnAmber

@Composable
fun CoreConfigPanel(
    config: AppConfig,
    onUpdate: (AppConfig) -> Unit,
    onToggleProviderEnabled: (String) -> Unit,
    onToggleProviderDefault: (String) -> Unit
) {
    val regions = listOf("", "USA7", "USA6", "USA5", "UK3", "CA1", "FR1", "DE2", "HK1", "IN1", "AU1", "SZ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Core Settings", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = config.apiBaseUrl,
                onValueChange = { onUpdate(config.copy(apiBaseUrl = it)) },
                label = { Text("Remote Backend URL (FREE_MOVIES_API_BASE_URL)") },
                placeholder = { Text("Leave blank for On-Device + Local /m3u8-proxy") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Leave blank to resolve VidZee directly from this device and proxy via 127.0.0.1/m3u8-proxy (non-IP-locked).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = config.port.toString(),
                onValueChange = { raw ->
                    val parsed = raw.filter { it.isDigit() }.toIntOrNull() ?: 8787
                    onUpdate(config.copy(port = parsed.coerceIn(1, 65535)))
                },
                label = { Text("API Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Default Region", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                regions.forEach { region ->
                    FilterChip(
                        selected = (config.defaultRegion ?: "") == region,
                        onClick = { onUpdate(config.copy(defaultRegion = region.ifBlank { null })) },
                        label = { Text(region.ifBlank { "Auto" }) }
                    )
                }
            }

            Text("Providers (Enable + Default)", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderCatalog.PROVIDERS.forEach { spec ->
                    val enabled = ProviderCatalog.isProviderEnabled(config, spec.name)
                    val isDefault = config.defaultProviders.any { it.equals(spec.name, ignoreCase = true) }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { onToggleProviderEnabled(spec.name) }) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Toggle ${spec.name} enabled",
                                    tint = if (enabled) SuccessGreen else DangerRed
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = spec.defaultStatusNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onToggleProviderDefault(spec.name) }) {
                                Icon(
                                    imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Toggle ${spec.name} default",
                                    tint = if (isDefault) WarnAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QualityFiltersPanel(
    config: AppConfig,
    onUpdate: (AppConfig) -> Unit
) {
    val qualityPresets = listOf("all", "480p", "720p", "1080p", "1440p", "2160p", "custom")
    val currentMq = config.minQualitiesRaw ?: "all"
    val selectedQualityPreset = if (currentMq.lowercase() in qualityPresets.dropLast(1)) {
        currentMq.lowercase()
    } else "custom"

    val codecPresets = listOf(
        "none" to "{\"excludeDV\":false,\"excludeHDR\":false}",
        "all" to "{\"excludeDV\":true,\"excludeHDR\":true}",
        "dv" to "{\"excludeDV\":true,\"excludeHDR\":false}",
        "hdr" to "{\"excludeDV\":false,\"excludeHDR\":true}"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Quality & Filtering", style = MaterialTheme.typography.titleLarge)

            Text("Minimum Quality", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qualityPresets.forEach { preset ->
                    FilterChip(
                        selected = selectedQualityPreset == preset,
                        onClick = {
                            if (preset == "custom") {
                                onUpdate(config.copy(minQualitiesRaw = "{\"default\":\"720p\"}"))
                            } else {
                                onUpdate(config.copy(minQualitiesRaw = preset))
                            }
                        },
                        label = { Text(if (preset == "2160p") "4K+" else preset.uppercase()) }
                    )
                }
            }

            if (selectedQualityPreset == "custom") {
                OutlinedTextField(
                    value = config.minQualitiesRaw.orEmpty(),
                    onValueChange = { onUpdate(config.copy(minQualitiesRaw = it)) },
                    label = { Text("Custom minQualities JSON") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Exclude Codecs", style = MaterialTheme.typography.titleMedium)
            codecPresets.forEach { (label, jsonPayload) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = config.excludeCodecsRaw == jsonPayload,
                        onClick = { onUpdate(config.copy(excludeCodecsRaw = jsonPayload)) }
                    )
                    Text(
                        text = when (label) {
                            "none" -> "None (allow DV & HDR)"
                            "all" -> "Exclude All (DV + HDR)"
                            "dv" -> "Exclude Dolby Vision (DV)"
                            else -> "Exclude HDR / HDR10(+)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            OutlinedTextField(
                value = config.excludeCodecsRaw.orEmpty(),
                onValueChange = { onUpdate(config.copy(excludeCodecsRaw = it)) },
                label = { Text("Exclude Codecs JSON") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TmdbKeysPanel(
    config: AppConfig,
    onUpdate: (AppConfig) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("TMDB API Keys", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Optional: VidZee works without a TMDB key, while keys enable title previews & IMDb external ID lookups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Enter TMDB API key") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val trimmed = keyInput.trim()
                        if (trimmed.isNotEmpty() && trimmed !in config.tmdbApiKeys) {
                            onUpdate(config.copy(tmdbApiKeys = config.tmdbApiKeys + trimmed))
                            keyInput = ""
                        }
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add TMDB key")
                }
            }

            if (config.tmdbApiKeys.isEmpty()) {
                Text(
                    text = "No TMDB keys added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                config.tmdbApiKeys.forEachIndexed { idx, key ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                onUpdate(config.copy(tmdbApiKeys = config.tmdbApiKeys.filterIndexed { i, _ -> i != idx }))
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove key", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FebBoxCookiesPanel(
    config: AppConfig,
    onUpdate: (AppConfig) -> Unit
) {
    var cookieInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("FebBox / PStream", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Add FebBox ui tokens (without the ui= prefix) for Showbox cookie rotation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    label = { Text("Enter ui token") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val cleaned = cookieInput.trim().removePrefix("ui=")
                        if (cleaned.isNotEmpty() && cleaned !in config.febboxCookies) {
                            onUpdate(config.copy(febboxCookies = config.febboxCookies + cleaned))
                            cookieInput = ""
                        }
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add cookie")
                }
            }

            if (config.febboxCookies.isEmpty()) {
                Text(
                    text = "No cookies added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                config.febboxCookies.forEachIndexed { idx, cookie ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cookie,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                onUpdate(config.copy(febboxCookies = config.febboxCookies.filterIndexed { i, _ -> i != idx }))
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove cookie", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedFlagsPanel(
    config: AppConfig,
    onUpdate: (AppConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Advanced Flags", style = MaterialTheme.typography.titleLarge)

            FlagToggleRow(
                title = "Enable Stream Proxy (/m3u8-proxy)",
                subtitle = "Rewrites .m3u8 playlists & segment URLs through local proxy with upstream Referer headers",
                checked = config.enableProxy,
                onCheckedChange = { onUpdate(config.copy(enableProxy = it)) }
            )
            FlagToggleRow(
                title = "Disable Cache",
                subtitle = "Bypasses in-memory segment caching inside /ts-proxy",
                checked = config.disableCache,
                onCheckedChange = { onUpdate(config.copy(disableCache = it)) }
            )
            FlagToggleRow(
                title = "Disable URL Validation",
                subtitle = "Skips segment magic-byte probing (may include PNG-wrapped sources)",
                checked = config.disableUrlValidation,
                onCheckedChange = { onUpdate(config.copy(disableUrlValidation = it)) }
            )
            FlagToggleRow(
                title = "Disable 4KHDHub Validation",
                subtitle = "Skip link validation on 4KHDHub candidates",
                checked = config.disable4khdhubUrlValidation,
                onCheckedChange = { onUpdate(config.copy(disable4khdhubUrlValidation = it)) }
            )
            OutlinedTextField(
                value = config.showboxCacheDir.orEmpty(),
                onValueChange = { onUpdate(config.copy(showboxCacheDir = it.ifBlank { null })) },
                label = { Text("Showbox Cache Dir") },
                placeholder = { Text("/data/showbox-cache") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun LiveConfigPanel(configPrettyJson: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Live Configuration (Merged)", style = MaterialTheme.typography.titleLarge)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = configPrettyJson,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun FlagToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
