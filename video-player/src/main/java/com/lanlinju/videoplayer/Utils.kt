package com.lanlinju.videoplayer

import android.net.Uri
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.ui.unit.Constraints
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


internal fun VideoSize.aspectRatio(): Float =
    if (height == 0 || width == 0) 0f else (width * pixelWidthHeightRatio) / height

/**
 * The [FrameLayout] will not resize itself if the fractional difference between its natural
 * aspect ratio and the requested aspect ratio falls below this threshold.
 *
 *
 * This tolerance allows the view to occupy the whole of the screen when the requested aspect
 * ratio is very close, but not exactly equal to, the aspect ratio of the screen. This may reduce
 * the number of view layers that need to be composited by the underlying system, which can help
 * to reduce power consumption.
 */
private const val MAX_ASPECT_RATIO_DIFFERENCE_FRACTION = 0.01f
private const val VIDEO_ASPECT_RATIO_16_9 = 16f.div(9f) // 16 : 9, 1.7777778
private const val VIDEO_ASPECT_RATIO_4_3 = 4f.div(3f)  //   4 : 3, 1.3333334

internal fun Constraints.resizeForVideo(
    mode: ResizeMode,
    aspectRatio: Float
): Constraints {
    if (aspectRatio <= 0f) {
        // 设置默认视频显示大小为：横屏模式下宽高比为16:9的大小
        val width = (maxHeight * VIDEO_ASPECT_RATIO_16_9).toInt() // default 16 : 9
        return this.copy(maxWidth = width)
    }

    var width = maxWidth
    var height = maxHeight
    val constraintAspectRatio: Float = (width / height).toFloat()
    val difference = aspectRatio / constraintAspectRatio - 1

    if (kotlin.math.abs(difference) <= MAX_ASPECT_RATIO_DIFFERENCE_FRACTION) {
        // 视频比例与屏幕比例十分接近，不处理
        return this
    }

    when (mode) {
        ResizeMode.Fit -> {
            if (difference > 0) { /* difference 大于零 为竖屏模式 */
                height = (width / aspectRatio).toInt()
            } else { /* 横屏模式 */
                width = (height * aspectRatio).toInt()
            }
        }

        ResizeMode.Zoom -> {
            if (difference > 0) {
                width = (height * aspectRatio).toInt()
            } else {
                height = (width / aspectRatio).toInt()
            }
        }

        ResizeMode.FixedWidth -> {
            height = (width / aspectRatio).toInt()
        }

        ResizeMode.FixedHeight -> {
            width = (height * aspectRatio).toInt()
        }

        ResizeMode.FixedRatio_16_9 -> {
            if (difference > 0) {
                height = (width / VIDEO_ASPECT_RATIO_16_9).toInt()
            } else {
                width = (height * VIDEO_ASPECT_RATIO_16_9).toInt()
            }
        }

        ResizeMode.FixedRatio_4_3 -> {
            if (difference > 0) {
                height = (width / VIDEO_ASPECT_RATIO_4_3).toInt()
            } else {
                width = (height * VIDEO_ASPECT_RATIO_4_3).toInt()
            }
        }

        ResizeMode.Full -> {
            if (difference > 0) {
                width = (height * aspectRatio).toInt()
            } else {
                height = (width / aspectRatio).toInt()
            }
        }

        ResizeMode.Fill -> Unit
    }

    return this.copy(maxWidth = width, maxHeight = height)
}


/**
 * Will return a timestamp denoting the current video [position] and the [duration] in the following
 * format "mm:ss / mm:ss"
 * **/
fun prettyVideoTimestamp(
    position: Duration,
    duration: Duration
): String = buildString {
    appendMinutesAndSeconds(position)
    append("/")
    appendMinutesAndSeconds(duration)
}

/**
 * Will split [duration] in minutes and seconds and append it to [this] in the following format "mm:ss"
 * */
private fun StringBuilder.appendMinutesAndSeconds(duration: Duration) {
    val minutes = duration.inWholeMinutes
    val seconds = (duration - minutes.minutes).inWholeSeconds
    appendDoubleDigit(minutes)
    append(':')
    appendDoubleDigit(seconds)
}

