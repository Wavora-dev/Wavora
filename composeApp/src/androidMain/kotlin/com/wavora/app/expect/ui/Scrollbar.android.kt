package com.wavora.app.expect.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun HorizontalScrollBar(
    modifier: Modifier,
    scrollState: LazyListState,
) {
}

@Composable
actual fun VerticalScrollBar(
    modifier: Modifier,
    scrollState: LazyListState,
) {
}

@Composable
actual fun VerticalGridScrollBar(
    modifier: Modifier,
    scrollState: LazyGridState,
) {
}

@Composable
actual fun HorizontalGridScrollBar(
    modifier: Modifier,
    scrollState: LazyGridState,
) {
}