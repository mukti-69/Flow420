package io.github.aedev.flow.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

/**
 * TuTube header logo (replaces Flow's Canvas-drawn "F" glyph + separate
 * "FLOW" title text with a single combined icon+wordmark image, per
 * explicit request). Because the wordmark is now baked into this image,
 * the call site's separate title Text should be left empty - showing both
 * would duplicate the app name.
 *
 * The long-press-to-toggle-Deep-Flow interaction from the original
 * Canvas-drawn version is kept - only the drawn artwork changed. Deep Flow
 * active state is shown as a dimmed logo rather than the original's
 * animated vector glyph crossfade, since the new artwork is a raster
 * image, not vector paths.
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
        painter = painterResource(R.drawable.tutube_header_logo),
        contentDescription = null,
        contentScale = ContentScale.FillHeight,
        modifier =
            modifier
                .height(28.dp)
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
