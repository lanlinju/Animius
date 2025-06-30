package com.anime.danmaku.ui

import androidx.annotation.UiThread
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anime.danmaku.api.DanmakuLocation
import com.anime.danmaku.api.DanmakuPresentation
import kotlin.math.floor


@Composable
fun rememberDanmakuHostState(
    danmakuConfig: DanmakuConfig = DanmakuConfig.Default,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium,
): DanmakuHostState {
    val density = LocalDensity.current
    val danmakuTextMeasurer = rememberTextMeasurer(200)
    return remember {
        DanmakuHostState(danmakuConfig, density, baseStyle, danmakuTextMeasurer)
    }
}

class DanmakuHostState(
    val config: DanmakuConfig = DanmakuConfig.Default,
    val density: Density,
    val baseStyle: TextStyle,
    val danmakuTextMeasurer: TextMeasurer,
    val trackStubMeasurer: TextMeasurer = danmakuTextMeasurer,
) {
    internal var hostWidth by mutableIntStateOf(0)
    internal var hostHeight by mutableIntStateOf(0)

    /**
     * 所有在 [floatingTracks], [topTracks] 和 [bottomTracks] 弹幕.
     */
    internal val presentFloatingDanmaku = mutableStateListOf<FloatingDanmaku<StyledDanmaku>>()
    internal val presentFixedDanmaku = mutableListOf<FixedDanmaku<StyledDanmaku>>()

    // 弹幕轨道
    internal val floatingTracks = mutableListOf<FloatingDanmakuTrack<StyledDanmaku>>()
    internal val topTracks = mutableListOf<FixedDanmakuTrack<StyledDanmaku>>()
    internal val bottomTracks = mutableListOf<FixedDanmakuTrack<StyledDanmaku>>()

    /* 计时器，用于计算弹幕在屏幕上的[FloatingDanmaku]滚动距离和[FixedDanmaku]停留时间 */
    internal var elapsedFrameTimeNanos: Long = 0L
    internal val isDebug by mutableStateOf(config.isDebug)
    internal var paused by mutableStateOf(false)
    internal var speedMultiplier = 1f

    internal val trackWidth by derivedStateOf { hostWidth }
    internal val trackHeight by lazy {
        val dummyDanmaku = dummyDanmaku(
            trackStubMeasurer,
            baseStyle,
            config.style,
            "Lorem Ipsum"
        )
        val verticalPadding = with(density) {
            (config.danmakuTrackProperties.verticalPadding * 2).dp.toPx()
        }
        val trackHeight = (dummyDanmaku.danmakuHeight + verticalPadding).toInt()
        trackHeight
    }

    /**
     * 尝试发送弹幕到屏幕, 如果当前时间点已没有更多轨道可以使用则会发送失败.
     *
     * 对于一定发送成功的版本, 请查看 [DanmakuHostState.send].
     * 若是浮动弹幕则加入到 [presentFloatingDanmaku], 固定弹幕加到 [presentFixedDanmaku].
     *
     * @return 如果发送成功则返回 true
     * @see DanmakuHostState.send
     */
    fun trySend(danmaku: DanmakuPresentation): Boolean {
        val styledDanmaku = StyledDanmaku(
            presentation = danmaku,
            measurer = danmakuTextMeasurer,
            baseStyle = baseStyle,
            style = config.style,
            enableColor = config.enableColor,
            isDebug = config.isDebug,
        )
        return when (danmaku.danmaku.location) {
            DanmakuLocation.NORMAL -> {
                val floatingDanmaku = floatingTracks.firstNotNullOfOrNull {
                    it.tryPlace(styledDanmaku)
                }
                floatingDanmaku?.also(presentFloatingDanmaku::add) != null
            }

            DanmakuLocation.TOP -> {
                val floatingDanmaku = topTracks.firstNotNullOfOrNull {
                    it.tryPlace(styledDanmaku)
                }
                floatingDanmaku?.also(presentFixedDanmaku::add) != null
            }

            DanmakuLocation.BOTTOM -> {
                val floatingDanmaku = bottomTracks.firstNotNullOfOrNull {
                    it.tryPlace(styledDanmaku)
                }
                floatingDanmaku?.also(presentFixedDanmaku::add) != null
            }
        }
    }

    /**
     * 逻辑帧 tick, 主要用于移除超出屏幕外或超过时间的弹幕
     */
    @UiThread
    fun tick() {
        floatingTracks.forEach { it.tick() }
        topTracks.forEach { it.tick() }
        bottomTracks.forEach { it.tick() }
    }

    /**
     * 在每一帧中调用，主要用于更新浮动弹幕的位置。
     * 该方法为一个协程循环，确保弹幕的位置根据时间的变化得到更新。
     */
    internal suspend fun interpolateFrameLoop() {
        var lastFrameTimeNanos = withFrameNanos { it }

        while (true) {
            withFrameNanos { currentFrameTimeNanos ->
                val delta = currentFrameTimeNanos - lastFrameTimeNanos

                elapsedFrameTimeNanos += delta
                lastFrameTimeNanos = currentFrameTimeNanos

                // 更新浮动弹幕的位置
                for (danmaku in presentFloatingDanmaku) {
                    val time = delta / 1_000_000_000f
                    danmaku.distanceX += time * danmaku.speedPxPerSecond * speedMultiplier // 累计行驶的距离
                    danmaku.updatePosX(danmaku.placePosition - danmaku.distanceX)
                }
            }
        }
    }

    /**
     * 设置弹幕轨道的数量。根据Host高度和配置中的显示区域比例，计算出轨道数量并初始化。
     */
    internal fun setTrackCount() {
        val trackCount = floor(hostHeight / trackHeight * config.displayArea)
            .coerceAtLeast(1f)
            .toInt()
        initTrackCount(trackCount, config)
    }

    /**
     * 更新弹幕轨道数量, 同时也会更新轨道属性
     */
    @UiThread
    private fun initTrackCount(count: Int, config: DanmakuConfig) {
        val newFloatingTrackSpeed =
            with(density) { this@DanmakuHostState.config.baseSpeed.dp.toPx() }
        val newFloatingTrackSafeSeparation =
            with(density) { this@DanmakuHostState.config.safeSeparation.toPx() }

        floatingTracks.setTrackCountImpl(if (config.enableFloating) count else 0) { index ->
            FloatingDanmakuTrack(
                trackIndex = index,
                elapsedFrameTimeNanos = { elapsedFrameTimeNanos },
                trackHeight = trackHeight,
                trackWidth = trackWidth,
                density = density,
                baseSpeedPxPerSecond = newFloatingTrackSpeed,
                safeSeparation = newFloatingTrackSafeSeparation,
                // speedMultiplier = floatingSpeedMultiplierState,
                onRemoveDanmaku = { removed ->
                    presentFloatingDanmaku.removeFirst { it.danmaku == removed.danmaku }
                },
            )
        }
        topTracks.setTrackCountImpl(if (config.enableTop) count else 0) { index ->
            FixedDanmakuTrack(
                trackIndex = index,
                elapsedFrameTimeNanos = { elapsedFrameTimeNanos },
                trackHeight = trackHeight,
                trackWidth = trackWidth,
                hostHeight = hostHeight,
                fromBottom = false,
                durationMillis = config.danmakuTrackProperties.fixedDanmakuPresentDuration,
                onRemoveDanmaku = { removed -> presentFixedDanmaku.removeFirst { it.danmaku == removed.danmaku } },
            )
        }
        bottomTracks.setTrackCountImpl(if (config.enableBottom) count else 0) { index ->
            FixedDanmakuTrack(
                trackIndex = index,
                elapsedFrameTimeNanos = { elapsedFrameTimeNanos },
                trackHeight = trackHeight,
                trackWidth = trackWidth,
                hostHeight = hostHeight,
                fromBottom = true,
                durationMillis = config.danmakuTrackProperties.fixedDanmakuPresentDuration,
                onRemoveDanmaku = { removed -> presentFixedDanmaku.removeFirst { it.danmaku == removed.danmaku } },
            )
        }
    }

    /**
     * 清除当前显示的所有弹幕。
     */
    @UiThread
    fun clearPresentDanmaku() {
        floatingTracks.forEach { it.clearAll() }
        topTracks.forEach { it.clearAll() }
        bottomTracks.forEach { it.clearAll() }

        check(presentFloatingDanmaku.size == 0) {
            "presentFloatingDanmaku is not totally cleared after releasing track."
        }
        check(presentFixedDanmaku.size == 0) {
            "presentFixedDanmaku is not totally cleared after releasing track."
        }
    }

    /**
     * 清空屏幕并以这些弹幕填充. 常见于快进/快退时
     * Todo: 将[list] 填充到屏幕.
     *
     * @param list 顺序为由距离当前时间近到远.
     * @param playTimeMillis 当前播放器的时间
     */
    suspend fun repopulate(
        list: List<DanmakuPresentation> = emptyList(),
        playTimeMillis: Long = 0L
    ) {
        clearPresentDanmaku()
    }

    /**
     * Sends the danmaku to the screen, guaranteeing its placement on a track without collisions.
     * Todo: 已知Bug，发送的弹幕会被后面的弹幕撞击，导致重叠
     *
     * @param danmaku The danmaku to be sent to the screen.
     */
    suspend fun send(danmaku: DanmakuPresentation) {
        if (trySend(danmaku)) return
        val styledDanmaku = StyledDanmaku(
            presentation = danmaku,
            measurer = danmakuTextMeasurer,
            baseStyle = baseStyle,
            style = config.style,
            enableColor = config.enableColor,
            isDebug = config.isDebug
        )

        // Randomly select a track from floatingTracks
        val selectedTrack = floatingTracks.random()

        // Mark the track as unavailable
        selectedTrack.forbided = true

        // Get the last FloatingDanmaku from the selected track
        val last = selectedTrack.danmakuList.lastOrNull() ?: run {
            selectedTrack.place(styledDanmaku).let(presentFloatingDanmaku::add)
            selectedTrack.forbided = false
            return
        }
        // Calculate the remaining distance for the last danmaku to fully enter the screen
        val safeSeparation = 16.dp.toPx(density)

        // Place the new danmaku
        val sendDanmaku = selectedTrack.place(styledDanmaku)

        if (last.speedPxPerSecond < sendDanmaku.speedPxPerSecond) {
            // Calculate the exit time for the last danmaku
            val exitDistance = last.screenPosX + last.danmaku.danmakuWidth + safeSeparation
            val exitTime = exitDistance / last.speedPxPerSecond

            // Calculate how far the new danmaku will move during the last's exit time
            val distance = sendDanmaku.speedPxPerSecond * exitTime

            // Set the new danmaku's position to avoid overtaking the last one
            sendDanmaku.placePosition = distance.coerceAtLeast(trackWidth.toFloat())
        } else {
            // Calculate the remaining distance for the last danmaku to fully enter the screen
            val remainingDistance = last.danmaku.danmakuWidth - last.distanceX
            // Place the new danmaku directly behind the last one
            sendDanmaku.placePosition += remainingDistance + safeSeparation
        }
        presentFloatingDanmaku.add(sendDanmaku)
        selectedTrack.forbided = false
    }

    fun play() {
        paused = false
    }

    fun pause() {
        paused = true
    }

    /**
     * 设置弹幕的倍速
     * speedMultiplier默认为1
     */
    fun speed(speed: Float) {
        require(speed > 0f) { "Speed must be greater than 0." }
        speedMultiplier = speed
    }
}

private fun <D : SizeSpecifiedDanmaku, DT, T : DanmakuTrack<D, DT>>
        MutableList<T>.setTrackCountImpl(count: Int, newInstance: (index: Int) -> T) {
    when {
        size == count -> return
        // 清除 track 的同时要把 track 里的 danmaku 也要清除
        count < size -> repeat(size - count) { removeAt(lastIndex).clearAll() }
        else -> addAll(List(count - size) { newInstance(size + it) })
    }
}

private inline fun <T> MutableList<T>.removeFirst(predicate: (T) -> Boolean): T? {
    val index = indexOfFirst(predicate)
    if (index == -1) return null
    return removeAt(index)
}

private fun Dp.toPx(density: Density): Float {
    return with(density) { toPx() }
}