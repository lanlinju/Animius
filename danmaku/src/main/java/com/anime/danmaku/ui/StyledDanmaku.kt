package com.anime.danmaku.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.anime.danmaku.api.Danmaku
import com.anime.danmaku.api.DanmakuLocation
import com.anime.danmaku.api.DanmakuPresentation
import java.util.UUID
import kotlin.math.floor
import android.graphics.Canvas as AndroidCanvas

/**
 * 已知大小长度的弹幕
 *
 * 只有知道弹幕的大小才能将弹幕放到[轨道][DanmakuTrack]中.
 */
@Immutable
interface SizeSpecifiedDanmaku {
    val danmakuWidth: Int
    val danmakuHeight: Int
}

/**
 * DanmakuState holds all params which [Canvas] needs to draw a danmaku text.
 */
@Immutable
data class StyledDanmaku(
    val presentation: DanmakuPresentation,
    internal val measurer: TextMeasurer,
    internal val baseStyle: TextStyle,
    internal val style: DanmakuStyle,
    internal val enableColor: Boolean,
    internal val isDebug: Boolean
) : SizeSpecifiedDanmaku {
    private val danmakuText = presentation.danmaku.run {
        val seconds = playTimeMillis.toFloat().div(1000)
        if (isDebug) "$text (${floor((seconds / 60)).toInt()}:${String.format2f(seconds % 60)})" else text
    }

    private val solidTextLayout = measurer.measure(
        text = danmakuText,
        style = baseStyle.merge(
            style.styleForText(
                color = if (enableColor) {
                    Color(0xFF_00_00_00L or presentation.danmaku.color.toUInt().toLong())
                } else Color.White,
            ).copy(textDecoration = if (presentation.isSelf) TextDecoration.Underline else null),
        ),
        overflow = TextOverflow.Clip,
        maxLines = 1,
        softWrap = false,
    )

    private val borderTextLayout = measurer.measure(
        text = danmakuText,
        style = baseStyle.merge(style.styleForBorder())
            .copy(textDecoration = if (presentation.isSelf) TextDecoration.Underline else null),
        overflow = TextOverflow.Clip,
        maxLines = 1,
        softWrap = false,
    )

    internal var imageBitmap: ImageBitmap? = null

    override val danmakuWidth: Int = solidTextLayout.size.width
    override val danmakuHeight: Int = solidTextLayout.size.height

    fun DrawScope.draw(screenPosX: () -> Float, screenPosY: () -> Float) {
        val cachedImage = imageBitmap ?: createDanmakuImageBitmap(solidTextLayout, borderTextLayout)
            .also { imageBitmap = it }

        drawImage(cachedImage, Offset(screenPosX(), screenPosY()))
    }
}

/*
/**
 *
 * Source: https://github.com/open-ani/ani/blob/92690b0505f6596da0ebe1bbad01dd865020fc6f/danmaku/ui/commonMain/StyledDanmaku.kt#L80
 * 绘制缓存好的`ImageBitmap` `drawImage` cpu使用率相比 直接绘制`drawText` cpu使用率
 * 降低`4% - %6`左右
 * 测试用的CPU: `snapdragon870`, 非release模式下
 */
internal fun DrawScope.drawDanmakuText(
    state: StyledDanmaku,
    screenPosX: () -> Float,
    screenPosY: () -> Float,
) {
    drawImage(state.imageBitmap, Offset(screenPosX(), screenPosY()))
}*/

/**
 * draw text
 */
/*internal fun DrawScope.drawDanmakuText(
    state: StyledDanmaku,
    screenPosX: () -> Float,
    screenPosY: () -> Float
) {
    val offset = Offset(screenPosX(), screenPosY())
    // draw black bolder first, then solid text
    drawText(textLayoutResult = state.borderTextLayout, topLeft = offset)
    drawText(
        textLayoutResult = state.solidTextLayout,
        topLeft = offset,
        textDecoration = if (state.presentation.isSelf) TextDecoration.Underline else null
    )
    "drawDanmakuText: $offset".log("drawDanmakuText")
}*/

/**
 * Create image snapshot of danmaku text.
 */
internal fun createDanmakuImageBitmap(
    solidTextLayout: TextLayoutResult,
    borderTextLayout: TextLayoutResult,
): ImageBitmap {
    // create a gpu-accelerated bitmap
    val destBitmap = Bitmap.createBitmap(
        borderTextLayout.size.width,
        borderTextLayout.size.height,
        Bitmap.Config.ARGB_8888
    )
    val destCanvas = Canvas(AndroidCanvas(destBitmap))

    TextPainter.paint(destCanvas, borderTextLayout)
    TextPainter.paint(destCanvas, solidTextLayout)

    return destBitmap.asImageBitmap().apply {
        prepareToDraw()
    }
}

internal fun dummyDanmaku(
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    style: DanmakuStyle,
    dummyText: String = "dummy 占位 攟 の \uD83D\uDE04"
): StyledDanmaku {
    return StyledDanmaku(
        presentation = DanmakuPresentation(
            Danmaku(
                UUID.randomUUID().toString(),
                "dummy",
                0L, "1",
                DanmakuLocation.NORMAL, dummyText, 0,
            ),
            isSelf = false
        ),
        measurer = measurer,
        baseStyle = baseStyle,
        style = style,
        enableColor = false,
        isDebug = false
    )
}