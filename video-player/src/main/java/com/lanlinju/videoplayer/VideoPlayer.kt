package com.lanlinju.videoplayer

import android.media.AudioManager
import android.provider.Settings
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

@JvmInline
value class ResizeMode private constructor(val value: Int) {
    companion object {
        val Fit = ResizeMode(0) /* 自适应 */
        val FixedWidth = ResizeMode(1)
        val FixedHeight = ResizeMode(2)
        val Fill = ResizeMode(3) /* 以改变视频纵横比的形式填充屏幕 */
        val Full = ResizeMode(4) /* 不改变视频纵横比的形式充满屏幕 */
        val Zoom = ResizeMode(5)
        val FixedRatio_16_9 = ResizeMode(6)
        val FixedRatio_4_3 = ResizeMode(7)
    }
}

private fun Modifier.adaptiveLayout(
    aspectRatio: Float,
    resizeMode: ResizeMode = ResizeMode.Fit
) = layout { measurable, constraints ->
    val resizedConstraint = constraints.resizeForVideo(resizeMode, aspectRatio)
    val placeable = measurable.measure(resizedConstraint)
    layout(constraints.maxWidth, constraints.maxHeight) {
        // Center x and y axis relative to the layout
        placeable.placeRelative(
            x = (constraints.maxWidth - resizedConstraint.maxWidth) / 2,
            y = (constraints.maxHeight - resizedConstraint.maxHeight) / 2
        )
    }
}

private fun Modifier.defaultPlayerDragGestures(playerState: VideoPlayerState) =
    this.pointerInput(Unit) {
        var downX = 0f
        var downY = 0f
        var isChangePosition = false
        var isChangeVolume = false
        var isChangeBrightness = false
        var gestureDownPosition = 0L
        var gestureDownVolume = 0
        var gestureDownBrightness = 0f
        val maxFastForwardDuration = 180_000L // 最大快进时长为180秒
        val threshold = (12.dp).toPx() // 拖动距离超过12dp后开始计算
        val horizontalEdgeExclusionPx = (40.dp).toPx() // 屏幕左右边缘安全区域，避免与系统侧滑返回冲突
        val audioManager = playerState.audioManager
        val layoutParams = playerState.window.attributes
        detectDragGestures(
            onDragStart = { offset ->
                downX = offset.x
                downY = offset.y
                isChangePosition = false
                isChangeVolume = false
                isChangeBrightness = false
            }, onDragEnd = {
                if (isChangePosition) playerState.onSeeked()
                playerState.onChanged()
            }) { change, dragAmount ->
            val deltaX = change.position.x - downX
            var deltaY = change.position.y - downY
            val absDeltaX = abs(deltaX)
            val absDeltaY = abs(deltaY)
            if (!isChangePosition && !isChangeVolume && !isChangeBrightness
                && downY > size.height * 0.15f && downY < size.height * 0.9f
            ) {
                if (absDeltaX > threshold || absDeltaY > threshold) {
                    if (absDeltaX >= threshold) {
                        // 水平滑动仅在下按点位于水平安全区域内时触发快进快退，
                        // 屏幕左右边缘 40dp 内留给系统侧滑返回手势
                        if (downX > horizontalEdgeExclusionPx && downX < size.width - horizontalEdgeExclusionPx) {
                            isChangePosition = true
                            gestureDownPosition = playerState.player.currentPosition
                        }
                    } else {
                        if (change.position.x < size.width * 0.5f) {
                            isChangeBrightness = true
                            if (layoutParams.screenBrightness < 0f) {
                                gestureDownBrightness = Settings.System.getInt(
                                    playerState.window.context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS
                                ).toFloat()
                            } else {
                                gestureDownBrightness = layoutParams.screenBrightness * 255
                            }
                        } else {
                            isChangeVolume = true
                            gestureDownVolume =
                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }
                    }
                }
            }
            if (isChangePosition && playerState.videoDurationMs.value > 0) {
                val deltaMs = deltaX / size.width * maxFastForwardDuration
                val seekTimePosition = gestureDownPosition + deltaMs
                val progress =
                    (seekTimePosition / playerState.videoDurationMs.value).coerceIn(0f..1f)
                playerState.onSeeking(progress)
            }
            if (isChangeVolume) {
                deltaY = -deltaY
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val deltaV = (max * deltaY * 3 / size.height).toInt()
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC, gestureDownVolume + deltaV, 0
                )
                val volumePercent = (gestureDownVolume + deltaV) / max.toFloat()
                playerState.onChangeVolume(volumePercent.coerceIn(0f..1f))
            }
            if (isChangeBrightness) {
                deltaY = -deltaY
                val deltaV = 255 * deltaY * 3 / size.height
                val brightnessPercent = ((gestureDownBrightness + deltaV) / 255)
                layoutParams.screenBrightness = brightnessPercent.coerceIn(0.01f..1f)
                playerState.window.attributes = layoutParams
                playerState.onChangeBrightness(brightnessPercent.coerceIn(0f..1f))
            }
        }
    }

private fun Modifier.defaultPlayerTapGestures(
    playerState: VideoPlayerState,
    haptics: HapticFeedback,
) = this.pointerInput(Unit) {
    detectTapGestures(
        onTap = {
            if (playerState.isControlUiVisible.value) {
                playerState.hideControlUi()
            } else {
                playerState.showControlUi()
            }
        },
        onDoubleTap = {
            if (playerState.isPlaying.value) {
                playerState.control.pause()
            } else {
                playerState.control.play()
            }
        },
        onLongPress = {
            if (playerState.isPlaying.value) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                playerState.onLongPress()
            }
        },
        onPress = {
            tryAwaitRelease() /* 等待按压结束 */
            if (playerState.isLongPress.value) {
                playerState.onDisLongPress()
            }
        }
    )
}

@Composable
private fun VideoPlayer(
    modifier: Modifier,
    playerState: VideoPlayerState,
    controller: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                playerState.onUserInteraction()
                false // don't consume, pass through to children
            }
            .defaultPlayerTapGestures(playerState, haptics)
            .defaultPlayerDragGestures(playerState)
    ) {

        AndroidView(
            modifier = Modifier
                .adaptiveLayout(
                    aspectRatio = playerState.videoSize.value.aspectRatio(),
                    resizeMode = playerState.videoResizeMode.value
                ),
            factory = { context ->
                SurfaceView(context).also {
                    playerState.player.setVideoSurfaceView(it)
                }
            }
        )

        AnimatedVisibility(
            visible = playerState.isControlUiVisible.value,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            controller()
        }
    }
}

/**
 * @param playerState state to attach to this composable.
 * @param lifecycleOwner required to manage the ExoPlayer instance.
 * @param controller you can use [VideoPlayerControl] or alternatively implement your own
 * */
@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    url: String,
    videoPosition: Long,
    playerState: VideoPlayerState,
    headers: Map<String, String> = emptyMap(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onBackPress: () -> Unit,
    controller: @Composable () -> Unit,
) {
    VideoPlayer(
        modifier = modifier,
        playerState = playerState,
        controller = controller
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> playerState.showControlUi()
                Lifecycle.Event.ON_STOP -> playerState.player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        playerState.registerListener()
        onDispose {
            playerState.unregisterListener()
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerState.player.release()
        }
    }

    LaunchedEffect(url) {
        playerState.player.setVideoUrl(url, headers)
        playerState.player.prepare()
        playerState.player.seekTo(videoPosition)
        playerState.player.playWhenReady = true
    }

    BackHandler {
        if (playerState.isControlUiVisible.value) {
            playerState.hideControlUi()
        } else {
            onBackPress()
        }
    }
}
