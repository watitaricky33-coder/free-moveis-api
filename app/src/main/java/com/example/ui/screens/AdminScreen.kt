package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.AppConfig
import com.example.model.AuthState
import com.example.ui.AdminFeedback
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedBg
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarnAmber
import com.example.ui.theme.WarnAmberBg

enum class AdminSection(val label: String) {
    CORE("Core"),
    QUALITY("Quality / Filters"),
    KEYS("Keys"),
    FEBBOX("FebBox / PStream"),
    ADVANCED("Advanced"),
    LIVE("Live Config")
}

@Composable
fun AdminScreen(
    authState: AuthState,
    lockoutSecondsRemaining: Long,
    config: AppConfig,
    feedback: AdminFeedback,
    configPrettyJson: String,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onSaveConfig: (AppConfig) -> Unit,
    onToggleProviderEnabled: (String) -> Unit,
    onToggleProviderDefault: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onRestartProxy: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!authState.isAuthenticated) {
        AdminLoginCard(
            lockoutSecondsRemaining = lockoutSecondsRemaining,
            feedback = feedback,
            onLogin = onLogin,
            onClearFeedback = onClearFeedback,
            modifier = modifier
        )
        return
    }

    var selectedSection by rememberSaveable { mutableStateOf(AdminSection.CORE) }
    var draftConfig by remember(config) { mutableStateOf(config) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(config) {
        draftConfig = config
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Settings?") },
            text = {
                Text("This removes all overrides, deletes TMDB API keys and FebBox cookies, and restores provider and feature flags to defaults.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetDefaults()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onSubmit = { oldPw, newPw ->
                showPasswordDialog = false
                onChangePassword(oldPw, newPw)
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Admin Console", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "Signed in as ${authState.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPasswordDialog = true },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Change password")
                        }
                        OutlinedButton(
                            onClick = onRestartProxy,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Restart proxy")
                        }
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        }
                    }
                }
            }

            if (feedback.message.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = if (feedback.isError) DangerRedBg else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (feedback.isError) DangerRed else SuccessGreen)
                    ) {
                        Text(
                            text = feedback.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (feedback.isError) DangerRed else SuccessGreen,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (config.febboxCookies.isEmpty()) {
                item {
                    WarningBanner("No FEBBOX_COOKIES configured. Showbox / PStream streams may be unavailable.")
                }
            }
            if (config.disableCache) {
                item {
                    WarningBanner("Segment cache is DISABLED; expect slower responses & higher upstream load.")
                }
            }
            if (config.tmdbApiKeys.isEmpty()) {
                item {
                    WarningBanner("No TMDB API Keys configured. Metadata previews & IMDb lookups are skipped.")
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminSection.entries.forEach { section ->
                        FilterChip(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            label = { Text(section.label) },
                            modifier = Modifier.testTag("admin_tab_${section.name.lowercase()}")
                        )
                    }
                }
            }

            item {
                when (selectedSection) {
                    AdminSection.CORE -> CoreConfigPanel(
                        config = draftConfig,
                        onUpdate = { draftConfig = it },
                        onToggleProviderEnabled = onToggleProviderEnabled,
                        onToggleProviderDefault = onToggleProviderDefault
                    )
                    AdminSection.QUALITY -> QualityFiltersPanel(
                        config = draftConfig,
                        onUpdate = { draftConfig = it }
                    )
                    AdminSection.KEYS -> TmdbKeysPanel(
                        config = draftConfig,
                        onUpdate = { draftConfig = it }
                    )
                    AdminSection.FEBBOX -> FebBoxCookiesPanel(
                        config = draftConfig,
                        onUpdate = { draftConfig = it }
                    )
                    AdminSection.ADVANCED -> AdvancedFlagsPanel(
                        config = draftConfig,
                        onUpdate = { draftConfig = it }
                    )
                    AdminSection.LIVE -> LiveConfigPanel(configPrettyJson = configPrettyJson)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onSaveConfig(draftConfig) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_config_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save Overrides")
                    }
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = DangerRed)
                        Spacer(Modifier.width(6.dp))
                        Text("Reset All", color = DangerRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminLoginCard(
    lockoutSecondsRemaining: Long,
    feedback: AdminFeedback,
    onLogin: (String, String) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var username by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("TMDB Embed Admin", style = MaterialTheme.typography.headlineSmall)

                if (lockoutSecondsRemaining > 0) {
                    val m = (lockoutSecondsRemaining / 60).toString().padStart(2, '0')
                    val s = (lockoutSecondsRemaining % 60).toString().padStart(2, '0')
                    Surface(
                        color = DangerRedBg,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Too Many Attempts. Try again in $m:$s.",
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onClearFeedback()
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_username_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearFeedback()
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_password_input")
                )

                if (feedback.message.isNotBlank()) {
                    Text(
                        text = feedback.message,
                        color = if (feedback.isError) DangerRed else SuccessGreen,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = { onLogin(username, password) },
                    enabled = lockoutSecondsRemaining == 0L && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_login_button")
                ) {
                    Text("Login")
                }

                Text(
                    text = "Sessions expire after 12h. Default credentials on first launch: admin / change-me.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnAmber
                )
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = oldPw,
                    onValueChange = { oldPw = it },
                    label = { Text("Old Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("New Password (min 8 chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(oldPw, newPw) },
                enabled = oldPw.isNotEmpty() && newPw.length >= 8
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WarningBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = WarnAmberBg,
        border = BorderStroke(1.dp, WarnAmber)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarnAmber)
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = WarnAmber)
        }
    }
}
