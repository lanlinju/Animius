package com.anime.danmaku.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density

/**
 * FloatingDanmakuTrack 中的弹幕在以下情况会移除:
 * - tick 中的逻辑帧检测
 * - 调用 [DanmakuTrack.clearAll]
 * 移除时必须调用 [onRemoveDanmaku] 避免内存泄露.
 */
@Stable
internal class FloatingDanmakuTrack<T : SizeSpecifiedDanmaku>(
    val trackIndex: Int,
    private val elapsedFrameTimeNanos: () -> Long,
    private val trackHeight: Int,
    private val trackWidth: Int,
    private val density: Density,
    var baseSpeedPxPerSecond: Float,
    var safeSeparation: Float,
    // val speedMultiplier: Float,
    private val onRemoveDanmaku: (FloatingDanmaku<T>) -> Unit // 某个弹幕需要消失, 必须调用此函数避免内存泄漏.
) : DanmakuTrack<T, FloatingDanmaku<T>> {
    internal val danmakuList: MutableList<FloatingDanmaku<T>> = mutableListOf()

    @Volatile
    internal var forbided: Boolean = false // 表示当前轨道是否可用(forbid的正确的过去式：forbade,过去分词：forbidden)

    /**
     * 判断给定的 [danmaku] 是否可以在轨道上放置，而不会与之前的弹幕重叠。
     *
     * 如果轨道是空的，则弹幕可以放置。如果轨道上最后一个弹幕完全可见，
     * 则进一步检查以确保新弹幕不会追赶上并与最后一个弹幕重叠。
     *
     * 放置的依据是比较新弹幕和最后一个弹幕的速度。如果新弹幕速度较慢
     * 或者在最后一个弹幕退出屏幕之前不会追赶上，则可以放置。
     *
     * @param danmaku 要放置的弹幕对象。
     * @return 如果弹幕可以无重叠地放置，则返回 `true`，否则返回 `false`。
     */
    override fun canPlace(danmaku: T): Boolean {
        if (forbided) return false // 优先保证用户发送的弹幕进入屏幕，防止其他弹幕竞争当前轨道
        if (danmakuList.isEmpty()) return true
        val lastDanmaku = danmakuList.last()

        // 如果最后一个弹幕没有完全可见，则新弹幕不能放置
        if (!lastDanmaku.isFullyVisible()) return false

        val lastSpeed = lastDanmaku.speedPxPerSecond
        val newSpeed =
            calculateLengthBasedSpeed(danmaku.danmakuWidth.toFloat(), density, baseSpeedPxPerSecond)

        // 如果新弹幕速度更慢，可以放置，因为它不会追上
        if (newSpeed <= lastSpeed) return true

        // 检查是否会发生碰撞
        return !checkHit(lastDanmaku, newSpeed, lastSpeed)
    }

    /**
     * 在当前轨道上放置指定的弹幕。
     * 弹幕的移动速度和放置时间将根据参数进行初始化，并将其添加到轨道的弹幕列表中。
     *
     * @param danmaku 要放置的弹幕对象。
     * @return 已放置的初始化的 [FloatingDanmaku] 对象。
     */
    override fun place(danmaku: T): FloatingDanmaku<T> {
        return FloatingDanmaku(
            danmaku,
            trackIndex = trackIndex,
            trackHeight = trackHeight,
            trackWidth = trackWidth,
            density = density,
            baseSpeedPxPerSecond = baseSpeedPxPerSecond,
            placeTimeNanos = elapsedFrameTimeNanos()
        ).also { danmakuList.add(it) }
    }

    /**
     * 清除轨道上的所有弹幕。
     * 在清除每个弹幕之前，会调用 [onRemoveDanmaku] 方法以确保
     * 相关资源被释放，防止内存泄漏。
     */
    override fun clearAll() {
        danmakuList.removeAll {
            onRemoveDanmaku(it)
            true
        }
    }

    /**
     * 该方法在逻辑帧期间定期调用，用于检查弹幕是否已经移出屏幕。
     * 如果弹幕已移出屏幕，将从轨道中移除，并调用 [onRemoveDanmaku] 来清理资源。
     */
    override fun tick() {
        if (danmakuList.isEmpty()) return
        danmakuList.removeAll { danmaku ->
            danmaku.isGone().also {
                if (it) {
                    onRemoveDanmaku(danmaku)
                }
            }
        }
    }

    /**
     * 检查弹幕是否已经完全移出屏幕。
     *
     * @return 如果弹幕的 X 轴位置小于其负宽度（即完全移出屏幕），返回 `true`，否则返回 `false`。
     */
    private fun FloatingDanmaku<T>.isGone(): Boolean {
        return screenPosX <= -danmaku.danmakuWidth
    }

    /**
     * 检查弹幕是否完全可见。
     *
     * @return 如果弹幕的 X 轴位置小于轨道宽度减去弹幕宽度和安全间隔，返回 `true`，否则返回 `false`。
     */
    private fun FloatingDanmaku<T>.isFullyVisible(): Boolean {
        return screenPosX <= trackWidth - danmaku.danmakuWidth - safeSeparation
    }

    override fun toString(): String {
        return "FloatingTrack(index=${trackIndex}, danmakuCount=${danmakuList.size})"
    }
}

