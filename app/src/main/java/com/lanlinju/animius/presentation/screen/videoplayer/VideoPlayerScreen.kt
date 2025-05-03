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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.lanlinju.animius.util.isWideScreen
import com.lanlinju.animius.util.openExternalPlayer
import com.lanlinju.animius.util.rememberPreference
import com.lanlinju.videoplayer.AdaptiveTextButton
import com.lanlinju.videoplayer.ResizeMode
import com.lanlinju.videoplayer.VideoPlayer
import com.lanlinju.videoplayer.VideoPlayerControl
import com.lanlinju.videoplayer.VideoPlayerState
import com.lanlinju.videoplayer.prettyVideoTimestamp
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .adaptiveSize(playerState.isFullscreen.value, view, activity),
                contentAlignment = Alignment.Center
            ) {

                // Video player composable
                VideoPlayer(
                    url = video.url,
                    videoPosition = video.lastPlayPosition,
                    playerState = playerState,
                    headers = video.headers,
                    onBackPress = { handleBackPress(playerState, onBackClick, view, activity) },
                    modifier = Modifier
                        .focusable()
                        .defaultRemoteControlHandler(
                            playerState = playerState,
                            onNextClick = { viewModel.playNextEpisode(playerState.player.currentPosition) }
                        )
                ) {
                    VideoPlayerControl(
                        state = playerState,
                        title = "${video.title}-${video.episodeName}",
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
                                onForwardClick = { playerState.control.skip(85000) }
                            )
                        },
                        onDanmakuClick = { viewModel.setEnabledDanmaku(it) }
                    )
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
        OutlinedButton(onClick = onBackClick) {
            Text(text = stringResource(id = R.string.back), color = Color.White)
        }
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        OutlinedButton(onClick = { viewModel.retry() }) {
            Text(
                text = stringResource(id = R.string.retry),
                color = MaterialTheme.colorScheme.primary
            )
        }
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

private fun Modifier.defaultRemoteControlHandler(
    playerState: VideoPlayerState,
    onNextClick: () -> Unit = {},
) = onKeyEvent { keyEvent: KeyEvent ->
    if (keyEvent.type == KeyEventType.KeyDown)
        when (keyEvent.key) {
            Key.DirectionLeft -> {
                playerState.showControlUi()
                playerState.control.rewind()
                true
            }

            Key.DirectionRight -> {
                playerState.showControlUi()
                playerState.control.forward()
                true
            }

            Key.DirectionUp -> {
                playerState.showEpisodeUi()
                true
            }

            Key.DirectionDown -> {
                playerState.showControlUi()
                onNextClick()
                true
            }

            Key.DirectionCenter, Key.Spacebar -> {
                if (playerState.isPlaying.value) {
                    playerState.showControlUi()
                    playerState.control.pause()
                } else {
                    playerState.control.play()
                }
                true
            }

            else -> false
        } else {
        false
    }
}

