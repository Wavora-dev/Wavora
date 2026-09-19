package com.wavora.app.expect.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun HorizontalScrollBar(
    modifier: Modifier,
    scrollState: LazyListState,
)

// AUDIT FIX (auditoría visual de scroll - Biblioteca y Búsqueda): mismo
// componente que HorizontalScrollBar de arriba (que ya arreglaba los chips
// de género), pero para las listas verticales. En Desktop, una LazyColumn
// larga sin scrollbar no da ninguna señal de que hay más contenido debajo
// ni permite arrastrar para navegar rápido - justo el problema que ya se
// había resuelto para los chips horizontales.
@Composable
expect fun VerticalScrollBar(
    modifier: Modifier,
    scrollState: LazyListState,
)

// Igual que VerticalScrollBar pero para LazyVerticalGrid (GridLibraryPlaylist,
// usado por la mayoría de los tabs de Biblioteca: playlists de YouTube, Mix
// para vos, playlists locales, favoritos, descargas, podcasts).
@Composable
expect fun VerticalGridScrollBar(
    modifier: Modifier,
    scrollState: LazyGridState,
)

// AUDIT FIX (Home - "Selecciones rápidas" y "Moods & moments" sin forma de
// scrollear): estas dos secciones usan LazyHorizontalGrid (no LazyRow), así
// que nunca compartieron el fix que ya tenían los chips de género de arriba
// (que usan LazyRow + HorizontalScrollBar). En Desktop, sin scrollbar y sin
// soporte de rueda del mouse, un usuario con mouse no tenía ninguna forma
// obvia de ver el resto del contenido - solo quedaba un pedacito de la
// siguiente card asomando en el borde, sin poder llegar a ella.
@Composable
expect fun HorizontalGridScrollBar(
    modifier: Modifier,
    scrollState: LazyGridState,
)