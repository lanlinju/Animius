package com.lanlinju.videoplayer

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.OrientationEventListener
import android.view.Window
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(UnstableApi::class)
@Composable
fun rememberVideoPlayerState(
    hideControllerAfterMs: Long = 6000,
    videoPositionPollInterval: Long = 500,
    isAutoOrientation: Boolean = true,
    context: Context = LocalContext.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    config: ExoPlayer.Builder.() -> Unit = {
        setLoadControl(loadControlCreator())
        setSeekForwardIncrementMs(15 * 1000)
        setSeekBackIncrementMs(15 * 1000)
    }
): VideoPlayerState = remember {
    VideoPlayerStateImpl(
        player = ExoPlayer.Builder(context).apply(config).build(),
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
        window = (context as Activity).window,
        context = context,
        coroutineScope = coroutineScope,
        isAutoOrientation = isAutoOrientation,
        hideControllerAfterMs = hideControllerAfterMs,
        videoPositionPollInterval = videoPositionPollInterval
    ).also {
        it.player.addListener(it)
    }
}

class VideoPlayerStateImpl(
    override val player: ExoPlayer,
    override val audioManager: AudioManager,
    override val window: Window,
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val isAutoOrientation: Boolean,
    private val hideControllerAfterMs: Long,
    private val videoPositionPollInterval: Long,
) : VideoPlayerState, Player.Listener {
    override val videoSize = mutableStateOf(player.videoSize)
    override val videoResizeMode = mutableStateOf(ResizeMode.Fit)
    override val videoPositionMs = mutableStateOf(0L)
    override val videoDurationMs = mutableStateOf(0L)
    override val speed = mutableStateOf(1f)

    override val isFullscreen = mutableStateOf(true)
    override val isPlaying = mutableStateOf(player.isPlaying)
    override val isLoading = mutableStateOf(true)
    override val isEnded = mutableStateOf(false)
    override val isError = mutableStateOf(false)
    override val playbackState = mutableStateOf(player.playbackState)

    override val isSeeking = mutableStateOf(false)
    override val isLongPress = mutableStateOf(false)
    override val isChangingVolume = mutableStateOf(false)
    override val isChangingBrightness = mutableStateOf(false)

    override val videoProgress = mutableStateOf(0F)
    override val videoBufferedProgress = mutableStateOf(0F)
    override val volumeBrightnessProgress = mutableStateOf(0F)

    override val speedText = mutableStateOf("倍速")
    override val resizeText = mutableStateOf("适应")

    override val isOptionsUiVisible = mutableStateOf(false)
    override val isControlUiVisible = mutableStateOf(false)
    override val isSpeedUiVisible = mutableStateOf(false)
    override val isResizeUiVisible = mutableStateOf(false)
    override val isEpisodeUiVisible = mutableStateOf(false)

    /**
     * 当拖动Slider或屏幕时，更新Slider进度条位置
     */
    override val onSeeking: (Float) -> Unit
        get() = {
            controlUiLastInteractionMs = 0
            isSeeking.value = true
            isEnded.value = false
            if (!isControlUiVisible.value) showControlUi()
            this.videoProgress.value = it
        }

    /**
     * 当拖动Slider或屏幕结束时，更新视频播放位置
     */
    override val onSeeked: () -> Unit
        get() = {
            isSeeking.value = false
            player.seekTo((player.duration * videoProgress.value).toLong())
        }

    /**
     * 当点Slider时，更改进度条的值和视频播放进度
     */
    override val onClickSlider: (progress: Float) -> Unit
        get() = { progress ->
            controlUiLastInteractionMs = 0
            isEnded.value = false
            this.videoProgress.value = progress
            player.seekTo((player.duration * videoProgress.value).toLong())
        }

    override fun onChangeVolume(value: Float) {
        isChangingVolume.value = true
        volumeBrightnessProgress.value = value
    }

    override fun onChangeBrightness(value: Float) {
        isChangingBrightness.value = true
        volumeBrightnessProgress.value = value
    }

    override fun onChanged() {
        isChangingVolume.value = false
        isChangingBrightness.value = false
    }

    override fun onLongPress() {
        isLongPress.value = true
        val currentSpeed = player.playbackParameters.speed
        control.setPlaybackSpeed(currentSpeed * 2)
    }

    override fun onDisLongPress() {
        isLongPress.value = false
        val currentSpeed = player.playbackParameters.speed
        control.setPlaybackSpeed(currentSpeed.div(2))
    }

    override val control = object : VideoPlayerControl {
        override fun play() {
            controlUiLastInteractionMs = 0
            player.play()
        }

        override fun pause() {
            controlUiLastInteractionMs = 0
            player.pause()
        }

        override fun forward() {
            controlUiLastInteractionMs = 0
            player.seekForward()
        }

        override fun rewind() {
            controlUiLastInteractionMs = 0
            player.seekBack()
        }

        override fun skip(skipMs: Long) {
            controlUiLastInteractionMs = 0
            if (videoDurationMs.value == 0L) return
            val positionMs = (player.currentPosition + skipMs).coerceIn(0, videoDurationMs.value)
            player.seekTo(positionMs)
        }

        override fun retry() {
            isError.value = false
            isLoading.value = true
            player.prepare()
        }

        override fun setFullscreen(value: Boolean) {
            controlUiLastInteractionMs = 0
            isFullscreen.value = value
        }

        override fun setVideoResize(mode: ResizeMode) {
            controlUiLastInteractionMs = 0
            videoResizeMode.value = mode
        }

        override fun setPlaybackSpeed(speed: Float) {
            this@VideoPlayerStateImpl.speed.value = speed
            player.setPlaybackSpeed(speed)
        }
    }

    override fun setLoading(loading: Boolean) {
        isLoading.value = loading
    }

    private var pollVideoPositionJob: Job? = null
    private var controlUiLastInteractionMs = 0L

    override fun hideControlUi() {
        controlUiLastInteractionMs = 0
        isControlUiVisible.value = false
        pollVideoPositionJob?.cancel()
        pollVideoPositionJob = null
    }

    override fun showControlUi() {
        controlUiLastInteractionMs = 0
        isControlUiVisible.value = true
        pollVideoPositionJob?.cancel()
        pollVideoPositionJob = coroutineScope.launch {
            while (true) {
                if (videoDurationMs.value > 0) {
                    videoPositionMs.value = player.currentPosition
                    if (!isSeeking.value) {
                        // 播放进度
                        videoProgress.value =
                            videoPositionMs.value / videoDurationMs.value.toFloat()
                        // 缓冲进度
                        videoBufferedProgress.value =
                            player.bufferedPosition / videoDurationMs.value.toFloat()
                    }
                }
                controlUiLastInteractionMs += videoPositionPollInterval

                delay(videoPositionPollInterval)
                if (controlUiLastInteractionMs >= hideControllerAfterMs) {
                    hideControlUi()
                    break
                }
            }
        }
    }

    override fun setSpeedText(text: String) {
        speedText.value = text
    }

    override fun setResizeText(text: String) {
        resizeText.value = text
    }

    override fun hideOptionsUi() {
        isOptionsUiVisible.value = true
    }

    override fun showOptionsUi() {
        isOptionsUiVisible.value = false
    }

    override fun showSpeedUi() {
        hideControlUi()
        isSpeedUiVisible.value = true
    }

    override fun hideSpeedUi() {
        isSpeedUiVisible.value = false
    }

    override fun showResizeUi() {
        hideControlUi()
        isResizeUiVisible.value = true
    }

    override fun hideResizeUi() {
        isResizeUiVisible.value = false
    }

    override fun showEpisodeUi() {
        hideControlUi()
        isEpisodeUiVisible.value = true
    }

    override fun hideEpisodeUi() {
        isEpisodeUiVisible.value = false
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying.value = isPlaying
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) videoDurationMs.value = player.duration
        this.playbackState.value = playbackState
        when (playbackState) {
            Player.STATE_IDLE -> Unit
            Player.STATE_BUFFERING -> isLoading.value = true
            Player.STATE_READY -> isLoading.value = false
            Player.STATE_ENDED -> isEnded.value = true
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                control.play() // 重新获得焦点，恢复播放
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                control.pause() // Permanent loss of audio focus，Pause playback immediately
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                control.pause() // 暂时失去音频焦点，暂停播放
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 暂时失去音频焦点，但可以继续播放，不过需要降低音量(系统默认降低音量)
            }
        }
    }

    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).run {
            setAudioAttributes(AudioAttributes.Builder().run {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                build()
            })
            setAcceptsDelayedFocusGain(true)
            setOnAudioFocusChangeListener(audioFocusChangeListener)
            build()
        }

    private val orientationEventListener = object : OrientationEventListener(context) {
        private var currentOrientation = 270
        private val activity = context as Activity
        override fun onOrientationChanged(orientation: Int) {
            if (!isFullscreen.value || orientation == ORIENTATION_UNKNOWN) {
                return
            }

            if (orientation in 261..279) {
                if (currentOrientation == 270) return
                currentOrientation = 270

                coroutineScope.launch {
                    delay(300)
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

            } else if (orientation in 81..99) {
                if (currentOrientation == 90) return
                currentOrientation = 90

                coroutineScope.launch {
                    delay(300)
                    activity.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            }
        }

    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        this.videoSize.value = videoSize
    }

    override fun onPlayerError(error: PlaybackException) {
        isError.value = true
    }

    override fun onRenderedFirstFrame() {
        isError.value = false
        isEnded.value = false
    }

    override fun registerListener() {
        if (isAutoOrientation) {
            orientationEventListener.enable()
        }
        audioManager.requestAudioFocus(focusRequest)
    }

    override fun unregisterListener() {
        if (isAutoOrientation) {
            orientationEventListener.disable()
        }
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

}


interface VideoPlayerState {
    val player: ExoPlayer
    val audioManager: AudioManager
    val window: Window

    val videoSize: State<VideoSize>
    val videoResizeMode: State<ResizeMode>
    val videoPositionMs: State<Long>    /*当控制组件显示时才会更新这个值，获取视频当前进度用player.currentPosition*/
    val videoDurationMs: State<Long>    /*视频时长*/
    val speed: State<Float>

    val isFullscreen: State<Boolean>
    val isPlaying: State<Boolean>
    val isLoading: State<Boolean>
    val isEnded: State<Boolean>
    val isError: State<Boolean>
    val playbackState: State<Int>

    val isSeeking: State<Boolean>
    val isLongPress: State<Boolean>
    val isChangingVolume: State<Boolean>
    val isChangingBrightness: State<Boolean>
    val videoProgress: State<Float> /*进度条百分比 0f - 1f*/
    val videoBufferedProgress: State<Float>
    val volumeBrightnessProgress: State<Float>

    val onSeeking: (dragProcess: Float) -> Unit     // 当拖动进度条时调用
    val onSeeked: () -> Unit                        // 当拖动进度条结束时调用
    val onClickSlider: (progress: Float) -> Unit // 当点击进度条时调用

    val speedText: State<String>
    val resizeText: State<String>

    val isOptionsUiVisible: State<Boolean>
    val isControlUiVisible: State<Boolean>
    val isSpeedUiVisible: State<Boolean>
    val isResizeUiVisible: State<Boolean>
    val isEpisodeUiVisible: State<Boolean>
    val control: VideoPlayerControl

    fun setLoading(loading: Boolean)

    fun onChangeVolume(value: Float)
    fun onChangeBrightness(value: Float)
    fun onChanged()

    fun onLongPress()
    fun onDisLongPress()

    fun registerListener()
    fun unregisterListener()

    fun setSpeedText(text: String)
    fun setResizeText(text: String)

    fun hideOptionsUi()
    fun showOptionsUi()

    fun hideControlUi()
    fun showControlUi()

    fun showSpeedUi()
    fun hideSpeedUi()

    fun showResizeUi()
    fun hideResizeUi()

    fun showEpisodeUi()
    fun hideEpisodeUi()
}

interface VideoPlayerControl {
    fun play()
    fun pause()

    fun forward()
    fun rewind()
    fun skip(skipMs: Long)

    fun retry()

    fun setFullscreen(value: Boolean)
    fun setVideoResize(mode: ResizeMode)
    fun setPlaybackSpeed(speed: Float)
}