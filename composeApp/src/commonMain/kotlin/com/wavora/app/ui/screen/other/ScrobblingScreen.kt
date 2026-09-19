package com.wavora.app.ui.screen.other

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wavora.app.ui.component.RippleIconButton
import com.wavora.app.ui.theme.LocalAppTypography
import com.wavora.app.viewModel.ScrobblingViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import wavora.composeapp.generated.resources.scrobbling
import wavora.composeapp.generated.resources.scrobbling_connect
import wavora.composeapp.generated.resources.scrobbling_connected_as
import wavora.composeapp.generated.resources.scrobbling_create_account
import wavora.composeapp.generated.resources.scrobbling_disconnect
import wavora.composeapp.generated.resources.scrobbling_lastfm
import wavora.composeapp.generated.resources.scrobbling_lastfm_not_configured
import wavora.composeapp.generated.resources.scrobbling_listenbrainz
import wavora.composeapp.generated.resources.scrobbling_listenbrainz_token
import wavora.composeapp.generated.resources.scrobbling_listenbrainz_token_hint
import wavora.composeapp.generated.resources.scrobbling_not_available
import wavora.composeapp.generated.resources.scrobbling_password
import wavora.composeapp.generated.resources.scrobbling_username

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrobblingScreen(
    navController: NavController,
    viewModel: ScrobblingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(Res.string.scrobbling), style = LocalAppTypography.current.titleMedium) },
            navigationIcon = {
                RippleIconButton(resId = Res.drawable.baseline_arrow_back_ios_new_24) {
                    navController.navigateUp()
                }
            },
        )

        if (!uiState.supported) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.scrobbling_not_available),
                    style = LocalAppTypography.current.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Last.fm ----
            Text(text = stringResource(Res.string.scrobbling_lastfm), style = LocalAppTypography.current.titleSmall)

            if (!uiState.lastFmConfigured) {
                Text(
                    text = stringResource(Res.string.scrobbling_lastfm_not_configured),
                    style = LocalAppTypography.current.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uiState.lastFmLoggedInAs != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(Res.string.scrobbling_connected_as, uiState.lastFmLoggedInAs.orEmpty()),
                            style = LocalAppTypography.current.bodyMedium,
                        )
                    }
                    Switch(checked = uiState.lastFmEnabled, onCheckedChange = viewModel::setLastFmEnabled)
                }
                OutlinedButton(onClick = viewModel::logoutFromLastFm) {
                    Text(stringResource(Res.string.scrobbling_disconnect))
                }
            } else {
                var username by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.scrobbling_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.scrobbling_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (uiState.lastFmError != null) {
                    Text(
                        text = uiState.lastFmError.orEmpty(),
                        style = LocalAppTypography.current.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { viewModel.loginToLastFm(username, password) },
                    enabled = !uiState.lastFmLoggingIn && username.isNotBlank() && password.isNotBlank(),
                ) {
                    if (uiState.lastFmLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.scrobbling_connect))
                    }
                }
                // Last.fm es una cuenta personal, no algo que Wavora te dé -
                // cada usuario necesita la suya propia para poder loguearse
                // acá arriba. Este link solo abre el navegador, no crea nada
                // desde la app (Last.fm no tiene una API de alta de cuentas).
                val uriHandler = LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri("https://www.last.fm/join") }) {
                    Text(stringResource(Res.string.scrobbling_create_account))
                }
            }

            HorizontalDivider()

            // ---- ListenBrainz ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(Res.string.scrobbling_listenbrainz), style = LocalAppTypography.current.titleSmall)
                Switch(checked = uiState.listenBrainzEnabled, onCheckedChange = viewModel::setListenBrainzEnabled)
            }
            var tokenField by remember(uiState.listenBrainzToken) { mutableStateOf(uiState.listenBrainzToken) }
            OutlinedTextField(
                value = tokenField,
                onValueChange = { tokenField = it },
                label = { Text(stringResource(Res.string.scrobbling_listenbrainz_token)) },
                supportingText = { Text(stringResource(Res.string.scrobbling_listenbrainz_token_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.setListenBrainzToken(tokenField) },
                enabled = tokenField != uiState.listenBrainzToken,
            ) {
                Text(stringResource(Res.string.scrobbling_connect))
            }
        }
    }
}
