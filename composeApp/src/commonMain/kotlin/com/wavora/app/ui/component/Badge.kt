package com.wavora.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavora.app.ui.theme.typo
import com.wavora.app.ui.theme.wavoraPrimary
import com.wavora.app.ui.theme.wavoraTextSecondary
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.ai
import wavora.composeapp.generated.resources.explicit_content
import com.wavora.app.ui.theme.LocalAppTypography

@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Explicit,
            // Left as a neutral tone on purpose -- this is a content-advisory marker
            // (same convention as Spotify/Apple Music), not a brand accent, so it
            // shouldn't compete visually with real Wavora UI.
            contentDescription = stringResource(Res.string.explicit_content),
            tint = wavoraTextSecondary,
        )
    }
}

@Composable
fun AIBadge() {
    Box(
        modifier = Modifier.height(24.dp)
            .padding(3.dp)
            .wrapContentWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(wavoraPrimary),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = stringResource(Res.string.ai),
            color = { Color.White },
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp),
            style = LocalAppTypography.current.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewBadge() {
    Column {
        ExplicitBadge()
        AIBadge()
    }
}