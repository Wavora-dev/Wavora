package com.wavora.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.wavora.app.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.contributor_email
import wavora.composeapp.generated.resources.contributor_name
import wavora.composeapp.generated.resources.help_build_lyrics_database
import wavora.composeapp.generated.resources.help_build_lyrics_database_description
import wavora.composeapp.generated.resources.later
import wavora.composeapp.generated.resources.ok
import wavora.composeapp.generated.resources.use_anonymous
import com.wavora.app.ui.theme.LocalAppTypography

@Composable
@ExperimentalMaterial3Api
fun ShareSavedLyricsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (
        contributor: Pair<String, String>?,
    ) -> Unit, // contributor name and email, null if anonymous
) {
    var useAnonymous by remember {
        mutableStateOf(true)
    }
    var contributorName by remember {
        mutableStateOf("")
    }
    var contributorEmail by remember {
        mutableStateOf("")
    }

    AlertDialog(
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        onDismissRequest = {
            onDismissRequest.invoke()
        },
        confirmButton = {
            TextButton(onClick = {
                onDismissRequest.invoke()
                onConfirm(
                    if (useAnonymous) {
                        null
                    } else {
                        Pair(contributorName, contributorEmail)
                    },
                )
            }) {
                Text(
                    stringResource(Res.string.ok),
                    style = LocalAppTypography.current.bodySmall,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismissRequest.invoke()
            }) {
                Text(
                    stringResource(Res.string.later),
                    style = LocalAppTypography.current.bodySmall,
                )
            }
        },
        title = {
            Text(
                stringResource(Res.string.help_build_lyrics_database),
                style = LocalAppTypography.current.labelSmall,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(Res.string.help_build_lyrics_database_description),
                    style = LocalAppTypography.current.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                AnimatedVisibility(
                    modifier =
                        Modifier.padding(
                            vertical = 12.dp,
                        ),
                    visible = !useAnonymous,
                    enter = fadeIn() + slideInHorizontally(),
                    exit = fadeOut() + slideOutHorizontally(),
                ) {
                    Column {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            OutlinedTextField(
                                value = contributorName,
                                textStyle = LocalAppTypography.current.bodySmall,
                                onValueChange = { contributorName = it },
                                label = {
                                    Text(
                                        stringResource(Res.string.contributor_name),
                                        style =
                                            LocalAppTypography.current.labelSmall.copy(
                                                fontSize = 8.sp,
                                            ),
                                    )
                                },
                                placeholder = { Text(stringResource(Res.string.contributor_name), style = LocalAppTypography.current.bodySmall) },
                                singleLine = true,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = contributorEmail,
                                textStyle = LocalAppTypography.current.bodySmall,
                                onValueChange = { contributorEmail = it },
                                label = {
                                    Text(
                                        stringResource(Res.string.contributor_email),
                                        style =
                                            LocalAppTypography.current.labelSmall.copy(
                                                fontSize = 8.sp,
                                            ),
                                    )
                                },
                                placeholder = { Text(stringResource(Res.string.contributor_email), style = LocalAppTypography.current.bodySmall) },
                                singleLine = true,
                                isError = contributorEmail.isNotEmpty() && !contributorEmail.contains("@"),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                Row(
                    modifier =
                        Modifier.clickable {
                            useAnonymous = !useAnonymous
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        Checkbox(
                            checked = useAnonymous,
                            onCheckedChange = { useAnonymous = it },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.use_anonymous),
                        style = LocalAppTypography.current.bodySmall,
                    )
                }
            }
        },
    )
}