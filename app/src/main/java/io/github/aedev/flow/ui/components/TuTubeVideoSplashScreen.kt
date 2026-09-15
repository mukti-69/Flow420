package io.github.aedev.flow.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.aedev.flow.R
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Video-based splash screen. Replaces the procedurally-animated
 * FlowSplashScreen with a single pre-produced video
 * (res/raw/tutube_splash.mp4) that already contains the full branded
 * sequence baked in - logo, wordmark, tagline, loading bar, and creator
 * credit - provided directly rather than recreated in Compose.
 *
 * Muted by default: auto-playing audio on app launch is generally poor
 * UX and can surprise a user at unexpected volume - flagging this choice
 * rather than silently deciding it's definitely wanted. Easy to change
 * (volume = 1f) if audio is actually desired.
 *
 * Plays exactly once, then calls onAnimationFinished - same callback
 * contract as the FlowSplashScreen it replaces, so the call site in
 * MainActivity.kt doesn't need any other change. Guarded against being
 * called twice (playback-ended + timeout could otherwise both fire), and
 * backed by a hard timeout so a corrupt/stuck video can never leave the
 * user stranded on a black screen indefinitely.
 */
@Composable
fun TuTubeVideoSplashScreen(onAnimationFinished: () -> Unit) {
    val context = LocalContext.current
    val finished = remember { AtomicBoolean(false) }

    fun finishOnce() {
        if (finished.compareAndSet(false, true)) {
            onAnimationFinished()
        }
    }

    val exoPlayer =
        remember {
            ExoPlayer.Builder(context).build().apply {
                val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.tutube_splash}")
                setMediaItem(MediaItem.fromUri(uri))
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                finishOnce()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            // Safety fallback: if the video fails to load/decode for any
                            // reason, don't leave the user stuck on a black screen - just
                            // proceed into the app.
                            finishOnce()
                        }
                    },
                )
                prepare()
                playWhenReady = true
            }
        }

    // Hard timeout backstop (video is ~7.5s; 12s gives generous margin) in
    // case playback silently stalls without ever reaching STATE_ENDED or
    // firing an error.
    LaunchedEffect(Unit) {
        delay(12_000L)
        finishOnce()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
    )
}
