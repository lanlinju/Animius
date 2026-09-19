package com.lanlinju.animius.presentation.screen.videoplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import com.anime.danmaku.api.DanmakuEvent
import com.anime.danmaku.api.DanmakuPresentation
import com.anime.danmaku.api.DanmakuSession
import com.anime.danmaku.ui.DanmakuHost
import com.anime.danmaku.ui.rememberDanmakuHostState
import com.lanlinju.animius.R
import com.lanlinju.animius.domain.model.Episode
import com.lanlinju.animius.domain.model.Video
import com.lanlinju.animius.presentation.component.Forward85
import com.lanlinju.animius.presentation.component.StateHandler
import com.lanlinju.animius.presentation.screen.settings.DanmakuConfigData
import com.lanlinju.animius.presentation.theme.AnimeTheme
import com.lanlinju.animius.presentation.theme.padding
import com.lanlinju.animius.util.KEY_AUTO_CONTINUE_PLAY_ENABLED
import com.lanlinju.animius.util.KEY_AUTO_ORIENTATION_ENABLED
import com.lanlinju.animius.util.KEY_DANMAKU_CONFIG_DATA
import com.lanlinju.animius.util.isAndroidTV
import com.lanlinju.animius.util.isTabletDevice
import com.lanlinju.animius.util.focus.focusedIconButtonColors
import com.lanlinju.animius.util.focus.focusedOutlinedButtonColors
import com.lanlinju.animius.util.focus.rememberIsFocused
import com.lanlinju.animius.util.isWideScreen
import com.lanlinju.animius.util.openExternalPlayer
import com.lanlinju.animius.util.rememberPreference
import com.lanlinju.videoplayer.AdaptiveTextButton
import com.lanlinju.videoplayer.ResizeMode
import com.lanlinju.videoplayer.VideoPlayer
import com.lanlinju.videoplayer.VideoPlayerControl
import com.lanlinju.videoplayer.VideoPlayerState
import com.lanlinju.videoplayer.prettyVideoTimestamp
import com.lanlinju.videoplayer.component.Slider
import com.lanlinju.videoplayer.rememberVideoPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val Speeds = arrayOf(
    "0.5X" to 0.5f,
    "0.75X" to 0.75f,
    "1.0X" to 1.0f,
    "1.25X" to 1.25f,
    "1.5X" to 1.5f,
    "2.0X" to 2.0f
)

private val Resizes = arrayOf(
    "适应" to ResizeMode.Fit,
    "拉伸" to ResizeMode.Fill,
    "填充" to ResizeMode.Full,
    "16:9" to ResizeMode.FixedRatio_16_9,
    "4:3" to ResizeMode.FixedRatio_4_3,
)

