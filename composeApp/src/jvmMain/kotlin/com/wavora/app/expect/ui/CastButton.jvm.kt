package com.wavora.app.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Google Cast es Android-only. En Desktop este botón no renderiza nada. */
@Composable
actual fun CastButton(modifier: Modifier) {
    // no-op
}