/**
 * Will append [value] as double digit to [this].
 * If a single digit value is passed, ex: 4 then a 0 will be added as prefix resulting in 04
 * */
private fun StringBuilder.appendDoubleDigit(value: Long) {
    if (value < 10) {
        append(0)
        append(value)
    } else {
        append(value)
    }
}

internal fun isHlsUrl(url: String): Boolean = url.contains(".m3u8")

internal fun mediaItemCreator(url: String): MediaItem {
    if (url.contains("/storage/emulated")) { // 本地视频文件处理
        return MediaItem.fromUri(Uri.fromFile(File(url)))
    }
    val builder = MediaItem.Builder().setUri(url) // 远程视频文件类型处理
    if (isHlsUrl(url)) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }
    return builder.build()
}

@OptIn(UnstableApi::class)
internal fun mediaSourceCreator(
    url: String,
    headers: Map<String, String>
): MediaSource {
    val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(headers)
    // m3u8 必须用 HlsMediaSource:ProgressiveMediaSource 依赖 media3-extractor,
    // 而其中没有任何 HLS 提取器,会当作容器解析并抛 UnrecognizedInputFormatException
    // (mediaItemCreator 设置的 mimeType 在这里无效,setMediaSource 已绕过默认工厂)。
    val videoSource: MediaSource = if (isHlsUrl(url)) {
        HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItemCreator(url))
    } else {
        ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItemCreator(url))
    }
    return videoSource
}

/** 本地视频(文件路径/内容 URI)必须走默认工厂,DefaultHttpDataSource 打不开它们 */
@OptIn(UnstableApi::class)
internal fun ExoPlayer.setVideoUrl(url: String, headers: Map<String, String>) {
    if (url.startsWith("http")) {
        setMediaSource(mediaSourceCreator(url, headers))
    } else {
        setMediaItem(mediaItemCreator(url))
    }
}

@OptIn(UnstableApi::class)
internal fun loadControlCreator(): LoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(50_000, 90_000, 2000, 5000)
        .setBackBuffer(20_000, true)
        .build()
}

/**
 * 视频文件大小:本地文件读文件长度,远程文件用 HEAD 请求取 Content-Length。
 * HLS 是分片流没有单一文件大小,服务器不支持 HEAD 或不返回长度时同样返回 null。
 *
 * 这里单独建一个 DataSource,是有意为之:
 * - media3 的 DataSource 实例本身不可共享(非线程安全);能共享的只有连接池。
 *   播放侧用的 DefaultHttpDataSource 基于 HttpURLConnection,其 keep-alive 池是 JVM 级的,
 *   所以再多一个实例并不会各起一套连接。
 * - 只有换成 OkHttpDataSource 才能显式共享池,但那会改变超时/重定向/请求头行为,
 *   为一个 HEAD 请求不值得冒这个险。
 *
 * HEAD 失败一律吞成 null 同样是刻意的:大小只是展示项,取不到时 UI 显示"大小未知",
 * 不应因此影响播放。
 */
@OptIn(UnstableApi::class)
internal fun probeMediaSize(url: String, headers: Map<String, String>): Long? {
    if (isHlsUrl(url)) return null
    return runCatching {
        if (!url.startsWith("http")) {
            return@runCatching File(Uri.parse(url).path ?: url).length().takeIf { it > 0 }
        }
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .createDataSource()
        try {
            // 长度已在 open() 里由 Content-Length/Content-Range 算出,无需自己解析响应头
            dataSource.open(
                DataSpec.Builder()
                    .setUri(url)
                    .setHttpMethod(DataSpec.HTTP_METHOD_HEAD)
                    .build()
            ).takeIf { it > 0 }
        } finally {
            dataSource.close()
        }
    }.getOrNull()
}

