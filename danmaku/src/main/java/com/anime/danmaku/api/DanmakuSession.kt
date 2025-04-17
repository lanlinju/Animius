package com.anime.danmaku.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Source : https://github.com/open-ani/ani/blob/339fa3b41b20a2db951a130121f5611106f2c002/danmaku/api/src/commonMain/kotlin/DanmakuCollection.kt#L70
 */
interface DanmakuSession {
    val totalCount: Flow<Int?> get() = emptyFlow()

    /**
     * 创建一个随视频进度 [curTimeMillis] 匹配到的弹幕数据流.
     *
     * [curTimeMillis] 当前的视频播放进度
     * [isPlayingFlow] 表示当前视频是否处于播放/暂停状态，如果为暂停状态，将不会轮询发送弹幕
     */
    fun at(
        curTimeMillis: () -> Duration,
        isPlayingFlow: Flow<Boolean> = flowOf(true)
    ): Flow<DanmakuEvent>
}

sealed class DanmakuEvent {
    /**
     * 发送一个新弹幕
     */
    class Add(val danmaku: Danmaku) : DanmakuEvent()

    /**
     * 清空屏幕并以这些弹幕填充. 常见于快进/快退时
     *
     * @param list 顺序为由距离当前时间近到远.
     * @param playTimeMillis 当前播放器的时间
     */
    data class Repopulate(val list: List<Danmaku>, val playTimeMillis: Long) : DanmakuEvent()
}

class TimeBasedDanmakuSession private constructor(
    /**
     * 一个[Danmaku] list. 必须根据 [DanmakuInfo.playTime] 排序且创建后不可更改，是一条动漫完整的弹幕列表.
     */
    private val list: List<Danmaku>,
    private val flowCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val tickDelayTimeMs: Long = 400, // 轮询要发送弹幕的间隔，单位[MillisSeconds]毫秒
) : DanmakuSession {
    override val totalCount: Flow<Int?> = flowOf(list.size)

    companion object {
        fun create(
            sequence: Sequence<Danmaku>,
            coroutineContext: CoroutineContext = EmptyCoroutineContext,
        ): DanmakuSession {
            val list = sequence.mapTo(ArrayList()) { sanitize(it) }
            list.sortBy { it.playTimeMillis }
            return TimeBasedDanmakuSession(list, coroutineContext)
        }
    }

    /**
     * 接收一个视频的播放进度[Duration]. 和一个[List<DanmakuRegexFilter>]，根据视频进度和过滤后的弹幕列表，通过call [DanmakuSessionAlgorithm] 的 [tick] 函数发送弹幕
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun at(
        curTimeMillis: () -> Duration,
        isPlayingFlow: Flow<Boolean>
    ): Flow<DanmakuEvent> {
        if (list.isEmpty()) {
            return emptyFlow() // fast path
        }
        val state = DanmakuSessionFlowState(
            list,
            repopulateThreshold = 3.seconds,
            repopulateDistance = { 10.seconds },
        )
        val algorithm = DanmakuSessionAlgorithm(state)
        return isPlayingFlow.flatMapLatest { isPlaying ->
            channelFlow {
                // 一个单独协程收集当前进度
                launch(Dispatchers.Main) {
                    while (isActive && isPlaying) {
                        state.curTimeShared = curTimeMillis()
                        delay(tickDelayTimeMs)
                    }
                }

                val sendItem: (DanmakuEvent) -> Boolean = {
                    trySend(it).isSuccess
                }

                while (isActive && isPlaying) { // 只有当视频播放时才进行轮询发送弹幕
                    algorithm.tick(sendItem)
                    delay(tickDelayTimeMs) // always check for cancellation
                }
            }.flowOn(flowCoroutineContext)
        }
    }

    // 下面的算法有 bug, 而且会创建大量协程影响性能
    /*      var lastTime: Duration = Duration.ZERO
            var lastIndex = -1// last index at which we accessed [list]
            return progress.map { it - shiftMillis.milliseconds }
                .let {
                    if (samplePeriod == Duration.ZERO) it else it.sample(samplePeriod)
                }
                .transformLatest { curTime ->
                    if (curTime < lastTime) {
                        // Went back, reset position to the correct one
                        lastIndex = list.indexOfFirst { it.playTimeMillis >= curTime.inWholeMilliseconds } - 1
                        if (lastIndex == -2) {
                            lastIndex = -1
                        }
                    }

                    lastTime = curTime

                    val curTimeSecs = curTime.inWholeMilliseconds

                    for (i in (lastIndex + 1)..list.lastIndex) {
                        val item = list[i]
                        if (curTimeSecs >= item.playTimeMillis // 达到了弹幕发送的时间
                        ) {
                            if (curTimeSecs - item.playTimeMillis > 3000) {
                                // 只发送三秒以内的, 否则会导致快进之后发送大量过期弹幕
                                continue
                            }
                            lastIndex = i
                            emit(item) // Note: 可能会因为有新的 [curTime] 而 cancel
                        } else {
                            // not yet, 因为 list 是排序的, 这也说明后面的弹幕都还没到时间
                            break
                        }
                    }
                }
                .flowOn(coroutineContext) */
}

