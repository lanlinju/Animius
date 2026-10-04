package com.lanlinju.animius.presentation.screen.downloaddetail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanlinju.animius.R
import com.lanlinju.animius.domain.model.Episode
import com.lanlinju.animius.presentation.component.BackTopAppBar
import com.lanlinju.animius.presentation.component.DeleteOverlay
import com.lanlinju.animius.presentation.component.LoadingIndicator
import com.lanlinju.animius.presentation.component.PopupMenuListItem
import com.lanlinju.animius.presentation.component.StateHandler
import com.lanlinju.animius.presentation.navigation.PlayerParameters
import com.lanlinju.animius.util.CROSSFADE_DURATION
import com.lanlinju.animius.util.SourceMode
import com.lanlinju.animius.util.VIDEO_ASPECT_RATIO
import com.lanlinju.animius.util.focus.focusedTextButtonColors
import com.lanlinju.animius.util.focus.rememberIsFocused
import com.lanlinju.animius.util.toast
import com.lanlinju.download.Progress
import com.lanlinju.download.core.DownloadTask
import com.lanlinju.download.download
import com.lanlinju.download.utils.formatSize
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import com.lanlinju.download.State as DownloadSate

@Composable
fun DownloadDetailScreen(
    onNavigateToVideoPlay: (parameters: String) -> Unit,
    onNavigateToAnimeDetail: (detailUrl: String, mode: SourceMode) -> Unit,
    onBackClick: () -> Unit
) {
    val viewModel: DownloadDetailViewModel = hiltViewModel()
    val downloadDetailsState = viewModel.downloadDetailsState.collectAsState()
    val titleState = viewModel.title.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    StateHandler(
        state = downloadDetailsState.value,
        onLoading = { LoadingIndicator() },
        onFailure = { }
    ) { resource ->
        var isDeleteMode by remember { mutableStateOf(false) }
        val detailUrlState = viewModel.detailUrl.collectAsState()
        val sourceModeState = viewModel.sourceMode.collectAsState()

        Scaffold(topBar = {
            BackTopAppBar(
                title = titleState.value,
                onBackClick = onBackClick,
                actions = {
                    val (detailFocused, detailModifier) = rememberIsFocused()
                    TextButton(
                        onClick = { onNavigateToAnimeDetail(detailUrlState.value, sourceModeState.value) },
                        modifier = detailModifier,
                        colors = focusedTextButtonColors(
                            detailFocused,
                            unfocusedContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text(stringResource(id = R.string.anime_detail)) }
                    val (deleteFocused, deleteModifier) = rememberIsFocused()
                    TextButton(
                        onClick = { isDeleteMode = !isDeleteMode },
                        modifier = deleteModifier,
                        colors = focusedTextButtonColors(
                            deleteFocused,
                            unfocusedContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text(if (isDeleteMode) stringResource(R.string.cancel) else stringResource(R.string.delete)) }
                }
            )
        }) { paddingValues ->
            if (isDeleteMode) {
                BackHandler { isDeleteMode = false }
            }
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                resource.data?.let { downloadDetailList ->

                    itemsIndexed(
                        downloadDetailList,
                        key = { i, d -> d.downloadUrl }) { index, downloadDetail ->

                        val state =
                            rememberDownloaderState(
                                url = downloadDetail.downloadUrl,
                                path = downloadDetail.path,
                                isSucceed = downloadDetail.fileSize != 0L,
                                progress = Progress(
                                    downloadSize = downloadDetail.downloadSize,
                                    totalSize = downloadDetail.totalSize
                                ),
                                onSucceed = {
                                    viewModel.updateDownloadDetail(downloadDetail, it)
                                }
                            )

                        SideEffect {
                            // 刷新已经下载完成的
                            if (downloadDetail.fileSize == 0L && state.file.exists()) {
                                state.start()
                            }
                        }

                        PopupMenuListItem(
                            content = {
                                Box {
                                    DownloadEpisodeItem(
                                        title = downloadDetail.title,
                                        imgUrl = downloadDetail.imgUrl,
                                        state = state
                                    )
                                    DeleteOverlay(
                                        visible = isDeleteMode,
                                        modifier = Modifier
                                            .padding(
                                                horizontal = dimensionResource(id = R.dimen.small_padding),
                                                vertical = dimensionResource(id = R.dimen.small_padding)
                                            )
                                            .height(dimensionResource(id = R.dimen.image_cover_height))
                                            .aspectRatio(VIDEO_ASPECT_RATIO)
                                    )
                                }
                            },
                            menuText = stringResource(id = R.string.delete),
                            onClick = {
                                if (isDeleteMode) {
                                    viewModel.deleteDownloadDetail(
                                        downloadDetail.downloadUrl,
                                        deleteFile = { state.remove() }
                                    )
                                } else {
                                    when {
                                        state.isSucceed.value -> {
                                        val title = titleState.value
                                        val episodeName = downloadDetail.title
                                        scope.launch {
                                            // 过滤出已下载好的视频剧集
                                            val episodes =
                                                downloadDetailList.filter { it.fileSize != 0L }
                                                    .map {
                                                        Episode(name = it.title, url = it.path)
                                                    }
                                            // 根据集数名获取对应视频
                                            val index =
                                                episodes.indexOfFirst { it.name == episodeName }

                                            if (index != -1) {
                                                PlayerParameters.serialize(
                                                    title = title,
                                                    episodeIndex = index,
                                                    episodes = episodes,
                                                    isLocalVideo = true,
                                                ).let { onNavigateToVideoPlay(it) }
                                            } else {
                                                context.toast(R.string.unknown_error)
                                            }
                                        }
                                    }

                                    state.isStarted() -> state.stop()
                                    else -> state.start()
                                }
                                }
                            },
                            onMenuItemClick = {
                                viewModel.deleteDownloadDetail(
                                    downloadDetail.downloadUrl,
                                    deleteFile = { state.remove() }
                                )
                            }
                        )

                    }

                }
            }
        }
    }

}

@Composable
fun DownloadEpisodeItem(
    modifier: Modifier = Modifier,
    title: String,
    imgUrl: String,
    state: DownloaderState,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(
                    horizontal = dimensionResource(id = R.dimen.small_padding),
                    vertical = dimensionResource(id = R.dimen.small_padding)
                ),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.small_padding))
        ) {
            AnimeImage(imgUrl = imgUrl, isDownloaded = state.isSucceed.value)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AnimeTitle(title)

                if (!state.isSucceed.value) {
                    DownloadInfo(state = state)
                } else {
                    DownloadFinishInfo(state)
                }
            }
        }

    }
}

