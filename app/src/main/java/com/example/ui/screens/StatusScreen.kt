package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.FunctionalCheckState
import com.example.model.ProviderStatusItem
import com.example.model.RuntimeMetrics
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedBg
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenBg
import com.example.ui.theme.WarnAmber
import com.example.ui.theme.WarnAmberBg

@Composable
fun StatusScreen(
    metrics: RuntimeMetrics,
    providers: List<ProviderStatusItem>,
    checkSummary: String,
    isRunningChecks: Boolean,
    onRefreshStatus: () -> Unit,
    onRestartProxy: () -> Unit,
    onRunFunctionalChecks: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Server Status", style = MaterialTheme.typography.headlineMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRestartProxy,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restart Proxy")
                        }
                        Button(
                            onClick = onRefreshStatus,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("refresh_status_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Metrics", style = MaterialTheme.typography.titleLarge)
                        val metricsJson = buildString {
                            appendLine("{")
                            appendLine("  \"uptimeSeconds\": ${metrics.uptimeSeconds},")
                            appendLine("  \"requestsTotal\": ${metrics.requestsTotal},")
                            appendLine("  \"streamRequests\": ${metrics.streamRequests},")
                            appendLine("  \"streamsReturned\": ${metrics.streamsReturned},")
                            appendLine("  \"tmdbToImdbLookups\": ${metrics.tmdbToImdbLookups},")
                            appendLine("  \"localProxyRunning\": ${metrics.localProxyRunning},")
                            appendLine("  \"localProxyPort\": ${metrics.localProxyPort},")
                            appendLine("  \"mode\": \"${metrics.activeBackendMode}\"")
                            append("}")
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = metricsJson,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Providers", style = MaterialTheme.typography.titleLarge)
                        Button(
                            onClick = onRunFunctionalChecks,
                            enabled = !isRunningChecks,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_provider_checks_button")
                        ) {
                            if (isRunningChecks) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.PlayCircleOutline, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRunningChecks) "Running Provider Checks..." else "Run Provider Functional Checks")
                        }
                        if (checkSummary.isNotBlank()) {
                            Text(
                                text = checkSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(providers, key = { it.name }) { item ->
                ProviderStatusRowCard(item)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Local Proxy & API Surface", style = MaterialTheme.typography.titleLarge)
                        val endpoints = listOf(
                            "GET http://127.0.0.1:${metrics.localProxyPort}/api/health",
                            "GET http://127.0.0.1:${metrics.localProxyPort}/m3u8-proxy?url=<encoded>&headers=<json>",
                            "GET http://127.0.0.1:${metrics.localProxyPort}/ts-proxy?url=<encoded>&headers=<json>",
                            "GET http://127.0.0.1:${metrics.localProxyPort}/sub-proxy?url=<encoded>&headers=<json>",
                            "GET /api/streams/:type/:tmdbId",
                            "GET /api/streams/:provider/:type/:tmdbId",
                            "GET /movie/:tmdbId  |  GET /tv/:tmdbId/:season/:episode"
                        ).joinToString("\n")
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = endpoints,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderStatusRowCard(item: ProviderStatusItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium
                )
                val (funcLabel, funcBg, funcFg) = when (item.functionalState) {
                    FunctionalCheckState.IDLE -> Triple("?", SlateSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    FunctionalCheckState.RUNNING -> Triple("...", WarnAmberBg, WarnAmber)
                    FunctionalCheckState.OK -> Triple("OK", SuccessGreenBg, SuccessGreen)
                    FunctionalCheckState.NONE -> Triple("NONE", DangerRedBg, DangerRed)
                    FunctionalCheckState.SKIPPED -> Triple("SKIP", SlateSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    FunctionalCheckState.FAILED -> Triple("FAIL", DangerRedBg, DangerRed)
                }
                StatusPill(text = "FUNC: $funcLabel", bg = funcBg, fg = funcFg)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = if (item.enabled) "ON" else "OFF",
                    bg = if (item.enabled) SuccessGreenBg else SlateSurfaceVariant,
                    fg = if (item.enabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    text = if (item.cookieRequired) "COOKIE REQ" else "NO COOKIE",
                    bg = if (item.cookieRequired) WarnAmberBg else SlateSurfaceVariant,
                    fg = if (item.cookieRequired) WarnAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    text = if (item.cookieOk) "COOKIE OK" else "COOKIE MISSING",
                    bg = if (item.cookieOk) SuccessGreenBg else DangerRedBg,
                    fg = if (item.cookieOk) SuccessGreen else DangerRed
                )
            }

            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
