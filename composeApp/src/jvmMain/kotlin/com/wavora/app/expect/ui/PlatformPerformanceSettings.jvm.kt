package com.wavora.app.expect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wavora.app.diagnostics.DiagnosticsConfig
import com.wavora.app.input.GlobalMediaKeys
import com.wavora.app.input.KeyboardShortcuts
import com.wavora.app.render.AppRestart
import com.wavora.app.render.RenderConfig
import com.wavora.app.ui.component.SettingItem
import com.wavora.app.ui.theme.LocalAppTypography
import com.wavora.app.ui.theme.wavoraPrimary
import com.wavora.app.ui.theme.white
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.cancel
import wavora.composeapp.generated.resources.diagnostics_mode_desc
import wavora.composeapp.generated.resources.diagnostics_mode_title
import wavora.composeapp.generated.resources.hw_accel_api_direct3d
import wavora.composeapp.generated.resources.hw_accel_api_software
import wavora.composeapp.generated.resources.hw_accel_auto
import wavora.composeapp.generated.resources.hw_accel_auto_desc
import wavora.composeapp.generated.resources.hw_accel_fallback
import wavora.composeapp.generated.resources.hw_accel_off
import wavora.composeapp.generated.resources.hw_accel_off_desc
import wavora.composeapp.generated.resources.hw_accel_on
import wavora.composeapp.generated.resources.hw_accel_on_desc
import wavora.composeapp.generated.resources.hw_accel_pending_restart
import wavora.composeapp.generated.resources.hw_accel_restart_now
import wavora.composeapp.generated.resources.hw_accel_subtitle
import wavora.composeapp.generated.resources.hw_accel_title
import wavora.composeapp.generated.resources.keyboard_shortcuts_desc
import wavora.composeapp.generated.resources.keyboard_shortcuts_title
import wavora.composeapp.generated.resources.media_keys_desc
import wavora.composeapp.generated.resources.media_keys_title
import wavora.composeapp.generated.resources.performance_settings_title
import wavora.composeapp.generated.resources.shortcut_action_like
import wavora.composeapp.generated.resources.shortcut_action_mini_player
import wavora.composeapp.generated.resources.shortcut_action_next
import wavora.composeapp.generated.resources.shortcut_action_play_pause
import wavora.composeapp.generated.resources.shortcut_action_previous
import wavora.composeapp.generated.resources.shortcut_action_volume_down
import wavora.composeapp.generated.resources.shortcut_action_volume_up
import wavora.composeapp.generated.resources.shortcut_change
import wavora.composeapp.generated.resources.shortcut_done
import wavora.composeapp.generated.resources.shortcut_needs_modifier
import wavora.composeapp.generated.resources.shortcut_none
import wavora.composeapp.generated.resources.shortcut_press_keys
import wavora.composeapp.generated.resources.shortcut_remove
import wavora.composeapp.generated.resources.shortcut_restore_defaults
import wavora.composeapp.generated.resources.shortcuts_disabled_summary
import wavora.composeapp.generated.resources.shortcuts_enabled
import wavora.composeapp.generated.resources.shortcuts_hint