@Composable
private fun AnimeTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun AnimeImage(imgUrl: String, isDownloaded: Boolean) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imgUrl)
            .crossfade(CROSSFADE_DURATION)
            .build(),
        contentDescription = stringResource(id = R.string.lbl_anime_img),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .height(dimensionResource(id = R.dimen.image_cover_height))
            .aspectRatio(VIDEO_ASPECT_RATIO)
            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.small_padding)))
            .blur(if (isDownloaded) 0.dp else 8.dp)
    )
}

@Composable
private fun DownloadInfo(state: DownloaderState) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.small_padding))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = state.speedStr.value,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = state.stateMessage.value,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = state.percentStr.value,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Bottom)
            )
        }

        DownloadProgress(isStarted = state.isStarted(), progress = state.progress.value)
    }
}

@Composable
private fun DownloadProgress(isStarted: Boolean, progress: Float) {
    if (isStarted) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .height(2.dp),
            strokeCap = StrokeCap.Round,
        )
    } else {
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun DownloadFinishInfo(state: DownloaderState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(id = R.string.play),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )

        // 文件大小
        Text(
            text = state.file.length().formatSize(),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun rememberDownloaderState(
    url: String,
    path: String,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    context: Context = LocalContext.current,
    progress: Progress = Progress(),
    isSucceed: Boolean = false,
    onSucceed: (DownloadTask) -> Unit = { }
): DownloaderState = remember {
    val file = File(path)
    val downloadTask = GlobalScope.download(
        url = url,
        saveName = file.name,
        savePath = file.parent!!
    )
    DownloaderSateImpl(
        downloadTask = downloadTask,
        coroutineScope = coroutineScope,
        context = context,
        onSucceed = onSucceed,
        isSucceed = isSucceed,
        progress = progress,
        file = file
    )
}

class DownloaderSateImpl(
    override val downloadTask: DownloadTask,
    override val file: File,
    private val coroutineScope: CoroutineScope,
    val context: Context,
    progress: Progress,
    isSucceed: Boolean,
    onSucceed: (DownloadTask) -> Unit,
) : DownloaderState {
    override val progress = mutableStateOf(progress.progress())
    override val percentStr = mutableStateOf(progress.percentStr())
    override val speedStr = mutableStateOf("0.0 B/s")
    override val stateMessage = mutableStateOf("未开始")
    override val isSucceed = mutableStateOf(isSucceed)
    override fun isStarted(): Boolean {
        return downloadTask.isStarted()
    }

    override fun start() {
        downloadTask.start()
    }

    override fun stop() {
        downloadTask.stop()
    }

    override fun remove() {
        downloadTask.remove()
    }

    init {
        coroutineScope.launch {
            launch {
                downloadTask.state().onEach { state ->
                    when (state) {
                        is DownloadSate.None -> stateMessage.value =
                            context.getString(R.string.unstart)

                        is DownloadSate.Waiting -> stateMessage.value =
                            context.getString(R.string.waiting)

                        is DownloadSate.Downloading -> stateMessage.value =
                            context.getString(R.string.downloading)

                        is DownloadSate.Failed -> stateMessage.value =
                            context.getString(R.string.retry)

                        is DownloadSate.Stopped -> stateMessage.value =
                            context.getString(R.string.resume)

                        is DownloadSate.Succeed -> {
                            stateMessage.value = context.getString(R.string.complete)
                            this@DownloaderSateImpl.isSucceed.value = true
                            onSucceed(downloadTask)
                        }
                    }
                }.launchIn(this)
            }

            launch {
                downloadTask.progress().onEach {
                    this@DownloaderSateImpl.progress.value = it.progress()
                    percentStr.value = it.percentStr()
                }.launchIn(this)
            }

            launch {
                downloadTask.speedStr().onEach {
                    speedStr.value = it
                }.launchIn(this)
            }
        }
    }
}

interface DownloaderState {
    val downloadTask: DownloadTask

    val file: File

    val progress: State<Float>
    val percentStr: State<String>
    val speedStr: State<String>
    val stateMessage: State<String>

    val isSucceed: State<Boolean>

    fun isStarted(): Boolean

    fun start()
    fun stop()
    fun remove()
}