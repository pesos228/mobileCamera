package com.android.mobilecamera.feature.viewer.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.compose.material3.buttons.MuteButton
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    path: String,
    onReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State
    var isPlayerVisible by remember { mutableStateOf(true) }
    var videoAlpha by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }

    val exoPlayer = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(path.toUri()))
            prepare()
            playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_OFF

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        duration = this@apply.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
        }
    }

    // Плавное появление видео с задержкой
    LaunchedEffect(exoPlayer) {
        while (exoPlayer.playbackState != Player.STATE_READY) {
            delay(50)
        }
        // Даём время фону затемниться
        videoAlpha = 1f
        onReady()
    }

    // Обновление позиции
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                currentPosition = exoPlayer.currentPosition
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Периодическое обновление позиции
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            delay(100)
        }
    }

    // Автоскрытие контролов
    LaunchedEffect(areControlsVisible, isPlaying, isSeeking) {
        if (areControlsVisible && isPlaying && !isSeeking) {
            delay(3000)
            areControlsVisible = false
        }
    }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_STOP -> isPlayerVisible = false
                Lifecycle.Event.ON_RESUME -> isPlayerVisible = true
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isPlayerVisible) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(videoAlpha)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        areControlsVisible = !areControlsVisible
                    }
            )
        }

        // Центральная кнопка Play/Pause
        AnimatedVisibility(
            visible = (areControlsVisible || !isPlaying) && videoAlpha > 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    PlayPauseButton(
                        player = exoPlayer,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        // Нижние контролы
        AnimatedVisibility(
            visible = areControlsVisible && videoAlpha > 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VideoControls(
                exoPlayer = exoPlayer,
                currentPosition = currentPosition,
                duration = duration,
                onSeekStarted = { isSeeking = true },
                onSeekFinished = { isSeeking = false }
            )
        }
    }
}

@UnstableApi
@Composable
private fun VideoControls(
    exoPlayer: ExoPlayer,
    currentPosition: Long,
    duration: Long,
    onSeekStarted: () -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Slider(
            value = if (duration > 0) currentPosition.toFloat() else 0f,
            onValueChange = { newValue ->
                onSeekStarted()
                exoPlayer.seekTo(newValue.toLong())
            },
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayPauseButton(
                        player = exoPlayer,
                        modifier = Modifier.size(32.dp)
                    )

                    Text(
                        text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }

                MuteButton(
                    player = exoPlayer,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatTime(timeMs: Long): String {
    if (timeMs <= 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60
    return String.format("%02d:%02d", minutes, seconds)
}