@Composable
actual fun PlatformPerformanceSettings() {
    val isWindows = RenderConfig.isWindows

    var mode by remember { mutableStateOf(RenderConfig.mode) }
    // Modo con el que arrancó ESTA sesión, para saber si hace falta reiniciar.
    val startupMode = remember { RenderConfig.mode }
    var showModeDialog by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(DiagnosticsConfig.enabledSetting) }
    val diagnosticsAtStartup = remember { DiagnosticsConfig.isActive }
    var mediaKeys by remember { mutableStateOf(GlobalMediaKeys.enabledSetting) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    val shortcutsEnabled by KeyboardShortcuts.enabled.collectAsState()

    val needsRestart = (isWindows && mode != startupMode) || diagnostics != diagnosticsAtStartup

    Column {
        Text(
            text = stringResource(Res.string.performance_settings_title),
            style = LocalAppTypography.current.labelMedium,
            color = white,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (isWindows) {
            val modeLabel =
                when (mode) {
                    RenderConfig.Mode.AUTO -> stringResource(Res.string.hw_accel_auto)
                    RenderConfig.Mode.HARDWARE -> stringResource(Res.string.hw_accel_on)
                    RenderConfig.Mode.SOFTWARE -> stringResource(Res.string.hw_accel_off)
                }
            val apiLabel =
                if (RenderConfig.current?.api == RenderConfig.Api.DIRECT3D) {
                    stringResource(Res.string.hw_accel_api_direct3d)
                } else {
                    stringResource(Res.string.hw_accel_api_software)
                }
            val blockReason = RenderConfig.blockReason
            val baseSubtitle = stringResource(Res.string.hw_accel_subtitle, modeLabel, apiLabel)
            val fallbackLine =
                if (mode == RenderConfig.Mode.AUTO && RenderConfig.hardwareBlocked && blockReason != null) {
                    stringResource(Res.string.hw_accel_fallback, blockReason)
                } else {
                    null
                }
            SettingItem(
                title = stringResource(Res.string.hw_accel_title),
                subtitle = if (fallbackLine != null) "$baseSubtitle\n$fallbackLine" else baseSubtitle,
                onClick = { showModeDialog = true },
            )
        }

        if (isWindows) {
            SettingItem(
                title = stringResource(Res.string.media_keys_title),
                subtitle = stringResource(Res.string.media_keys_desc),
                switch = (
                    mediaKeys to { enabled: Boolean ->
                        mediaKeys = enabled
                        GlobalMediaKeys.setEnabled(enabled)
                    }
                ),
            )
        }

        SettingItem(
            title = stringResource(Res.string.keyboard_shortcuts_title),
            subtitle =
                if (shortcutsEnabled) {
                    stringResource(Res.string.keyboard_shortcuts_desc)
                } else {
                    stringResource(Res.string.shortcuts_disabled_summary)
                },
            onClick = { showShortcutsDialog = true },
        )

        SettingItem(
            title = stringResource(Res.string.diagnostics_mode_title),
            subtitle = stringResource(Res.string.diagnostics_mode_desc),
            switch = (
                diagnostics to { enabled: Boolean ->
                    diagnostics = enabled
                    DiagnosticsConfig.enabledSetting = enabled
                }
            ),
        )

        if (needsRestart) {
            SettingItem(
                title = stringResource(Res.string.hw_accel_restart_now),
                subtitle = stringResource(Res.string.hw_accel_pending_restart),
                onClick = { AppRestart.restartNow() },
            )
        }
    }

    if (showModeDialog) {
        val options =
            listOf(
                Triple(RenderConfig.Mode.AUTO, Res.string.hw_accel_auto, Res.string.hw_accel_auto_desc),
                Triple(RenderConfig.Mode.HARDWARE, Res.string.hw_accel_on, Res.string.hw_accel_on_desc),
                Triple(RenderConfig.Mode.SOFTWARE, Res.string.hw_accel_off, Res.string.hw_accel_off_desc),
            )
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = {
                Text(
                    text = stringResource(Res.string.hw_accel_title),
                    style = LocalAppTypography.current.titleSmall,
                )
            },
            text = {
                Column {
                    options.forEach { (option, title, desc) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mode = option
                                        RenderConfig.mode = option
                                        showModeDialog = false
                                    }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = mode == option,
                                onClick = {
                                    mode = option
                                    RenderConfig.mode = option
                                    showModeDialog = false
                                },
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = stringResource(title), style = LocalAppTypography.current.bodyMedium)
                                Text(text = stringResource(desc), style = LocalAppTypography.current.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showShortcutsDialog) {
        ShortcutsDialog(
            onDismiss = {
                KeyboardShortcuts.cancelCapture()
                showShortcutsDialog = false
            },
        )
    }
}

/**
 * Editor de atajos de teclado (v2.3.1): activar/desactivar, cambiar cada
 * combinación (se captura la próxima combinación que se presione), quitarla o
 * restaurar los valores por defecto. Ver input/KeyboardShortcuts.kt.
 */
@Composable
private fun ShortcutsDialog(onDismiss: () -> Unit) {
    val enabled by KeyboardShortcuts.enabled.collectAsState()
    val bindings by KeyboardShortcuts.bindings.collectAsState()
    val capturing by KeyboardShortcuts.capturing.collectAsState()
    val captureError by KeyboardShortcuts.captureError.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.keyboard_shortcuts_title),
                style = LocalAppTypography.current.titleSmall,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.shortcuts_enabled),
                        style = LocalAppTypography.current.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { KeyboardShortcuts.setEnabled(it) })
                }
                KeyboardShortcuts.Action.entries.forEach { action ->
                    val isCapturing = capturing == action
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(action.labelRes()),
                                style = LocalAppTypography.current.bodyMedium,
                            )
                            Text(
                                text =
                                    when {
                                        isCapturing -> stringResource(Res.string.shortcut_press_keys)
                                        else -> bindings[action]?.label() ?: stringResource(Res.string.shortcut_none)
                                    },
                                style = LocalAppTypography.current.bodySmall,
                                color = if (isCapturing) wavoraPrimary else white.copy(alpha = 0.7f),
                            )
                        }
                        TextButton(
                            enabled = enabled,
                            onClick = {
                                if (isCapturing) KeyboardShortcuts.cancelCapture() else KeyboardShortcuts.startCapture(action)
                            },
                        ) {
                            Text(
                                text =
                                    if (isCapturing) {
                                        stringResource(Res.string.cancel)
                                    } else {
                                        stringResource(Res.string.shortcut_change)
                                    },
                            )
                        }
                        TextButton(
                            enabled = enabled && bindings[action] != null && !isCapturing,
                            onClick = { KeyboardShortcuts.setBinding(action, null) },
                        ) {
                            Text(text = stringResource(Res.string.shortcut_remove))
                        }
                    }
                }
                if (captureError == KeyboardShortcuts.CaptureError.NEEDS_MODIFIER) {
                    Text(
                        text = stringResource(Res.string.shortcut_needs_modifier),
                        style = LocalAppTypography.current.bodySmall,
                        color = wavoraPrimary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.shortcuts_hint),
                    style = LocalAppTypography.current.bodySmall,
                    color = white.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.shortcut_done))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                KeyboardShortcuts.cancelCapture()
                KeyboardShortcuts.restoreDefaults()
            }) {
                Text(text = stringResource(Res.string.shortcut_restore_defaults))
            }
        },
    )
}

private fun KeyboardShortcuts.Action.labelRes(): StringResource =
    when (this) {
        KeyboardShortcuts.Action.PLAY_PAUSE -> Res.string.shortcut_action_play_pause
        KeyboardShortcuts.Action.NEXT -> Res.string.shortcut_action_next
        KeyboardShortcuts.Action.PREVIOUS -> Res.string.shortcut_action_previous
        KeyboardShortcuts.Action.VOLUME_UP -> Res.string.shortcut_action_volume_up
        KeyboardShortcuts.Action.VOLUME_DOWN -> Res.string.shortcut_action_volume_down
        KeyboardShortcuts.Action.LIKE -> Res.string.shortcut_action_like
        KeyboardShortcuts.Action.MINI_PLAYER -> Res.string.shortcut_action_mini_player
    }

