package com.wavora.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavora.app.ui.theme.wavoraBorder
import com.wavora.app.ui.theme.wavoraSurface
import com.wavora.app.ui.theme.wavoraTextSecondary
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.coming_soon
import wavora.composeapp.generated.resources.introducing_wavora_chart

// Renamed from SimpMusicChart.kt — leftover filename from the SimpMusic fork, the
// composable inside was already called WavoraChartButton. The colors were also
// off-brand (a reddish/maroon pair that doesn't exist anywhere in Color.kt), which
// made this promo pill look like it belonged to a different app. Now uses the same
// surface/border tokens as every other card in Wavora, and the copy is localized
// and honestly labeled as a preview feature instead of looking like a dead tap target.
@Composable
fun WavoraChartButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = wavoraSurface,
        border = BorderStroke(1.dp, wavoraBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Sparkles icon
            Text(
                text = "✨",
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp),
            )

            Text(
                text = stringResource(Res.string.introducing_wavora_chart),
                fontSize = 13.sp,
                color = wavoraTextSecondary,
                fontWeight = FontWeight.Normal,
            )
            Text(
                text = " · ${stringResource(Res.string.coming_soon)}",
                fontSize = 13.sp,
                color = wavoraTextSecondary,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Preview
@Composable
fun PreviewWavoraChartButton() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        WavoraChartButton(onClick = {})
    }
}
