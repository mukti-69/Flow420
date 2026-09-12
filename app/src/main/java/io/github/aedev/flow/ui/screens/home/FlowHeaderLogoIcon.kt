package io.github.aedev.flow.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import io.github.aedev.flow.R

/**
 * TuTube header logo icon (replaces Flow's Canvas-drawn "F" glyph with the
 * actual TuTube icon asset). The long-press-to-toggle-Deep-Flow interaction
 * from the original is kept - only the drawn artwork changed. Deep Flow
 * active state is now shown as a dimmed icon rather than an animated glyph
 * crossfade, since the source artwork is a raster image, not vector paths
 * like the original.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FlowHeaderLogoIcon(
    isDeepFlowActive: Boolean,
    onToggleDeepFlow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    val iconAlpha by animateFloatAsState(
        targetValue = if (isDeepFlowActive) 0.4f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "deepFlowIconAlpha",
    )

    Image(
        painter = painterResource(R.drawable.tutube_header_icon_only),
        contentDescription = null,
        modifier =
            modifier
                .alpha(iconAlpha)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleDeepFlow()
                    },
                ),
    )
}