/* 屏幕方向改变会导致丢失状态 */
@Composable
fun VideoPlayScreen(
    viewModel: VideoPlayerViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val animeVideoState by viewModel.videoState.collectAsStateWithLifecycle()
    val danmakuEnabled by viewModel.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSession by viewModel.danmakuSession.collectAsStateWithLifecycle()
    val view = LocalView.current
    val activity = LocalActivity.current ?: LocalActivity.current as Activity
    val isAutoOrientation by rememberPreference(KEY_AUTO_ORIENTATION_ENABLED, true)
    var isAutoContinuePlayEnabled by rememberPreference(KEY_AUTO_CONTINUE_PLAY_ENABLED, false)

    // Handle screen orientation and screen-on state
    ManageScreenState(view, activity)

    StateHandler(
        state = animeVideoState,
        onLoading = { ShowLoadingPage() },
        onFailure = { ShowFailurePage(viewModel, onBackClick) }
    ) { resource ->
        resource.data?.let { video ->

            val playerState = rememberVideoPlayerState(isAutoOrientation = isAutoOrientation)

            val sliderFocusRequester = remember { FocusRequester() }
            val backFocusRequester = remember { FocusRequester() }
            val forwardFocusRequester = remember { FocusRequester() }
            val playPauseFocusRequester = remember { FocusRequester() }
            val episodeFocusRequester = remember { FocusRequester() }
            var pendingFocusTarget by remember { mutableStateOf<FocusTarget?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .adaptiveSize(playerState.isFullscreen.value, view, activity)
                    .focusable()
                    .onKeyEvent { event ->
                        // Back key: immediately hide any visible player UI
                        // Intercepted here because OnBackPressedDispatcher/BackHandler
                        // does not fire reliably on tablet/non-TV Compose for the first press.
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                            when {
                                playerState.isSpeedUiVisible.value -> { playerState.hideSpeedUi(); return@onKeyEvent true }
                                playerState.isResizeUiVisible.value -> { playerState.hideResizeUi(); return@onKeyEvent true }
                                playerState.isEpisodeUiVisible.value -> { playerState.hideEpisodeUi(); return@onKeyEvent true }
                                playerState.isControlUiVisible.value -> { playerState.hideControlUi(); return@onKeyEvent true }
                            }
                        }
                        // 侧栏打开时交给侧栏自己处理按键
                        if (playerState.isEpisodeUiVisible.value ||
                            playerState.isSpeedUiVisible.value ||
                            playerState.isResizeUiVisible.value) return@onKeyEvent false
                        // 控制 UI 可见：交给默认焦点导航（Slider 可聚焦，按钮间正常左右导航）
                        if (playerState.isControlUiVisible.value) return@onKeyEvent false
                        // 隐藏 UI 快捷键
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    playerState.onTimedSeek(-10000)
                                    pendingFocusTarget = FocusTarget.SLIDER
                                    true
                                }
                                Key.DirectionRight -> {
                                    playerState.onTimedSeek(10000)
                                    pendingFocusTarget = FocusTarget.SLIDER
                                    true
                                }
                                else -> false
                            }
                        } else if (event.type == KeyEventType.KeyUp) {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    playerState.showControlUi()
                                    pendingFocusTarget = FocusTarget.FORWARD
                                    true
                                }
                                Key.DirectionDown -> {
                                    playerState.showControlUi()
                                    pendingFocusTarget = FocusTarget.EPISODE
                                    true
                                }
                                Key.DirectionCenter, Key.Spacebar -> {
                                    if (playerState.isPlaying.value) {
                                        playerState.control.pause()
                                        playerState.showControlUi()
                                        pendingFocusTarget = FocusTarget.PLAY_PAUSE
                                    } else {
                                        playerState.control.play()
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                contentAlignment = Alignment.Center
            ) {

                // Video player composable
                VideoPlayer(
                    url = video.url,
                    videoPosition = video.lastPlayPosition,
                    playerState = playerState,
                    headers = video.headers,
                    onBackPress = { handleBackPress(playerState, onBackClick, view, activity) },
                ) {

                    // Deferred focus: wait for AnimatedVisibility to compose before requesting
                    LaunchedEffect(pendingFocusTarget) {
                        pendingFocusTarget?.let { target ->
                            delay(50)
                            runCatching {
                                when (target) {
                                    FocusTarget.FORWARD -> forwardFocusRequester.requestFocus()
                                    FocusTarget.PLAY_PAUSE -> playPauseFocusRequester.requestFocus()
                                    FocusTarget.EPISODE -> episodeFocusRequester.requestFocus()
                                    FocusTarget.SLIDER -> sliderFocusRequester.requestFocus()
                                }
                            }
                            pendingFocusTarget = null
                        }
                    }

                    val videoSize = playerState.videoSize.value
                    val subtitle = if (videoSize.width > 0 && videoSize.height > 0) {
                        val resolution = "${videoSize.width}×${videoSize.height}"
                        val bitrate = playerState.player.currentTracks.groups
                            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                            ?.getTrackFormat(0)?.bitrate
                            ?.takeIf { it > 0 }
                        if (bitrate != null) {
                            "$resolution · Bitrate ${"%.1f".format(bitrate / 1_000_000f)}Mbps"
                        } else {
                            resolution
                        }
                    } else null

                    VideoPlayerControl(
                        state = playerState,
                        title = "${video.title}-${video.episodeName}",
                        subtitle = subtitle,
                        danmakuEnabled = danmakuEnabled,
                        onBackClick = { handleBackPress(playerState, onBackClick, view, activity) },
                        onNextClick = {
                            playerState.control.pause()
                            playerState.setLoading(true)
                            viewModel.playNextEpisode(playerState.player.currentPosition)
                        },
                        optionsContent = {
                            OptionsContent(
                                video = video,
                                isAutoContinuePlayEnabled = isAutoContinuePlayEnabled,
                                onAutoContinuePlayClick = { isAutoContinuePlayEnabled = it },
                                onForwardClick = { playerState.control.skip(85000) },
                                forwardFocusRequester = forwardFocusRequester
                            )
                        },
                        onDanmakuClick = { viewModel.setEnabledDanmaku(it) },
                        modifier = Modifier,
                        sliderFocusRequester = sliderFocusRequester,
                        backFocusRequester = backFocusRequester,
                        forwardFocusRequester = forwardFocusRequester,
                        playPauseFocusRequester = playPauseFocusRequester,
                        episodeFocusRequester = episodeFocusRequester
                    )
                }

                // Default focus on initial load
                LaunchedEffect(Unit) {
                    delay(100)
                    runCatching { playPauseFocusRequester.requestFocus() }
                }

                // Danmaku and additional UI components
                DanmakuHost(playerState, danmakuSession, danmakuEnabled)
                VideoStateMessage(playerState, viewModel, isAutoContinuePlayEnabled)
                VolumeBrightnessIndicator(playerState)
                VideoSideSheet(video, playerState, viewModel)
                RegisterPlaybackStateListener(playerState, viewModel, isAutoContinuePlayEnabled)

                // Save video position on dispose
                DisposableEffect(Unit) {
                    onDispose {
                        viewModel.saveVideoPosition(playerState.player.currentPosition)
                    }
                }
            }
        }
    }
}