/**
 * 一条浮动弹幕
 */
@Stable
internal class FloatingDanmaku<T : SizeSpecifiedDanmaku>(
    val danmaku: T,
    val placeTimeNanos: Long,
    private val trackIndex: Int,
    private val trackHeight: Int,
    private val trackWidth: Int,
    private val density: Density,
    private val baseSpeedPxPerSecond: Float,
) {
    /**
     * 弹幕初始时所在的位置，默认为轨道宽度[trackWidth]
     */
    var placePosition: Float = trackWidth.toFloat()

    /**
     * 弹幕在屏幕上的 Y 坐标位置, 由轨道高度和轨道索引计算得出.
     */
    val screenPosY = trackHeight.toFloat() * trackIndex

    /**
     * 弹幕在屏幕上的 X 坐标位置. 初始位置为轨道宽度 (即弹幕刚好在屏幕右侧边缘).
     * 使用 `mutableFloatStateOf` 以确保该值是可变且可组合的状态.
     */
    var screenPosX by mutableFloatStateOf(placePosition)

    /**
     * 弹幕在浮动轨道已滚动的距离, 是正数. 单位 px
     *
     * 例如, 如果弹幕现在在左侧刚被放置, 则等于 `0`.
     * 如果左边已滑到轨道最左侧, 则等于轨道长度.
     */
    var distanceX: Float = placePosition - screenPosX

    /**
     * 弹幕的速度, 以像素每秒为单位.
     * Unit px/s
     */
    var speedPxPerSecond =
        calculateLengthBasedSpeed(danmaku.danmakuWidth.toFloat(), density, baseSpeedPxPerSecond)

    /**
     * 在每一帧更新弹幕的位置.
     *
     * @param position 要更新到的新的 X 坐标位置.
     */
    fun updatePosX(position: Float) {
        screenPosX = position
    }

    override fun toString(): String {
        return "FloatingDanmaku(elapsedX=${distanceX}, y=$screenPosY)"
    }
}

internal fun calculateLengthBasedSpeed(
    length: Float,
    density: Density,
    baseSpeedPxPerSecond: Float
): Float {
    return with(density) {
        val ratio = calculateRatio(
            length.coerceIn(BaseTextLength.toPx(), MaxTextLength.toPx()),
            BaseTextLength.toPx(),
            MaxTextLength.toPx()
        )
        lerp(baseSpeedPxPerSecond, baseSpeedPxPerSecond * MaxSpeedMultiplier, ratio)
    }
}

internal fun lerp(start: Float, end: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * end
}

internal fun calculateRatio(value: Float, min: Float, max: Float): Float {
    return (value - min) / (max - min)
}

// 检测两个弹幕是否会碰撞
private fun <T : SizeSpecifiedDanmaku> FloatingDanmakuTrack<*>.checkHit(
    last: FloatingDanmaku<T>,
    newSpeed: Float,
    lastSpeed: Float,
): Boolean {
    val lastWidth = last.danmaku.danmakuWidth
    // 计算前一个弹幕离开屏幕的剩余距离和时间
    val exitDistance = last.screenPosX + lastWidth + safeSeparation
    val exitTime = exitDistance / lastSpeed

    // 计算新弹幕追上最后一个弹幕所需的时间
    val hitTime = (last.distanceX - lastWidth - safeSeparation) / (newSpeed - lastSpeed)

    // 如果新弹幕在最后一个弹幕退出屏幕之前不会追上，则返回 false (不会发生碰撞)
    return exitTime >= hitTime
}
