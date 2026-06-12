package com.wavora.app.expect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavora.app.expect.openUrl
import com.wavora.app.ui.theme.typo
import com.wavora.app.ui.theme.wavoraBorder
import com.wavora.app.ui.theme.wavoraPrimary
import com.wavora.app.ui.theme.wavoraSurface
import com.wavora.app.viewModel.LogInViewModel
import multiplatform.network.cmptoast.showToast
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.discord_setup_command
import wavora.composeapp.generated.resources.discord_setup_connect
import wavora.composeapp.generated.resources.discord_setup_connecting
import wavora.composeapp.generated.resources.discord_setup_error
import wavora.composeapp.generated.resources.discord_setup_step1_button
import wavora.composeapp.generated.resources.discord_setup_step1_desc
import wavora.composeapp.generated.resources.discord_setup_step1_title
import wavora.composeapp.generated.resources.discord_setup_step2_desc
import wavora.composeapp.generated.resources.discord_setup_step2_title
import wavora.composeapp.generated.resources.discord_setup_step3_copy_command
import wavora.composeapp.generated.resources.discord_setup_step3_desc
import wavora.composeapp.generated.resources.discord_setup_step3_title
import wavora.composeapp.generated.resources.discord_setup_step4_placeholder
import wavora.composeapp.generated.resources.discord_setup_step4_title
import wavora.composeapp.generated.resources.discord_setup_subtitle
import wavora.composeapp.generated.resources.discord_setup_title
import wavora.composeapp.generated.resources.desktop_webview_description
import wavora.composeapp.generated.resources.open_blog_post
import wavora.composeapp.generated.resources.spotify_setup_connect
import wavora.composeapp.generated.resources.spotify_setup_error
import wavora.composeapp.generated.resources.spotify_setup_step1_button
import wavora.composeapp.generated.resources.spotify_setup_step1_desc
import wavora.composeapp.generated.resources.spotify_setup_step1_title
import wavora.composeapp.generated.resources.spotify_setup_step2_desc
import wavora.composeapp.generated.resources.spotify_setup_step2_title
import wavora.composeapp.generated.resources.spotify_setup_step3_desc
import wavora.composeapp.generated.resources.spotify_setup_step3_title
import wavora.composeapp.generated.resources.spotify_setup_step4_placeholder
import wavora.composeapp.generated.resources.spotify_setup_step4_title
import wavora.composeapp.generated.resources.spotify_setup_subtitle
import wavora.composeapp.generated.resources.spotify_setup_title
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URI

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String =
            CookieHandler
                .getDefault()
                .get(URI(url), emptyMap())["Cookie"]
                ?.joinToString("; ") ?: ""

        override fun removeAllCookies() {
            CookieHandler.setDefault(CookieManager())
        }
    }

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    // On desktop there is no WebView. We show a step-by-step guide for
    // whichever service is being logged in (detected from initUrl).
    val isSpotify = initUrl.contains("spotify", ignoreCase = true)

    if (isSpotify) {
        SpotifyDesktopSetup(aboveContent = aboveContent, onLoginDone = { onPageFinished(it) })
    } else {
        // YouTube or other — keep existing blog-post fallback
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(Res.string.desktop_webview_description),
                    style = typo().labelMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { openUrl("https://www.wavora.org/blogs/en/how-to-log-in-on-desktop-app") },
                ) {
                    Text(
                        stringResource(Res.string.open_blog_post),
                        style = typo().labelMedium,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            aboveContent()
        }
    }
}

