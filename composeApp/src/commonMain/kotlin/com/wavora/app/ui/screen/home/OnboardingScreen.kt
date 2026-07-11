package com.wavora.app.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavora.app.ui.theme.LocalAppTypography
import com.wavora.app.ui.theme.wavoraGradientEnd
import com.wavora.app.ui.theme.wavoraGradientStart
import com.wavora.app.ui.theme.wavoraPrimary
import com.wavora.app.ui.theme.wavoraSecondary
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.mono
import wavora.composeapp.generated.resources.onboarding_back
import wavora.composeapp.generated.resources.onboarding_get_started
import wavora.composeapp.generated.resources.onboarding_next
import wavora.composeapp.generated.resources.onboarding_page1_subtitle
import wavora.composeapp.generated.resources.onboarding_page1_title
import wavora.composeapp.generated.resources.onboarding_page2_subtitle
import wavora.composeapp.generated.resources.onboarding_page2_title
import wavora.composeapp.generated.resources.onboarding_page3_subtitle
import wavora.composeapp.generated.resources.onboarding_page3_title
import wavora.composeapp.generated.resources.onboarding_page4_subtitle
import wavora.composeapp.generated.resources.onboarding_page4_title
import wavora.composeapp.generated.resources.onboarding_page_indicator
import wavora.composeapp.generated.resources.onboarding_skip

/**
 * Content for a single onboarding page.
 *
 * UX rationale for the 4-page structure (see delivery report for detail): each page introduces
 * exactly ONE idea. Wavora's real differentiators — its own lyrics backend, automatic
 * downloads, community translations/contributions — are real information the user needs, but
 * cramming them onto one page (as the previous 3-page version did, folding "Lyrics, Canvas &
 * More" into a single dense subtitle) reads as a wall of text nobody finishes reading.
 * Splitting "lyrics" and "translations/contributions" into their own pages keeps each screen
 * to a single sentence-and-a-half, which is what people actually read during onboarding.
 */
private data class OnboardingPage(
    val emoji: String,
    val title: @Composable () -> String,
    val subtitle: @Composable () -> String,
    val accentColor: Color,
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val onboardingPages = listOf(
        OnboardingPage(
            emoji = "🎵",
            title = { stringResource(Res.string.onboarding_page1_title) },
            subtitle = { stringResource(Res.string.onboarding_page1_subtitle) },
            accentColor = wavoraPrimary,
        ),
        OnboardingPage(
            emoji = "📝",
            title = { stringResource(Res.string.onboarding_page2_title) },
            subtitle = { stringResource(Res.string.onboarding_page2_subtitle) },
            accentColor = wavoraSecondary,
        ),
        OnboardingPage(
            emoji = "🌐",
            title = { stringResource(Res.string.onboarding_page3_title) },
            subtitle = { stringResource(Res.string.onboarding_page3_subtitle) },
            accentColor = wavoraGradientStart,
        ),
        OnboardingPage(
            emoji = "📥",
            title = { stringResource(Res.string.onboarding_page4_title) },
            subtitle = { stringResource(Res.string.onboarding_page4_subtitle) },
            accentColor = wavoraGradientEnd,
        ),
    )

    val pagerState = rememberPagerState { onboardingPages.size }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F)),
    ) {
        // Subtle gradient background that changes per page
        val bgColor by animateColorAsState(
            targetValue = onboardingPages[pagerState.currentPage].accentColor.copy(alpha = 0.07f),
            animationSpec = tween(600),
            label = "bg",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(bgColor, Color.Transparent))),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo
            Spacer(Modifier.height(32.dp))
            Icon(
                painter = painterResource(Res.drawable.mono),
                contentDescription = "Wavora",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(40.dp),
            )

            // Pager. Announced to screen readers as "Page X of Y" via a live region on the
            // container, since the individual page content already fades in/out and a raw
            // swipe gesture alone gives no non-visual signal of progress.
            val pageAnnouncement = stringResource(
                Res.string.onboarding_page_indicator,
                pagerState.currentPage + 1,
                onboardingPages.size,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = pageAnnouncement
                    },
            ) { page ->
                val isCurrentPage = pagerState.currentPage == page
                val alpha by animateFloatAsState(
                    targetValue = if (isCurrentPage) 1f else 0.4f,
                    animationSpec = tween(300),
                    label = "alpha_$page",
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp)
                        .alpha(alpha)
                        // Content is already announced via the pager's own live region above;
                        // clearing here avoids screen readers reading every off-screen page too.
                        .clearAndSetSemantics { },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Emoji in a glowing circle
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        onboardingPages[page].accentColor.copy(alpha = 0.3f),
                                        onboardingPages[page].accentColor.copy(alpha = 0.05f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = onboardingPages[page].emoji,
                            fontSize = 52.sp,
                        )
                    }

                    Spacer(Modifier.height(36.dp))

                    AnimatedVisibility(
                        visible = isCurrentPage,
                        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
                        exit = fadeOut(tween(200)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = onboardingPages[page].title(),
                                style = LocalAppTypography.current.headlineMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = onboardingPages[page].subtitle(),
                                style = LocalAppTypography.current.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.65f),
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }
            }

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    // Decorative — the real progress announcement lives on the pager above.
                    .clearAndSetSemantics { },
            ) {
                repeat(onboardingPages.size) { i ->
                    val isSelected = pagerState.currentPage == i
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "dot_w_$i",
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) wavoraPrimary else Color.White.copy(alpha = 0.25f),
                        animationSpec = tween(300),
                        label = "dot_c_$i",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(50))
                            .background(color),
                    )
                }
            }

            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

                // Skip / Back button
                TextButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        } else {
                            onFinish()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (pagerState.currentPage > 0) {
                            stringResource(Res.string.onboarding_back)
                        } else {
                            stringResource(Res.string.onboarding_skip)
                        },
                        color = Color.White.copy(alpha = 0.5f),
                        style = LocalAppTypography.current.labelLarge,
                    )
                }

                // Next / Get Started button
                Button(
                    onClick = {
                        if (isLastPage) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(50),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(wavoraGradientStart, wavoraGradientEnd),
                                ),
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isLastPage) {
                                stringResource(Res.string.onboarding_get_started)
                            } else {
                                stringResource(Res.string.onboarding_next)
                            },
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = LocalAppTypography.current.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
