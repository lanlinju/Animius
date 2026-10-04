package com.lanlinju.videoplayer.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun Slider(
    value: Float,
    secondValue: Float,
    modifier: Modifier = Modifier,
    onClick: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.LightGray.copy(alpha = 0.38f),
    secondTrackColor: Color =Color.LightGray.copy(alpha = 0.78f),
    isSeeking: Boolean = false,
    onDpadUpDown: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    durationMs: Long = 0L,
) {
    val isAnimHeight = remember(isSeeking) { mutableStateOf(isSeeking) }
    val animHeight = animateDpAsState(
        targetValue = if (isAnimHeight.value) 4.dp else 2.dp,
        animationSpec = tween()
    )
    var isFocused by remember { mutableStateOf(false) }
    val isActive = isFocused || isSeeking
    val thumbSize by animateDpAsState(
        targetValue = if (isActive) 20.dp else 15.dp,
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val stepFraction = if (durationMs > 0) 10000f / durationMs else 0.02f
                    when (event.key) {
                        Key.DirectionRight -> {
                            onClick((value + stepFraction).coerceIn(0f, 1f))
                            true
                        }
                        Key.DirectionLeft -> {
                            onClick((value - stepFraction).coerceIn(0f, 1f))
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (isSeeking) {
                                onDpadUpDown?.invoke()
                                true
                            } else {
                                false // let focus system handle spatial navigation
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { offset ->
                    isAnimHeight.value = true
                    onClick(offset.x / size.width)
                }, onPress = {
                    tryAwaitRelease()
                    delay(150)
                    isAnimHeight.value = false
                })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                        change.consume()
                    },
                    onDragEnd = onValueChangeFinished
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {

        // track
        Box(
            modifier = Modifier
                .clip(
                    FractionClip(fraction = value, start = false)
                )
                .fillMaxWidth()
                .height(animHeight.value)
                .background(
                    color = trackColor,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        // 视频缓冲进度
        Box(
            modifier = Modifier
                .fillMaxWidth(secondValue)
                .height(animHeight.value)
                .background(
                    color = secondTrackColor,
                    shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)
                )
        )

        // 视频播放进度
        Box(
            modifier = Modifier
                .clip(
                    FractionClip(fraction = value, start = true)
                )
                .fillMaxWidth()
                .height(animHeight.value)
                .background(
                    color = color,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        // thumb
        Box(
            modifier = Modifier
                .align(
                    BiasAlignment(
                        horizontalBias = (value * 2) - 1f,
                        verticalBias = 0f
                    )
                )
                .size(thumbSize)
                .then(if (isFocused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                .background(color, CircleShape)
        )
    }
}

class FractionClip(val fraction: Float, val start: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Rectangle(
            rect = Rect(
                left = when (start) {
                    true -> 0f
                    false -> size.width * fraction
                },
                top = 0f,
                right = when (start) {
                    true -> size.width * fraction
                    false -> size.width
                },
                bottom = size.height,
            )
        )
    }
}