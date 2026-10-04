package com.lanlinju.animius.parse

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanlinju.animius.data.remote.parse.AnimeSource
import com.lanlinju.animius.util.SourceHolder
import com.lanlinju.animius.util.SourceMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 全源全线路播放烟雾测试:每个源 → 搜索 → 详情 → 逐线路播放首集。
 *
 * 播放阶段完全复刻 video-player 的 mediaSourceCreator 逻辑
 * (m3u8 → HlsMediaSource,其余 → ProgressiveMediaSource,共用 headers 的 DataSource),
 * 因此能同时暴露"线路解析失败"与"地址拿到但播放失败"。
 *
 * 依赖真实站点网络与设备播放器,不在 CI 范围内(CI 仅执行 assembleDebug/Release)。
 * 执行:
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.lanlinju.animius.parse.AllSourcesPlaybackTest
 * ```
 * 结果通过 logcat 汇总:`adb logcat -s SRC-PLAY:I`
 */
@RunWith(AndroidJUnit4::class)
class AllSourcesPlaybackTest {

    private val tag = "SRC-PLAY"

    /** 多线路番剧,6 个源基本都能搜到 */
    private val keyword = "史莱姆"

    /** 各源的搜索词:Gogoanime 是英文站,需要用英文关键词 */
    private val keywordOf = mapOf(
        SourceMode.Gogoanime to "dragon ball daima"
    )

    private val sources = SourceMode.entries

    @Test
    fun allSourcesAllChannels() = runBlocking<Unit> {
        val results = mutableListOf<String>()

        for (mode in sources) {
            val source = SourceHolder.getSource(mode)
            Log.i(tag, "════════ ${mode.name} ════════")
            results += runCatching { probeSource(mode, source) }.getOrElse {
                "  ${mode.name}: 整体异常 ${it.javaClass.simpleName}: ${it.message}".also { s ->
                    Log.i(tag, s)
                }
            }
        }

        Log.i(tag, "════════ 汇总 ════════")
        results.forEach { Log.i(tag, it) }
    }

    private suspend fun probeSource(mode: SourceMode, source: AnimeSource): String {
        val query = keywordOf[mode] ?: keyword
        // 搜索
        val search = runCatching {
            withTimeout(30_000L) { source.getSearchData(query, 1) }
        }
        val list = search.getOrNull().orEmpty()
        if (list.isEmpty()) {
            val s = "${mode.name}: 搜索无结果 | ${search.exceptionOrNull()?.message}"
            Log.i(tag, s)
            return s
        }
        Log.i(tag, "搜索命中 ${list.size} 条,取 [${list.first().title}] ${list.first().url}")

        // 详情
        val detail = runCatching {
            withTimeout(40_000L) { source.getAnimeDetail(list.first().url) }
        }
        val d = detail.getOrNull()
        if (d == null) {
            val s = "${mode.name}: 详情失败 | ${detail.exceptionOrNull()?.javaClass?.simpleName}: ${detail.exceptionOrNull()?.message}"
            Log.i(tag, s)
            return s
        }
        // 部分源(如 Gogoanime)用旧的 episodes 字段而非 channels,统一成线路表
        val channels = if (d.channels.isNotEmpty()) d.channels
        else if (d.episodes.isNotEmpty()) mapOf(0 to d.episodes)
        else emptyMap()
        Log.i(tag, "详情 [${d.title}] 线路数=${channels.size}")
        if (channels.isEmpty()) {
            val s = "${mode.name}: 详情无线路(解析选择器可能失效)"
            Log.i(tag, s)
            return s
        }

        // 逐线路播放首集
        val lines = mutableListOf<String>()
        channels.forEach { (index, eps) ->
            val ep = eps.firstOrNull()
            if (ep == null) {
                lines += "    线路$index: 空剧集列表"
                Log.i(tag, "    线路$index: 空剧集列表")
                return@forEach
            }
            val r = runCatching {
                withTimeout(45_000L) { source.getVideoData(ep.url) }
            }
            val v = r.getOrNull()
            if (v == null) {
                val why = "解析失败 ${r.exceptionOrNull()?.javaClass?.simpleName}: ${r.exceptionOrNull()?.message}"
                lines += "    线路$index(${eps.size}集): $why"
                Log.i(tag, "    线路$index(${eps.size}集): $why")
            } else {
                val play = probePlayback(v.videoUrl, v.headers)
                lines += "    线路$index(${eps.size}集): $play"
                // 打印完整地址:很多 CDN 是无扩展名地址(如 /video/tos/),
                // 只截断会看不出该走 HlsMediaSource 还是 ProgressiveMediaSource
                Log.i(tag, "    线路$index(${eps.size}集): [$ep.name] ${v.videoUrl}")
                Log.i(tag, "        headers=${v.headers.keys} -> $play")
            }
        }
        return "${mode.name} [${d.title}] 线路数=${channels.size}\n" + lines.joinToString("\n")
    }

    /**
     * 复刻 video-player 的 mediaSourceCreator:headers 的 DataSource + 按 m3u8 分派。
     */
    @OptIn(UnstableApi::class)
    private fun probePlayback(url: String, headers: Map<String, String>, timeoutSec: Long = 25): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val latch = CountDownLatch(1)
        var outcome = "TIMEOUT(${timeoutSec}s)"
        var playerRef: ExoPlayer? = null

        Handler(Looper.getMainLooper()).post {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
            val mediaSource: MediaSource =
                if (url.contains(".m3u8")) {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))
                } else {
                    ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))
                }
            val player = ExoPlayer.Builder(context).build()
            playerRef = player
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        outcome = "PLAY-OK"
                        latch.countDown()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    outcome = "PLAY-FAIL ${error.errorCodeName}: " +
                        "${error.cause?.javaClass?.simpleName}: ${error.cause?.message}"
                    latch.countDown()
                }
            })
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        }

        latch.await(timeoutSec, TimeUnit.SECONDS)
        // 在主线程释放播放器,避免多个实例堆积
        val release = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            playerRef?.release()
            release.countDown()
        }
        release.await(5, TimeUnit.SECONDS)
        return outcome
    }
}