// Helper to manage screen state and orientation
@Composable
private fun ManageScreenState(view: View, activity: Activity) {
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        requestLandscapeOrientation(view, activity)
        onDispose {
            view.keepScreenOn = false
            requestPortraitOrientation(view, activity)
        }
    }
}

// Loading screen composable
@Composable
private fun ShowLoadingPage() {
    Box(
        modifier = Modifier
            .background(Color.Black)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

// Failure screen composable
@Composable
private fun ShowFailurePage(viewModel: VideoPlayerViewModel, onBackClick: () -> Unit) {
    val retryFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            tint = Color.White,
            contentDescription = ""
        )
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(id = R.string.txt_empty_result),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        val (backFocused, backModifier) = rememberIsFocused()
        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.then(backModifier),
            colors = focusedOutlinedButtonColors(
                backFocused,
                unfocusedContentColor = Color.White
            )
        ) {
            Text(text = stringResource(id = R.string.back))
        }
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        val (retryFocused, retryModifier) = rememberIsFocused()
        OutlinedButton(
            onClick = { viewModel.retry() },
            modifier = Modifier.focusRequester(retryFocusRequester).then(retryModifier),
            colors = focusedOutlinedButtonColors(retryFocused)
        ) {
            Text(text = stringResource(id = R.string.retry))
        }
    }

    LaunchedEffect(Unit) {
        runCatching { retryFocusRequester.requestFocus() }
    }
}

// Back press handler
private fun handleBackPress(
    playerState: VideoPlayerState,
    onBackClick: () -> Unit,
    view: View,
    activity: Activity
) {
    // 由于在竖屏模式下返回会导致SystemBars的Padding丢失，所以调用hideSystemBars()临时解决
    if (!playerState.isFullscreen.value) {
        hideSystemBars(view, activity)
    }
    onBackClick()
}

@Composable
private fun DanmakuHost(
    playerState: VideoPlayerState,
    session: DanmakuSession?,
    enabled: Boolean
) {
    if (!enabled) return
    val danmakuConfigData by rememberPreference(
        KEY_DANMAKU_CONFIG_DATA,
        DanmakuConfigData(),
        DanmakuConfigData.serializer()
    )
    val danmakuHostState =
        rememberDanmakuHostState(danmakuConfig = danmakuConfigData.toDanmakuConfig())
    if (session != null) {
        DanmakuHost(state = danmakuHostState)
    }

    LaunchedEffect(playerState.isPlaying.value) {
        if (playerState.isPlaying.value) {
            danmakuHostState.play()
        } else {
            danmakuHostState.pause()
        }
    }

    val isPlayingFlow = remember { snapshotFlow { playerState.isPlaying.value } }
    LaunchedEffect(session) {
        danmakuHostState.clearPresentDanmaku()
        session?.at(
            curTimeMillis = { playerState.player.currentPosition.milliseconds },
            isPlayingFlow = isPlayingFlow,
        )?.collect { danmakuEvent ->
            when (danmakuEvent) {
                is DanmakuEvent.Add -> {
                    danmakuHostState.trySend(
                        DanmakuPresentation(
                            danmakuEvent.danmaku,
                            false
                        )
                    )
                }
                // 快进/快退
                is DanmakuEvent.Repopulate -> danmakuHostState.repopulate()
            }
        }
    }
}

private enum class FocusTarget { FORWARD, PLAY_PAUSE, EPISODE, SLIDER }