internal class DanmakuSessionFlowState(
    var list: List<Danmaku>,
    /**
     * 当前视频播放进度
     */
    @Volatile
    var curTimeShared: Duration = Duration.INFINITE,

    /**
     * 每当快进/快退超过这个阈值后, 重新装填整个屏幕弹幕
     */
    val repopulateThreshold: Duration = 3.seconds,
    /**
     * 重新装填屏幕弹幕时, 从当前时间开始往旧重新装填的距离. 例如当前时间为 15s, repopulateDistance 为 3s, 则会装填 12-15s 的弹幕
     * 需要根据屏幕宽度, 弹幕密度, 以及弹幕速度计算
     */
    val repopulateDistance: () -> Duration,
    /**
     * 重新装填屏幕弹幕时, 最多装填的弹幕数量
     */
    val repopulateMaxCount: Int = 40,
) {
    var lastTime: Duration = Duration.INFINITE

    /**
     * 最后成功发送了的弹幕的索引
     */
    var lastIndex = -1
}


/**
 * 弹幕装填算法的具体实现
 */
internal class DanmakuSessionAlgorithm(val state: DanmakuSessionFlowState) {
    /**
     * 对于每一个时间已经到达的弹幕, 并更新 [DanmakuSessionFlowState.lastIndex]
     */
    private inline fun useEachDanmaku(block: (Danmaku) -> Unit) {
        var i = state.lastIndex + 1
        val list = state.list
        try {
            while (i <= list.lastIndex) {
                block(list[i])
                i++
            }
            // 都发送成功了, 说明我们到了最后
        } finally {
            state.lastIndex = i - 1
        }
    }

    fun tick(sendEvent: (DanmakuEvent) -> Boolean) {
        val curTime = state.curTimeShared
        if (curTime == Duration.INFINITE) {
            return
        }
        val list = state.list

        try {
            if (state.lastTime == Duration.INFINITE // 第一帧
                || (curTime - state.lastTime).absoluteValue >= state.repopulateThreshold
            ) {
                // 移动太远, 重新装填屏幕弹幕
                // 初次播放如果进度不是在 0 也会触发这个
                val targetTime = (curTime - state.repopulateDistance()).inWholeMilliseconds

                // 跳到 repopulateDistance 时间前的一个
                state.lastIndex = list
                    .binarySearchBy(targetTime, selector = { it.playTimeMillis })
                    .let {
                        if (it >= 0) {
                            if (list[it].playTimeMillis < targetTime) {
                                it + 1
                            } else it
                        } else -(it + 1) - 1
                    }
                    .coerceAtLeast(-1)

                // 发送所有在 repopulateDistance 时间内的弹幕
                val curTimeMillis = curTime.inWholeMilliseconds
                val event = DanmakuEvent.Repopulate(
                    buildList seq@{
                        var emitted = 0
                        useEachDanmaku { item ->
                            if (curTimeMillis < item.playTimeMillis) {
                                // 还没有达到弹幕发送时间, 因为 list 是排序的, 这也说明后面的弹幕都还没到时间
                                return@seq
                            }
                            if (emitted >= state.repopulateMaxCount) {
                                return@seq
                            }
                            add(item)
                            emitted++
                        }
                    },
                    curTimeMillis,
                )
                // Send Repopulate Event
                sendEvent(event)
                return
            }
        } finally { // 总是更新
            state.lastTime = curTime
        }

        val curTimeMillis = curTime.inWholeMilliseconds

        useEachDanmaku { item ->
            if (curTimeMillis < item.playTimeMillis) {
                // 还没有达到弹幕发送时间, 因为 list 是排序的, 这也说明后面的弹幕都还没到时间
                return
            }
            if (!sendEvent(DanmakuEvent.Add(item))) { // Send Add Event
                return // 发送失败, 意味着 channel 满了, 即 flow collector 满了, 下一逻辑帧再尝试
            }
        }
    }

}

/**
 * Danmaku Sanitizer
 */
private fun sanitize(danmaku: Danmaku): Danmaku = danmaku.run {
    if (text.indexOf("\n") == -1) return@run this

    copy(
        text = text
            .replace("\n\r", " ")
            .replace("\r\n", " ")
            .replace("\n", " ")
            .trim(),
    )
}