@Composable
private fun OptionsContent(
    video: Video,
    isAutoContinuePlayEnabled: Boolean,
    onAutoContinuePlayClick: (Boolean) -> Unit,
    onForwardClick: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Row {
        IconButton(onClick = onForwardClick) {
            Icon(
                imageVector = Icons.Rounded.Forward85,
                contentDescription = "Forward 85s"
            )
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = null
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(id = R.string.external_play))
                    },
                    onClick = {
                        expanded = false
                        openExternalPlayer(video.url)
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.auto_continue_play),
                            color = if (isAutoContinuePlayEnabled) MaterialTheme.colorScheme.primary else Color.Black
                        )
                    },
                    onClick = {
                        onAutoContinuePlayClick(!isAutoContinuePlayEnabled)
                    }
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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (playerState.isLoading.value && !playerState.isError.value && !playerState.isSeeking.value) {
            CircularProgressIndicator()
        }

        if (playerState.isError.value) {
            ShowVideoMessage(
                stringResource(id = R.string.video_error_msg),
                onRetryClick = { playerState.control.retry() }
            )
        }

        val hasNext = videoState.data?.let { it.currentEpisodeIndex + 1 < it.episodes.size } == true
        if (playerState.isEnded.value && isAutoContinuePlayEnabled && hasNext && !playerState.isLoading.value) {
            val countdown = rememberCountdown(initialTime = 3)
            FloatingMessageIndicator(stringResource(R.string.auto_play_next, countdown))
        }

        if (playerState.isSeeking.value) {
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
    onTimeout: () -> Unit = {}
): Int {
    var remaining by remember { mutableIntStateOf(initialTime) }

    LaunchedEffect(initialTime) {
        remaining = initialTime
        while (remaining > 0) {
            delay(1000)
            remaining--
            onTick(remaining)
        }
        onTimeout()
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
                    viewModel.startAutoContinuePlay(
                        playerState.player.currentPosition,
                        showLoading = { playerState.setLoading(true) }
                    )
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
            val isAndroidTV = isAndroidTV(LocalContext.current)
            Spacer(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isAndroidTV) MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.3f
                    ) else Color.Unspecified
                ),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable(),
                onClick = it
            ) {
                Text(text = stringResource(id = R.string.retry))
            }

            LaunchedEffect(Unit) {
                if (isAndroidTV) {
                    focusRequester.requestFocus()
                }
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
        var selectedEpisodeIndex by remember { mutableIntStateOf(video.currentEpisodeIndex) }
        EpisodeSideSheet(
            episodes = video.episodes,
            selectedEpisodeIndex = selectedEpisodeIndex,
            onEpisodeClick = { index, episode ->
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

    SideSheet(onDismissRequest = onDismissRequest, widthRatio = 0.2f) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            speeds.forEachIndexed { index, speed ->
                AdaptiveTextButton(
                    text = speed.first,
                    modifier = Modifier.size(MediumTextButtonSize),
                    onClick = { onSpeedClick(index, speed) },
                    color = if (selectedSpeedIndex == index) MaterialTheme.colorScheme.primary else Color.LightGray,
                )
            }
        }
    }
}

@Composable
private fun ResizeSideSheet(
    selectedResizeIndex: Int,
    onResizeClick: (Int, Pair<String, ResizeMode>) -> Unit,
    onDismissRequest: () -> Unit
) {

    SideSheet(onDismissRequest = onDismissRequest, widthRatio = 0.2f) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Resizes.forEachIndexed { index, resize ->
                AdaptiveTextButton(
                    text = resize.first,
                    modifier = Modifier.size(MediumTextButtonSize),
                    onClick = { onResizeClick(index, resize) },
                    color = if (selectedResizeIndex == index) MaterialTheme.colorScheme.primary else Color.LightGray,
                )
            }
        }
    }
}

@Composable
private fun EpisodeSideSheet(
    episodes: List<Episode>,
    selectedEpisodeIndex: Int,
    onEpisodeClick: (Int, Episode) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isAndroidTV = remember { isAndroidTV(context) }
    SideSheet(onDismissRequest = onDismissRequest, widthRatio = 0.38f) {

        LazyColumn(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = rememberLazyListState(selectedEpisodeIndex, -200)
        ) {
            itemsIndexed(episodes) {index, episode ->
                val focusRequester = remember { FocusRequester() }
                var isFocused by remember { mutableStateOf(false) }
                val selected = index == selectedEpisodeIndex
                OutlinedButton(
                    onClick = { onEpisodeClick(index, episode) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(
                        1.0.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged(onFocusChanged = { isFocused = it.isFocused })
                        .focusRequester(focusRequester)
                        .focusable()
                ) {
                    Text(
                        text = episode.name,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.LightGray,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }

                LaunchedEffect(selected) {
                    if (selected && isAndroidTV) {
                        focusRequester.requestFocus()
                    }
                }
            }
        }
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