@Composable
private fun SpotifyDesktopSetup(
    aboveContent: @Composable BoxScope.() -> Unit,
    onLoginDone: (String) -> Unit,
) {
    val viewModel: LogInViewModel = koinInject()
    val validationState by viewModel.spdcValidation.collectAsState()
    var spdc by remember { mutableStateOf("") }

    LaunchedEffect(validationState) {
        if (validationState is LogInViewModel.SpdcValidationState.Success) {
            onLoginDone("https://accounts.spotify.com/status")
            showToast("Connected to Spotify!")
            viewModel.resetSpdcValidation()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.spotify_setup_title),
                    style = typo().titleMedium,
                    color = Color.White,
                )
                Text(
                    text = stringResource(Res.string.spotify_setup_subtitle),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            StepCard(number = 1) {
                Text(stringResource(Res.string.spotify_setup_step1_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.spotify_setup_step1_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { openUrl("https://open.spotify.com") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Rounded.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.spotify_setup_step1_button), style = typo().labelMedium)
                }
            }

            StepCard(number = 2) {
                Text(stringResource(Res.string.spotify_setup_step2_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.spotify_setup_step2_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
            }

            StepCard(number = 3) {
                Text(stringResource(Res.string.spotify_setup_step3_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.spotify_setup_step3_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
            }

            StepCard(number = 4) {
                Text(stringResource(Res.string.spotify_setup_step4_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = spdc,
                    onValueChange = {
                        spdc = it
                        if (validationState is LogInViewModel.SpdcValidationState.Error) {
                            viewModel.resetSpdcValidation()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(Res.string.spotify_setup_step4_placeholder),
                            style = typo().bodySmall,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = validationState is LogInViewModel.SpdcValidationState.Error,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = wavoraBorder,
                        errorBorderColor = Color(0xFFCF6679),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
                AnimatedVisibility(
                    visible = validationState is LogInViewModel.SpdcValidationState.Error,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Text(
                        stringResource(Res.string.spotify_setup_error),
                        style = typo().bodySmall,
                        color = Color(0xFFCF6679),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                val isLoading = validationState is LogInViewModel.SpdcValidationState.Loading
                Button(
                    onClick = { if (spdc.isNotBlank()) viewModel.validateAndSaveSpotifySpdc(spdc) },
                    enabled = spdc.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.discord_setup_connecting), style = typo().labelMedium)
                    } else {
                        Text(stringResource(Res.string.spotify_setup_connect), style = typo().labelMedium)
                    }
                }
            }
        }
        aboveContent()
    }
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (String) -> Unit,
) {
    val viewModel: LogInViewModel = koinInject()
    val validationState by viewModel.tokenValidation.collectAsState()
    var token by remember { mutableStateOf("") }
    val jsCommand = stringResource(Res.string.discord_setup_command)

    // When validation succeeds, forward the token upward and reset state
    LaunchedEffect(validationState) {
        if (validationState is LogInViewModel.TokenValidationState.Success) {
            val info = (validationState as LogInViewModel.TokenValidationState.Success).userInfo
            onLoginDone(token.trim())
            showToast("Connected as ${info.name}")
            viewModel.resetTokenValidation()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.discord_setup_title),
                    style = typo().titleMedium,
                    color = Color.White,
                )
                Text(
                    text = stringResource(Res.string.discord_setup_subtitle),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            // Step 1
            StepCard(number = 1) {
                Text(
                    stringResource(Res.string.discord_setup_step1_title),
                    style = typo().labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.discord_setup_step1_desc),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { openUrl("https://discord.com/login") },
                    colors = ButtonDefaults.buttonColors(containerColor = wavoraPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(Res.string.discord_setup_step1_button),
                        style = typo().labelMedium,
                    )
                }
            }

            // Step 2
            StepCard(number = 2) {
                Text(
                    stringResource(Res.string.discord_setup_step2_title),
                    style = typo().labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.discord_setup_step2_desc),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            // Step 3
            StepCard(number = 3) {
                Text(
                    stringResource(Res.string.discord_setup_step3_title),
                    style = typo().labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.discord_setup_step3_desc),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                // Code block
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, wavoraBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = jsCommand,
                        style = typo().bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        color = Color(0xFF58A6FF),
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                    )
                    IconButton(
                        onClick = {
                            val clip = StringSelection(jsCommand)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(clip, null)
                            showToast("Command copied!")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(Res.string.discord_setup_step3_copy_command),
                            tint = wavoraPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Step 4
            StepCard(number = 4) {
                Text(
                    stringResource(Res.string.discord_setup_step4_title),
                    style = typo().labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        if (validationState is LogInViewModel.TokenValidationState.Error) {
                            viewModel.resetTokenValidation()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(Res.string.discord_setup_step4_placeholder),
                            style = typo().bodySmall,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = validationState is LogInViewModel.TokenValidationState.Error,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = wavoraPrimary,
                        unfocusedBorderColor = wavoraBorder,
                        errorBorderColor = Color(0xFFCF6679),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )

                // Error message
                AnimatedVisibility(
                    visible = validationState is LogInViewModel.TokenValidationState.Error,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Text(
                        stringResource(Res.string.discord_setup_error),
                        style = typo().bodySmall,
                        color = Color(0xFFCF6679),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))
                val isLoading = validationState is LogInViewModel.TokenValidationState.Loading
                Button(
                    onClick = { if (token.isNotBlank()) viewModel.validateAndSaveDiscordToken(token) },
                    enabled = token.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = wavoraPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.discord_setup_connecting), style = typo().labelMedium)
                    } else {
                        Text(stringResource(Res.string.discord_setup_connect), style = typo().labelMedium)
                    }
                }
            }
        }
        aboveContent()
    }
}

@Composable
private fun StepCard(
    number: Int,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(wavoraSurface)
                .border(1.dp, wavoraBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Step number badge
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(wavoraPrimary.copy(alpha = 0.15f))
                    .border(1.dp, wavoraPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = typo().labelMedium,
                color = wavoraPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