/**
 * 分片级码率 = 分片字节数 × 8 ÷ 分片媒体时长,取最近 [SEGMENT_WINDOW_MS] 毫秒媒体的加权平均。
 *
 * 分片自带媒体时长,所以缓冲预取和拖动进度条都不会影响它,这是 HLS 唯一可信的码率来源:
 * 清单里的 BANDWIDTH 是峰值上界估计,实测平均值反超它也是常态,而按下载字节算平均量到的是网速不是编码码率。
 *
 * 渐进式(MP4)不适用:它的事件跨度是「加载起点 → 文件末尾」而不是分片时长,所以只对 HLS 启用。
 */
@OptIn(UnstableApi::class)
internal class SegmentBitrateMeter : AnalyticsListener {
    /** 由播放源决定:HLS 置 true,其他源保持 false,免得白算 */
    @Volatile
    var isEnabled: Boolean = false

    /**
     * samples / latestBitrateBps 会被两个线程碰:onLoadCompleted 在播放线程,
     * reset() 在主线程(换集、换线路、切换统计来源都会调)。ArrayDeque 不是线程安全的,
     * 并发 clear/addFirst/removeLast 会算错窗口甚至抛异常,所以统一用一把锁护住。
     */
    private val lock = Any()

    private var latestBitrateBps: Long? = null

    /** 已统计分片的码率,还没有分片时为 null */
    val bitrateBps: Long? get() = synchronized(lock) { latestBitrateBps }

    /** 分片(字节数, 媒体时长毫秒),最新的排在最前 */
    private val samples = ArrayDeque<Pair<Long, Long>>()

    fun reset() {
        synchronized(lock) {
            samples.clear()
            latestBitrateBps = null
        }
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        if (!isEnabled) return
        // 清单、初始化段、音轨的事件都不能代表视频分片码率
        if (mediaLoadData.dataType != C.DATA_TYPE_MEDIA) return
        if (mediaLoadData.trackType !in SEGMENT_TRACK_TYPES) return
        // 没有媒体时长的事件(C.TIME_UNSET)会算出巨大负数跨度,用区间一并滤掉
        val durationMs = mediaLoadData.mediaEndTimeMs - mediaLoadData.mediaStartTimeMs
        if (durationMs !in MIN_SEGMENT_DURATION_MS..MAX_SEGMENT_DURATION_MS) return
        val bytes = loadEventInfo.bytesLoaded
        if (bytes <= 0) return

        // 上面的过滤只读事件自带的不可变数据,可以放在锁外;下面动 samples 才需要互斥
        synchronized(lock) {
            samples.addFirst(bytes to durationMs)

            // 分片时长在 1~10 秒之间乱跳,所以按媒体时长取窗口,统计跨度才不会忽长忽短
            var totalBytes = 0L
            var totalDurationMs = 0L
            var windowSize = 0
            for ((sampleBytes, sampleDurationMs) in samples) {
                if (windowSize > 0 && totalDurationMs >= SEGMENT_WINDOW_MS) break
                totalBytes += sampleBytes
                totalDurationMs += sampleDurationMs
                windowSize++
            }
            while (samples.size > windowSize) samples.removeLast()

            latestBitrateBps = totalBytes * 8000 / totalDurationMs
        }
    }
}

/** 平滑窗口:最近 30 秒媒体。实测单片码率在 0.6~8 Mbps 之间跳,窗口太短会跟着分片抖 */
private const val SEGMENT_WINDOW_MS = 30_000L
private const val MIN_SEGMENT_DURATION_MS = 1_000L
private const val MAX_SEGMENT_DURATION_MS = 600_000L

/**
 * 能代表视频分片的轨道类型。HLS 的复用 `.ts` 分片报的是 DEFAULT,只有分离轨道的流才报 VIDEO,
 * 而这类分片的事件里 trackFormat 是空的(mime 为 null、宽高 -1),没法从格式反推,只能看类型。
 */
private val SEGMENT_TRACK_TYPES = setOf(
    C.TRACK_TYPE_VIDEO,
    C.TRACK_TYPE_DEFAULT,
    C.TRACK_TYPE_UNKNOWN
)
