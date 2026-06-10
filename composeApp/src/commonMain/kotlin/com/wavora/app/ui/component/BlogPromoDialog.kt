package com.wavora.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.wavora.app.ui.theme.typo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.blog_promo_message
import wavora.composeapp.generated.resources.blog_promo_title
import wavora.composeapp.generated.resources.later
import wavora.composeapp.generated.resources.visit_blog
import wavora.composeapp.generated.resources.circle_app_icon


@Composable
@ExperimentalMaterial3Api
fun BlogPromoDialog(
    onDismissRequest: () -> Unit,
    onVisitBlog: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        onDismissRequest = {
            onDismissRequest.invoke()
        },
        confirmButton = {
            TextButton(onClick = {
                onVisitBlog.invoke()
                uriHandler.openUri("https://wavora.app")
            }) {
                Text(
                    stringResource(Res.string.visit_blog),
                    style = typo().bodySmall,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismissRequest.invoke()
            }) {
                Text(
                    stringResource(Res.string.later),
                    style = typo().bodySmall,
                )
            }
        },
        icon = {
            Image(
                    painter = painterResource(Res.drawable.circle_app_icon),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(48.dp),
            )
        },
        title = {
            Text(
                stringResource(Res.string.blog_promo_title),
                style = typo().labelSmall,
            )
        },
        text = {
            Text(
                stringResource(Res.string.blog_promo_message),
                textAlign = TextAlign.Center,
                style = typo().bodySmall,
            )
        },
    )
}
