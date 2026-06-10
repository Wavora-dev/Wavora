package com.wavora.app.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.wavora.app.expect.ui.PlatformBackdrop
import com.wavora.app.viewModel.SharedViewModel
import kotlin.reflect.KClass

@Composable
actual fun LiquidGlassAppBottomNavigationBar(
    startDestination: Any,
    navController: NavController,
    backdrop: PlatformBackdrop,
    viewModel: SharedViewModel,
    isScrolledToTop: Boolean,
    onOpenNowPlaying: () -> Unit,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit
) {
}