@Composable
private fun OptionsContent(
    video: Video,
    isAutoContinuePlayEnabled: Boolean,
    onAutoContinuePlayClick: (Boolean) -> Unit,
    onForwardClick: () -> Unit = {},
    forwardFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    var expanded by remember { mutableStateOf(false) }
    val (forwardFocused, forwardModifier) = rememberIsFocused()
    val (moreFocused, moreModifier) = rememberIsFocused()
    Row {
        IconButton(
            onClick = onForwardClick,
            colors = focusedIconButtonColors(forwardFocused, Color.White),
            modifier = forwardModifier
                .focusRequester(forwardFocusRequester)
        ) {
            Icon(
                imageVector = Icons.Rounded.Forward85,
                contentDescription = "Forward 85s",
                tint = if (forwardFocused) MaterialTheme.colorScheme.onPrimary
                else Color.White
            )
        }

        Box {
            IconButton(
                onClick = { expanded = true },
                colors = focusedIconButtonColors(moreFocused, Color.White),
                modifier = moreModifier
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = null,
                    tint = if (moreFocused) MaterialTheme.colorScheme.onPrimary
                    else Color.White
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                val (externalPlayFocused, externalModifier) = rememberIsFocused()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(id = R.string.external_play),
                            color = if (externalPlayFocused) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        openExternalPlayer(video.url)
                    },
                    modifier = externalModifier
                        .then(
                            if (externalPlayFocused) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier
                        )
                )

                val (autoPlayFocused, autoPlayModifier) = rememberIsFocused()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.auto_continue_play),
                            color = when {
                                autoPlayFocused -> MaterialTheme.colorScheme.onPrimary
                                isAutoContinuePlayEnabled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onAutoContinuePlayClick(!isAutoContinuePlayEnabled)
                    },
                    modifier = autoPlayModifier
                        .then(
                            if (autoPlayFocused) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier
                        )
                )
            }
        }
    }

}

@SuppressLint("SourceLockedOrientationActivity")
private fun requestPortraitOrientation(view: View, activity: Activity) {
    showSystemBars(view, activity)

    if (isAndroidTV(activity) || isTabletDevice(activity)) return

    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

private fun requestLandscapeOrientation(view: View, activity: Activity) {
    hideSystemBars(view, activity)

    if (isWideScreen(activity)) return

    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}

private fun Modifier.adaptiveSize(
    fullscreen: Boolean,
    view: View,
    activity: Activity
): Modifier {
    return if (fullscreen) {
        requestLandscapeOrientation(view, activity)
        fillMaxSize()
    } else {
        requestPortraitOrientation(view, activity)
        fillMaxWidth().aspectRatio(1.778f)
    }
}

private fun hideSystemBars(view: View, activity: Activity) {
    val windowInsetsController = WindowCompat.getInsetsController(activity.window, view)
    // Configure the behavior of the hidden system bars
    windowInsetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    // Hide both the status bar and the navigation bar
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
}

private fun showSystemBars(view: View, activity: Activity) {
    val windowInsetsController = WindowCompat.getInsetsController(activity.window, view)
    // Show both the status bar and the navigation bar
    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
}

@Composable
private fun VideoStateMessage(
    playerState: VideoPlayerState,
    viewModel: VideoPlayerViewModel,
    isAutoContinuePlayEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val videoState = viewModel.videoState.collectAsState().value
    val videoLoadError by viewModel.videoLoadError.collectAsState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (playerState.isLoading.value && !playerState.isError.value && !playerState.isSeeking.value && videoLoadError == null) {
            CircularProgressIndicator()
        }

        // 加载失败（切换剧集/线路或播放器错误）：显示错误浮层
        if (videoLoadError != null || playerState.isError.value) {
            ShowVideoMessage(
                stringResource(id = R.string.video_error_msg),
                onRetryClick = {
                    if (videoLoadError != null) viewModel.retryLoad() else playerState.control.retry()
                }
            )
        }

        val hasNext = videoState.data?.let { it.currentEpisodeIndex + 1 < it.episodes.size } == true
        if (playerState.isEnded.value && isAutoContinuePlayEnabled && hasNext && !playerState.isLoading.value) {
            val countdown =
                rememberCountdown(initialTime = 3, onFinished = { playerState.setLoading(true) })
            FloatingMessageIndicator(stringResource(R.string.auto_play_next, countdown))
        }

        if (playerState.isSeeking.value && playerState.isControlUiVisible.value) {
            TimelineIndicator(
                (playerState.videoDurationMs.value * playerState.videoProgress.value).toLong(),
                playerState.videoDurationMs.value
            )
        }

        if (playerState.isLongPress.value) {
            FastForwardIndicator(Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
fun rememberCountdown(
    initialTime: Int = 3,
    onTick: (Int) -> Unit = {},
    onFinished: () -> Unit = {}
): Int {
    var remaining by remember { mutableIntStateOf(initialTime) }

    LaunchedEffect(initialTime) {
        remaining = initialTime
        while (remaining > 0) {
            delay(1000)
            remaining--
            onTick(remaining)
        }
        onFinished()
    }

    return remaining
}

@Composable
fun RegisterPlaybackStateListener(
    playerState: VideoPlayerState,
    viewModel: VideoPlayerViewModel,
    isAutoContinuePlayEnabled: Boolean
) {

    LaunchedEffect(isAutoContinuePlayEnabled) {
        launch {
            snapshotFlow { playerState.isEnded.value }.collect { isEnded ->
                if (isEnded && isAutoContinuePlayEnabled) {
                    viewModel.startAutoContinuePlay(playerState.player.currentPosition)
                }
            }
        }
        launch {
            snapshotFlow { playerState.isSeeking.value }.collect { isSeeking ->
                if (isSeeking && isAutoContinuePlayEnabled) {
                    viewModel.cancelAutoContinuePlay() // 拖动进度条时调用，取消自动连播
                }
            }
        }
    }
}

@Composable
private fun FastForwardIndicator(modifier: Modifier) {
    Box(
        modifier = modifier
            .padding(top = dimensionResource(id = R.dimen.medium_padding))
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = dimensionResource(id = R.dimen.small_padding)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FastForwardAnimation()

            Text(
                text = stringResource(id = R.string.fast_forward_2x),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.offset((-12).dp)
            )
        }

    }
}

@Composable
private fun FastForwardAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "FastForwardAnimation")

    Row(modifier) {
        repeat(3) { index ->
            val color by transition.animateColor(
                initialValue = Color.LightGray.copy(alpha = 0.1f),
                targetValue = Color.LightGray,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 250)
                ),
                label = "color",
            )

            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "",
                modifier = Modifier.offset(-(index * 12).dp),
                tint = color
            )
        }
    }
}

