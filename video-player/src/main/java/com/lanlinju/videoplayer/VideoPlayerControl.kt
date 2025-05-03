package com.lanlinju.videoplayer


import android.content.res.Configuration
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imherrera.videoplayer.icons.Fullscreen
import com.imherrera.videoplayer.icons.FullscreenExit
import com.lanlinju.videoplayer.component.Slider
import com.lanlinju.videoplayer.icons.ArrowBackIos
import com.lanlinju.videoplayer.icons.Pause
import com.lanlinju.videoplayer.icons.Subtitles
import com.lanlinju.videoplayer.icons.SubtitlesOff
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoPlayerControl(
    state: VideoPlayerState,
    title: String,
    subtitle: String? = null,
    background: Color = Color.Black.copy(0.2f),
    contentColor: Color = Color.LightGray,
    progressLineColor: Color = MaterialTheme.colorScheme.inversePrimary,
    danmakuEnabled: Boolean,
    onBackClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onDanmakuClick: (Boolean) -> Unit = {},
    optionsContent: (@Composable () -> Unit)? = null,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(
                    start = horizontalPadding(),
                    end = horizontalPadding(),
                    top = 18.dp
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ControlHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = title,
                    subtitle = subtitle,
                    isSeeking = state.isSeeking.value,
                    onBackClick = onBackClick,
                    optionsContent = optionsContent,
                )

                Spacer(Modifier.size(1.dp))

                BottomControlBar(
                    modifier = Modifier.fillMaxWidth(),
                    progressLineColor = progressLineColor,
                    state = state,
                    enabledDanmaku = danmakuEnabled,
                    onNextClick = onNextClick,
                    onDanmakuClick = onDanmakuClick
                )
            }
        }
    }
}

@Composable
private fun ControlHeader(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    isSeeking: Boolean,
    onBackClick: (() -> Unit)?,
    optionsContent: (@Composable () -> Unit)? = null,
) {
    if (isSeeking) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = Modifier.size(BigIconButtonSize),
            onClick = { onBackClick?.invoke() }
        ) {
            Icon(imageVector = Icons.Rounded.ArrowBackIos, contentDescription = null)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            subtitle?.let {
                Text(
                    text = it,
                    color = LocalContentColor.current.copy(0.80f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        optionsContent?.invoke()
    }
}

@Composable
private fun BottomControlBar(
    modifier: Modifier,
    progressLineColor: Color,
    state: VideoPlayerState,
    enabledDanmaku: Boolean,
    onNextClick: () -> Unit,
    onDanmakuClick: (Boolean) -> Unit
) {
    val timestamp =
        remember(
            state.videoDurationMs.value,
            state.videoPositionMs.value.milliseconds.inWholeSeconds
        ) {
            prettyVideoTimestamp(
                state.videoPositionMs.value.milliseconds,
                state.videoDurationMs.value.milliseconds
            )
        }

    Column(modifier = modifier) {
        if (!state.isSeeking.value) {
            TimelineControl(
                timestamp = timestamp,
                isFullScreen = state.isFullscreen.value,
                onFullScreenToggle = { state.control.setFullscreen(!state.isFullscreen.value) }
            )
        }

        Slider(
            value = state.videoProgress.value.safeValue(),
            secondValue = state.videoBufferedProgress.value.safeValue(),
            onClick = { state.onClickSlider(it) },
            onValueChange = { state.onSeeking(it) },
            onValueChangeFinished = { state.onSeeked() },
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            isSeeking = state.isSeeking.value,
            color = progressLineColor,
        )

        if (!state.isSeeking.value) {
            PlaybackControl(
                isPlaying = state.isPlaying.value,
                enabledDanmaku = enabledDanmaku,
                onPlayPause = { if (state.isPlaying.value) state.control.pause() else state.control.play() },
                onNextClick = onNextClick,
                onDanmakuClick = onDanmakuClick,
                speedText = state.speedText.value,
                resizeText = state.resizeText.value,
                onSpeedClick = state::showSpeedUi,
                onResizeClick = state::showResizeUi,
                onEpisodeClick = state::showEpisodeUi
            )
        } else Spacer(modifier = Modifier.size(MediumIconButtonSize))
    }
}

@Composable
private fun TimelineControl(
    timestamp: String,
    isFullScreen: Boolean,
    onFullScreenToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = timestamp, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.weight(1.0f))
        AdaptiveIconButton(
            modifier = Modifier.size(SmallIconButtonSize),
            onClick = onFullScreenToggle
        ) {
            Icon(
                imageVector = if (isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun PlaybackControl(
    isPlaying: Boolean,
    enabledDanmaku: Boolean,
    onPlayPause: () -> Unit,
    onNextClick: () -> Unit,
    onDanmakuClick: (Boolean) -> Unit,
    speedText: String,
    resizeText: String,
    onSpeedClick: () -> Unit,
    onResizeClick: () -> Unit,
    onEpisodeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(isPlaying, onPlayPause)
            NextEpisodeIcon(onClick = onNextClick)
            DanmakuIcon(onClick = onDanmakuClick, danmakuEnabled = enabledDanmaku)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AdaptiveTextButton(text = "选集", onClick = onEpisodeClick)
            AdaptiveTextButton(text = speedText, onClick = onSpeedClick)
            AdaptiveTextButton(text = resizeText, onClick = onResizeClick)
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onPlayPause: () -> Unit) {
    AdaptiveIconButton(
        modifier = Modifier.size(MediumIconButtonSize),
        onClick = onPlayPause
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null
        )
    }
}

@Composable
private fun horizontalPadding(): Dp {
    return 8.dp + if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        24.dp
    } else 0.dp
}

private fun Float?.safeValue() = this?.takeIf { !it.isNaN() } ?: 0f

@Composable
private fun DanmakuIcon(
    danmakuEnabled: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveIconButton(
        onClick = { onClick(!danmakuEnabled) },
        modifier.size(MediumIconButtonSize),
    ) {
        if (danmakuEnabled) {
            Icon(Icons.Rounded.Subtitles, contentDescription = "禁用弹幕")
        } else {
            Icon(Icons.Rounded.SubtitlesOff, contentDescription = "启用弹幕")
        }
    }
}

@Composable
private fun NextEpisodeIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveIconButton(
        modifier = modifier.size(MediumIconButtonSize), // 下一集
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_next),
            contentDescription = "下一集"
        )
    }
}

@Composable
fun AdaptiveTextButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    color: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    AdaptiveIconButton(
        modifier = modifier.size(MediumIconButtonSize),
        onClick = onClick
    ) {
        Text(
            text = text,
            color = color,
            style = style,
        )
    }
}

@Composable
private fun AdaptiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabledIndication: Boolean = true,
    content: @Composable () -> Unit
) {
    val indication = LocalIndication.current

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = if (enabledIndication) indication else null
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private val BigIconButtonSize = 52.dp
private val MediumIconButtonSize = 42.dp
private val SmallIconButtonSize = 32.dp