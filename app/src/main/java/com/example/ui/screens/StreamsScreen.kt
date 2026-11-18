package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ProviderCatalog
import com.example.model.AppConfig
import com.example.model.MediaType
import com.example.model.ProbeStatus
import com.example.model.RuntimeMetrics
import com.example.model.StreamItem
import com.example.ui.StreamSearchUiState
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarnAmber

@Composable
fun StreamsScreen(
    tmdbId: String,
    mediaType: MediaType,
    season: String,
    episode: String,
    selectedProvider: String,
    probePlayability: Boolean,
    config: AppConfig,
    metrics: RuntimeMetrics,
    searchState: StreamSearchUiState,
    onTmdbIdChange: (String) -> Unit,
    onMediaTypeChange: (MediaType) -> Unit,
    onSeasonChange: (String) -> Unit,
    onEpisodeChange: (String) -> Unit,
    onProviderChange: (String) -> Unit,
    onProbeToggle: (Boolean) -> Unit,
    onProxyToggle: (Boolean) -> Unit,
    onQuickPreset: (String, MediaType, String, String) -> Unit,
    onResolve: () -> Unit,
    onPlayStream: (StreamItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                HeroBannerCard(metrics = metrics, proxyEnabled = config.enableProxy)
            }

            item {
                QueryControlsCard(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    season = season,
                    episode = episode,
                    selectedProvider = selectedProvider,
                    probePlayability = probePlayability,
                    proxyEnabled = config.enableProxy,
                    isLoading = searchState is StreamSearchUiState.Loading,
                    onTmdbIdChange = onTmdbIdChange,
                    onMediaTypeChange = onMediaTypeChange,
                    onSeasonChange = onSeasonChange,
                    onEpisodeChange = onEpisodeChange,
                    onProviderChange = onProviderChange,
                    onProbeToggle = onProbeToggle,
                    onProxyToggle = onProxyToggle,
                    onQuickPreset = onQuickPreset,
                    onResolve = onResolve
                )
            }

            when (searchState) {
                is StreamSearchUiState.Idle -> {
                    item {
                        EmptyPromptCard()
                    }
                }

                is StreamSearchUiState.Loading -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Resolving streams & probing segments...",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Querying active providers (dcloud, tik, ipcloud, v6:Hindi) and checking segment headers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is StreamSearchUiState.Error -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = searchState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                is StreamSearchUiState.Success -> {
                    val result = searchState.result
                    if (result.titlePreview != null || result.imdbId != null || result.providerTimingsMs.isNotEmpty()) {
                        item {
                            ResolutionSummaryCard(
                                title = result.titlePreview?.title ?: "TMDB #${result.tmdbId}",
                                year = result.titlePreview?.releaseYear.orEmpty(),
                                overview = result.titlePreview?.overview.orEmpty(),
                                imdbId = result.imdbId,
                                streamCount = result.streams.size,
                                timings = result.providerTimingsMs,
                                recommended = result.recommendedStream,
                                onPlayRecommended = { result.recommendedStream?.let(onPlayStream) }
                            )
                        }
                    } else if (result.recommendedStream != null) {
                        item {
                            ResolutionSummaryCard(
                                title = "TMDB #${result.tmdbId}",
                                year = "",
                                overview = "",
                                imdbId = null,
                                streamCount = result.streams.size,
                                timings = result.providerTimingsMs,
                                recommended = result.recommendedStream,
                                onPlayRecommended = { onPlayStream(result.recommendedStream) }
                            )
                        }
                    }

                    if (result.streams.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = "No streams",
                                        tint = WarnAmber,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "No playable streams returned (count: 0)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Upstream servers rotate per title. Try another TMDB ID or check Provider Status.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(result.streams, key = { it.id }) { stream ->
                            StreamResultCard(
                                stream = stream,
                                onPlay = { onPlayStream(stream) },
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Stream URL", stream.url))
                                    Toast.makeText(context, "Copied stream URL", Toast.LENGTH_SHORT).show()
                                },
                                onOpenExternal = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(stream.url), "application/x-mpegURL")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }.onFailure {
                                        Toast.makeText(context, "No external HLS player installed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBannerCard(
    metrics: RuntimeMetrics,
    proxyEnabled: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_stream_hero),
            contentDescription = "TMDB Embed stream engine banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x660F141A),
                            Color(0xE60F141A)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (metrics.localProxyRunning) SuccessGreen.copy(alpha = 0.2f) else WarnAmber.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (metrics.localProxyRunning) SuccessGreen else WarnAmber
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (metrics.localProxyRunning) SuccessGreen else WarnAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (proxyEnabled && metrics.localProxyRunning) {
                                "/m3u8-proxy :${metrics.localProxyPort}"
                            } else {
                                "Direct Headers Mode"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    text = metrics.activeBackendMode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column {
                Text(
                    text = "TMDB Embed Stream Engine",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = "Resolve non-IP-locked HLS streams with automatic segment probing & /m3u8-proxy header rewriting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

@Composable
private fun QueryControlsCard(
    tmdbId: String,
    mediaType: MediaType,
    season: String,
    episode: String,
    selectedProvider: String,
    probePlayability: Boolean,
    proxyEnabled: Boolean,
    isLoading: Boolean,
    onTmdbIdChange: (String) -> Unit,
    onMediaTypeChange: (MediaType) -> Unit,
    onSeasonChange: (String) -> Unit,
    onEpisodeChange: (String) -> Unit,
    onProviderChange: (String) -> Unit,
    onProbeToggle: (Boolean) -> Unit,
    onProxyToggle: (Boolean) -> Unit,
    onQuickPreset: (String, MediaType, String, String) -> Unit,
    onResolve: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MediaType.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = mediaType == item,
                        onClick = { onMediaTypeChange(item) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = MediaType.entries.size),
                        icon = {
                            Icon(
                                imageVector = if (item == MediaType.MOVIE) Icons.Default.Movie else Icons.Default.Tv,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("media_type_${item.apiValue}")
                    ) {
                        Text(item.label)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = tmdbId,
                    onValueChange = onTmdbIdChange,
                    label = { Text("TMDB ID") },
                    placeholder = { Text("550") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onResolve() }),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("tmdb_id_input")
                )
                if (mediaType == MediaType.TV) {
                    OutlinedTextField(
                        value = season,
                        onValueChange = onSeasonChange,
                        label = { Text("Season") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("season_input")
                    )
                    OutlinedTextField(
                        value = episode,
                        onValueChange = onEpisodeChange,
                        label = { Text("Episode") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("episode_input")
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = tmdbId == "550" && mediaType == MediaType.MOVIE,
                    onClick = { onQuickPreset("550", MediaType.MOVIE, "1", "1") },
                    label = { Text("Fight Club (550)") }
                )
                FilterChip(
                    selected = tmdbId == "293660" && mediaType == MediaType.MOVIE,
                    onClick = { onQuickPreset("293660", MediaType.MOVIE, "1", "1") },
                    label = { Text("Deadpool (293660)") }
                )
                FilterChip(
                    selected = tmdbId == "1396" && mediaType == MediaType.TV,
                    onClick = { onQuickPreset("1396", MediaType.TV, "1", "1") },
                    label = { Text("Breaking Bad S01E01") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedProvider == "all",
                    onClick = { onProviderChange("all") },
                    label = { Text("Aggregate All") }
                )
                ProviderCatalog.PROVIDER_NAMES.forEach { name ->
                    FilterChip(
                        selected = selectedProvider.equals(name, ignoreCase = true),
                        onClick = { onProviderChange(name) },
                        label = { Text(name) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rewrite via /m3u8-proxy", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Routes playlists & segments through local proxy with upstream Referer headers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = proxyEnabled,
                    onCheckedChange = onProxyToggle,
                    modifier = Modifier.testTag("toggle_m3u8_proxy")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Probe Segment Magic Bytes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sniffs first 16 bytes to skip PNG-wrapped sources (e.g. ipcloud)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = probePlayability,
                    onCheckedChange = onProbeToggle,
                    modifier = Modifier.testTag("toggle_probe_playability")
                )
            }

            Button(
                onClick = onResolve,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("resolve_streams_button")
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isLoading) "Resolving..." else "Resolve Streams")
            }
        }
    }
}

@Composable
private fun EmptyPromptCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text("Ready to Resolve Streams", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Enter a TMDB ID or tap a quick preset above to fetch live VidZee HLS streams, probe segment playability, and stream in ExoPlayer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResolutionSummaryCard(
    title: String,
    year: String,
    overview: String,
    imdbId: String?,
    streamCount: Int,
    timings: Map<String, Long?>,
    recommended: StreamItem?,
    onPlayRecommended: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (year.isNotBlank()) "$title ($year)" else title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "$streamCount streams",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            if (overview.isNotBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!imdbId.isNullOrBlank() || timings.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!imdbId.isNullOrBlank()) {
                        MetaBadge("IMDb: $imdbId")
                    }
                    timings.forEach { (prov, ms) ->
                        MetaBadge("$prov: ${ms ?: "err"}ms")
                    }
                }
            }
            if (recommended != null) {
                Button(
                    onClick = onPlayRecommended,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("play_recommended_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play Best Source (${recommended.server.ifBlank { recommended.provider }})")
                }
            }
        }
    }
}

@Composable
private fun StreamResultCard(
    stream: StreamItem,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val probeColor = when (stream.probeStatus) {
        ProbeStatus.OPENABLE -> SuccessGreen
        ProbeStatus.IMAGE_WRAPPED -> WarnAmber
        ProbeStatus.UNREACHABLE -> DangerRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stream_card_${stream.server.ifBlank { stream.provider }}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stream.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (stream.probeStatus == ProbeStatus.OPENABLE) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.WarningAmber
                        },
                        contentDescription = stream.probeStatus.label,
                        tint = probeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stream.probeStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = probeColor
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetaBadge(stream.provider)
                MetaBadge(stream.quality)
                MetaBadge(stream.language)
                if (stream.isProxied) MetaBadge("/m3u8-proxy")
                stream.headers["Referer"]?.let { MetaBadge("Referer: $it") }
            }

            Text(
                text = stream.url,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("play_stream_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy stream URL")
                }
                OutlinedButton(
                    onClick = onOpenExternal,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open external player")
                }
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