@Composable
private fun VolumeBrightnessIndicator(
    playerState: VideoPlayerState,
    modifier: Modifier = Modifier
) {

    AnimatedVisibility(
        visible = playerState.isChangingBrightness.value || playerState.isChangingVolume.value,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .width(200.dp)
                .aspectRatio(3.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.medium_padding)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.medium_padding))
            ) {
                val isBrightnessVisible = remember { playerState.isChangingBrightness.value }

                if (isBrightnessVisible) {
                    Icon(
                        modifier = Modifier.size(SmallIconButtonSize),
                        painter = painterResource(id = R.drawable.ic_brightness),
                        tint = Color.White,
                        contentDescription = stringResource(id = R.string.brightness)
                    )
                } else {
                    if (playerState.volumeBrightnessProgress.value == 0f) {
                        Icon(
                            modifier = Modifier.size(SmallIconButtonSize),
                            painter = painterResource(id = R.drawable.ic_volume_mute),
                            tint = Color.White,
                            contentDescription = stringResource(id = R.string.brightness)
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(SmallIconButtonSize),
                            painter = painterResource(id = R.drawable.ic_volume_up),
                            tint = Color.White,
                            contentDescription = stringResource(id = R.string.brightness)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { playerState.volumeBrightnessProgress.value },
                    modifier = Modifier
                        .padding(dimensionResource(id = R.dimen.medium_padding))
                        .height(2.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 2.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun TimelineIndicator(
    videoPositionMs: Long,
    videoDurationMs: Long,
    modifier: Modifier = Modifier
) {
    FloatingMessageIndicator(
        text = prettyVideoTimestamp(
            videoPositionMs.milliseconds,
            videoDurationMs.milliseconds
        ),
        modifier = modifier
    )
}

@Composable
fun FloatingMessageIndicator(
    text: String,
    modifier: Modifier = Modifier,
    minWidth: Dp = 120.dp,
    minHeight: Dp = 48.dp,
    backgroundColor: Color = Color.Black.copy(alpha = 0.7f),
    shape: Shape = RoundedCornerShape(8.dp),
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = Color.White,
    contentAlignment: Alignment = Alignment.Center,
    contentPaddingValues: PaddingValues = PaddingValues(MaterialTheme.padding.medium)
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth, minHeight)
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = contentAlignment
    ) {
        Text(
            modifier = Modifier.padding(contentPaddingValues),
            text = text,
            style = textStyle,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun PreviewFloatingMessageIndicator() {
    FloatingMessageIndicator(
        text = "Hello World, Hello World, Hello World"
    )
}

@Composable
private fun ShowVideoMessage(text: String, onRetryClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
        ) {
            Text(
                modifier = Modifier.padding(12.dp),
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // 重试 Button
        onRetryClick?.let {
            val focusRequester = remember { FocusRequester() }
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val isPressed by interactionSource.collectIsPressedAsState()
            val isActive = isFocused || isPressed
            Spacer(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(
                colors = focusedOutlinedButtonColors(
                    isActive,
                    unfocusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .focusRequester(focusRequester),
                interactionSource = interactionSource,
                onClick = it
            ) {
                Text(text = stringResource(id = R.string.retry))
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
private fun VideoSideSheet(
    video: Video,
    playerState: VideoPlayerState,
    viewModel: VideoPlayerViewModel
) {
    var selectedSpeedIndex by remember { mutableIntStateOf(3) }     // 1.0x
    var selectedResizeIndex by remember { mutableIntStateOf(0) }    // 适应

    if (playerState.isSpeedUiVisible.value) {
        SpeedSideSheet(
            selectedSpeedIndex,
            onSpeedClick = { index, (speedText, speed) ->
                selectedSpeedIndex = index
                playerState.setSpeedText(if (index == 3) "倍速" else speedText)
                playerState.control.setPlaybackSpeed(speed)
            }, onDismissRequest = { playerState.hideSpeedUi() }
        )
    }

    if (playerState.isResizeUiVisible.value) {
        ResizeSideSheet(
            selectedResizeIndex = selectedResizeIndex,
            onResizeClick = { index, (resizeText, resizeMode) ->
                selectedResizeIndex = index
                playerState.setResizeText(resizeText)
                playerState.control.setVideoResize(resizeMode)
            }, onDismissRequest = { playerState.hideResizeUi() }
        )
    }

    if (playerState.isEpisodeUiVisible.value) {
        var selectedEpisodeIndex by remember(video.currentEpisodeIndex) { mutableIntStateOf(video.currentEpisodeIndex) }
        EpisodeSideSheet(
            episodes = video.episodes,
            channels = video.channels,
            channelIndex = video.channelIndex,
            playingEpisodeUrl = video.episodeUrl,
            focusIndex = selectedEpisodeIndex,
            onChannelClick = { channelIndex -> viewModel.switchChannel(channelIndex) },
            onEpisodeClick = { index, episode ->
                playerState.control.pause()
                playerState.setLoading(true)
                viewModel.cancelAutoContinuePlay()
                selectedEpisodeIndex = index
                viewModel.getVideo(
                    episode.url,
                    episode.name,
                    index,
                    playerState.player.currentPosition
                )
            },
            onDismissRequest = { playerState.hideEpisodeUi() }
        )
    }
}

@Composable
private fun SpeedSideSheet(
    selectedSpeedIndex: Int,
    onSpeedClick: (Int, Pair<String, Float>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val speeds = remember { Speeds.reversedArray() }
    val focusRequester = remember { FocusRequester() }

    BackHandler { onDismissRequest() }

    SideSheet(onDismissRequest = onDismissRequest, widthRatio = 0.2f) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            speeds.forEachIndexed { index, speed ->
                AdaptiveTextButton(
                    // 不强制固定尺寸：按内容自适应宽度（最小 42dp），避免 "1.25X"/"0.75X" 被截断成 "1.2..."/"0.7..."
                    text = speed.first,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    onClick = { onSpeedClick(index, speed) },
                    color = if (selectedSpeedIndex == index) MaterialTheme.colorScheme.primary else Color.LightGray,
                    fontWeight = if (selectedSpeedIndex == index) FontWeight.Bold else null,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
}

@Composable
private fun ResizeSideSheet(
    selectedResizeIndex: Int,
    onResizeClick: (Int, Pair<String, ResizeMode>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    BackHandler { onDismissRequest() }

    SideSheet(onDismissRequest = onDismissRequest, widthRatio = 0.2f) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Resizes.forEachIndexed { index, resize ->
                AdaptiveTextButton(
                    text = resize.first,
                    modifier = Modifier
                        .size(MediumTextButtonSize)
                        .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    onClick = { onResizeClick(index, resize) },
                    color = if (selectedResizeIndex == index) MaterialTheme.colorScheme.primary else Color.LightGray,
                    fontWeight = if (selectedResizeIndex == index) FontWeight.Bold else null,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
}

// 线路 tab 与集数按钮共用的边框/颜色：active=聚焦或按压，secondary=选中或播放
@Composable
private fun channelBorderStroke(active: Boolean, secondary: Boolean): BorderStroke {
    val primary = MaterialTheme.colorScheme.primary
    return BorderStroke(
        1.0.dp,
        if (active || secondary) primary else MaterialTheme.colorScheme.outline.copy(0.5f)
    )
}

@Composable
private fun channelButtonColors(active: Boolean, secondary: Boolean) =
    ButtonDefaults.outlinedButtonColors(
        containerColor = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = when {
            active -> MaterialTheme.colorScheme.onPrimary
            secondary -> MaterialTheme.colorScheme.primary
            else -> Color.LightGray
        }
    )

@Composable
private fun EpisodeSideSheet(
    episodes: List<Episode>,
    channels: Map<Int, List<Episode>>,
    channelIndex: Int,
    playingEpisodeUrl: String,
    focusIndex: Int,
    onChannelClick: (Int) -> Unit,
    onEpisodeClick: (Int, Episode) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    // 稳定焦点请求器：打开侧栏或切换线路后主动夺焦
    val selectedFocusRequester = remember { FocusRequester() }
    val channelListState = rememberLazyListState()
    // 待聚焦的集数 index：首帧为 focusIndex（打开即聚焦播放集），左/右切线路时设为同 index
    var pendingFocusIndex by remember { mutableStateOf<Int?>(focusIndex) }
    // UP 键从第一集跳转到当前线路 tab 的待聚焦标记（null = 无）
    var pendingTabFocusIndex by remember { mutableStateOf<Int?>(null) }
    // 当前聚焦的集数 index（左/右切线路后保持同 index）
    var focusedEpisodeIndex by remember { mutableIntStateOf(focusIndex) }
    // 当前线路 tab 的焦点请求器（UP 键从集数列表聚焦到它）
    val channelFocusRequester = remember { FocusRequester() }

    // 正在播放的集数在当前线路列表中的 index（不在当前线路则为 -1）
    val playingIndex = episodes.indexOfFirst { it.url == playingEpisodeUrl }
    // 正在播放的线路 index（跨所有线路查找）
    val playingChannelIndex = channels.entries
        .firstOrNull { (_, eps) -> eps.any { it.url == playingEpisodeUrl } }
        ?.key ?: -1
    // 焦点目标：左/右切线路后保持同 index，否则为默认 focusIndex
    val targetFocusIndex = (pendingFocusIndex ?: focusIndex)
        .coerceIn(0, (episodes.size - 1).coerceAtLeast(0))

    SideSheet(onDismissRequest = onDismissRequest, widthRatio = 0.38f) {

        Column(Modifier.fillMaxSize()) {
            if (channels.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    state = channelListState
                ) {
                    items(channels.size) { index ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val isActive = isFocused || isPressed
                        val selected = index == channelIndex
                        val isPlayingChannel = index == playingChannelIndex

                        OutlinedButton(
                            onClick = { onChannelClick(index) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(4.dp),
                            border = channelBorderStroke(isActive, selected),
                            colors = channelButtonColors(isActive, selected),
                            // 焦点在 tab 上时：左右切换 tab 并同步切换线路，焦点保持在 tab 上
                            modifier = Modifier
                                .then(
                                    if (index == channelIndex) {
                                        Modifier.focusRequester(channelFocusRequester)
                                    } else Modifier
                                )
                                .onFocusChanged {
                                    if (it.isFocused && index != channelIndex) onChannelClick(index)
                                },
                            interactionSource = interactionSource
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // 左侧 15dp 空位（播放指示图标位置）
                                Box(
                                    modifier = Modifier
                                        .width(15.dp)
                                        .height(16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (isPlayingChannel) {
                                        EpisodePlaybackIndicator(
                                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                                                   else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.channel_number, index + 1),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                                // 右侧 15dp 空位，与左侧对称
                                Spacer(Modifier.width(15.dp))
                            }
                        }
                    }
                }
            }

            // 切换线路后让选中标签滚动到可见位置
            LaunchedEffect(channelIndex) {
                if (channels.size > 1) {
                    channelListState.animateScrollToItem(channelIndex.coerceAtLeast(0))
                }
            }

            key(channelIndex) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        // 焦点在集数列表时：左/右快速切换线路并聚焦同 index；第一集 UP 聚焦播放线路 tab
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    if (channelIndex > 0) {
                                        pendingFocusIndex = focusedEpisodeIndex
                                        onChannelClick(channelIndex - 1)
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (channelIndex < channels.size - 1) {
                                        pendingFocusIndex = focusedEpisodeIndex
                                        onChannelClick(channelIndex + 1)
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    // 仅在第一集时 UP 聚焦当前显示线路的 tab；否则交给正常纵向导航
                                    if (focusedEpisodeIndex == 0 && channels.size > 1) {
                                        pendingTabFocusIndex = channelIndex
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = rememberLazyListState(targetFocusIndex, -200)
                ) {
                    itemsIndexed(episodes) { index, episode ->
                        val focusRequester = remember { FocusRequester() }
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val isActive = isFocused || isPressed
                        // 播放指示：通过 URL 匹配当前线路中正在播放的集数（切走再切回仍正确）
                        val isPlaying = index == playingIndex
                        val isFocusTarget = index == targetFocusIndex

                        OutlinedButton(
                            onClick = { onEpisodeClick(index, episode) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            shape = RoundedCornerShape(4.dp),
                            border = channelBorderStroke(isActive, isPlaying),
                            colors = channelButtonColors(isActive, isPlaying),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (it.isFocused) focusedEpisodeIndex = index }
                                .then(
                                    if (isFocusTarget) {
                                        Modifier.focusRequester(selectedFocusRequester)
                                    } else Modifier
                                ),
                            interactionSource = interactionSource
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (isPlaying) {
                                    EpisodePlaybackIndicator(
                                        Modifier.align(Alignment.CenterStart),
                                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                                               else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    text = episode.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 打开侧栏聚焦播放中的集数；左/右切线路聚焦同 index；tab 切线路时 pending 已为 null 不夺焦
    LaunchedEffect(channelIndex) {
        delay(200)
        if (pendingFocusIndex != null) {
            runCatching { selectedFocusRequester.requestFocus() }
        }
        pendingFocusIndex = null
    }

    // UP 键从第一集跳转当前线路 tab：先滚动到可见，再延迟夺焦
    LaunchedEffect(pendingTabFocusIndex) {
        if (pendingTabFocusIndex != null) {
            channelListState.animateScrollToItem(channelIndex.coerceAtLeast(0))
            delay(100)
            runCatching { channelFocusRequester.requestFocus() }
            pendingTabFocusIndex = null
        }
    }
}

@Preview
@Composable
fun PreviewEEpisodePlaybackIndicator() {
    EpisodePlaybackIndicator(
        modifier = Modifier
            .background(Color.Black.copy(0.35f))
            .padding(16.dp)
    )
}

@Composable
fun EpisodePlaybackIndicator(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    BouncingBarsAnimation(modifier, tint)
}

@Composable
private fun BouncingBarsAnimation(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "BouncingBars")
    val barWidth = 3.dp
    val maxHeight = 16.dp
    val minHeight = 5.dp
    val barSpacing = 2.dp
    val durationMs = 800
    val startOffset = durationMs / 3

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(barSpacing)
    ) {
        repeat(3) { index ->
            val heightProgress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = durationMs
                        0f at 0
                        1f at (durationMs / 2) //with FastOutSlowInEasing
                        0f at durationMs
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * startOffset)
                ),
                label = "bar_$index"
            )

            DynamicBar(
                progress = heightProgress,
                width = barWidth,
                maxHeight = maxHeight,
                minHeight = minHeight,
                color = tint
            )
        }
    }
}

@Composable
private fun DynamicBar(
    progress: Float,
    width: Dp,
    maxHeight: Dp,
    minHeight: Dp,
    color: Color
) {
    val density = LocalDensity.current
    val animatedHeight = lerp(minHeight, maxHeight, progress)

    Canvas(
        modifier = Modifier
            .width(width)
            .height(maxHeight)
    ) {
        val barHeight = with(density) { animatedHeight.toPx() }
        val cornerRadius = size.width / 2

        drawRoundRect(
            color = color,
            topLeft = Offset(0f, size.height - barHeight),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
    }
}


// https://googlesamples.github.io/android-custom-lint-rules/checks/UnusedBoxWithConstraintsScope.md.html
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun SideSheet(
    onDismissRequest: () -> Unit,
    widthRatio: Float = 0.4f,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val fullWidth = constraints.maxWidth
        val sideSheetWidthDp = maxWidth * widthRatio

        val visibleState = remember {
            MutableTransitionState(false).apply {
                // Start the animation immediately.
                targetState = true
            }
        }

        val scope = rememberCoroutineScope()

        /**
         *  state.isIdle && state.currentState -> "Visible"
         *  !state.isIdle && state.currentState -> "Disappearing"
         *  state.isIdle && !state.currentState -> "Invisible"
         *  else -> "Appearing"
         */
        val dismissRequestHandler: () -> Unit = {
            visibleState.targetState = false
            scope.launch {
                while (!(visibleState.isIdle && !visibleState.currentState)) {
                    delay(100)
                }
            }.invokeOnCompletion { onDismissRequest() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { position ->
                        if (position.x < fullWidth - sideSheetWidthDp.toPx()) {
                            dismissRequestHandler()
                        }
                    })
                }) {
            AnimatedVisibility(
                visibleState = visibleState,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                Column(
                    modifier = Modifier
                        .width(sideSheetWidthDp)
                        .fillMaxHeight()
                        .background(color = Color.Black.copy(alpha = 0.85f))
                        .padding(8.dp)
                ) {
                    content()
                }
            }
        }

        BackHandler {
            dismissRequestHandler()
        }
    }
}

private val MediumTextButtonSize = 42.dp
private val SmallIconButtonSize = 32.dp

@Preview(device = Devices.TV_720p)
@Composable
fun SideSheetPreview() {
    AnimeTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            var isSideSheetVisible by remember { mutableStateOf(false) }

            Button(onClick = { isSideSheetVisible = !isSideSheetVisible }) {
                Text(text = "Open")
            }

            if (isSideSheetVisible) {
                SideSheet(onDismissRequest = { isSideSheetVisible = false }) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.small_padding)),
                    ) {
                        items(150) { num ->
                            val isSelected = num % 2 == 0
                            OutlinedButton(
                                onClick = { },
                                contentPadding = PaddingValues(8.dp),
                                border = if (isSelected) BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary
                                ) else ButtonDefaults.outlinedButtonBorder()
                            ) {
                                Text(
                                    text = "第2${num}集",
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
