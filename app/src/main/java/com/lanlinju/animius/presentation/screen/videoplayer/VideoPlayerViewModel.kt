package com.lanlinju.animius.presentation.screen.videoplayer

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anime.danmaku.api.DanmakuSession
import com.lanlinju.animius.data.remote.parse.util.SourceAuthManager
import com.lanlinju.animius.domain.model.Episode
import com.lanlinju.animius.domain.model.Video
import com.lanlinju.animius.domain.model.WebVideo
import com.lanlinju.animius.domain.repository.AnimeRepository
import com.lanlinju.animius.domain.repository.DanmakuRepository
import com.lanlinju.animius.domain.repository.RoomRepository
import com.lanlinju.animius.presentation.navigation.PlayerParameters
import com.lanlinju.animius.presentation.navigation.Screen
import com.lanlinju.animius.util.KEY_DANMAKU_ENABLED
import com.lanlinju.animius.util.Resource
import com.lanlinju.animius.util.Result
import com.lanlinju.animius.util.SourceMode
import com.lanlinju.animius.util.onError
import com.lanlinju.animius.util.onSuccess
import com.lanlinju.animius.util.preferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepository: RoomRepository,
    private val animeRepository: AnimeRepository,
    private val danmakuRepository: DanmakuRepository,
    private val application: Application
) : ViewModel() {

    // 保存视频状态的流，默认为加载中
    private val _videoState: MutableStateFlow<Resource<Video>> = MutableStateFlow(Resource.Loading)
    val videoState: StateFlow<Resource<Video>> get() = _videoState

    // 切换剧集/线路加载失败的错误（保留当前视频数据时使用，避免整个播放器被失败页替换）
    private val _videoLoadError = MutableStateFlow<Throwable?>(null)
    val videoLoadError: StateFlow<Throwable?> get() = _videoLoadError

    // 数据源要求先登录时，待打开的网页（非 null 时 UI 拉起 WebView）
    private val _needWebAuth = MutableStateFlow<SourceAuthManager.PendingWebAuth?>(null)
    val needWebAuth: StateFlow<SourceAuthManager.PendingWebAuth?> get() = _needWebAuth

    /**
     * 读取数据源留下的待处理鉴权请求（如次元城播放需要登录）。
     * 数据源在 getVideoData 失败时通过 [SourceAuthManager.requestWebAuth] 提交。
     */
    private fun syncPendingWebAuth() {
        SourceAuthManager.consumePendingWebAuth()?.let {
            _needWebAuth.value = it
        }
    }

    fun clearNeedWebAuth() {
        _needWebAuth.value = null
    }

    // 获取保存的偏好设置，初始化弹幕启用状态
    private val preferences = application.preferences
    private val _danmakuEnabled =
        MutableStateFlow(preferences.getBoolean(KEY_DANMAKU_ENABLED, false))
    val danmakuEnabled = _danmakuEnabled.asStateFlow()

    private val _danmakuSession = MutableStateFlow<DanmakuSession?>(null)
    val danmakuSession = _danmakuSession.asStateFlow()

    // 判断是否为本地视频
    private var isLocalVideo = false
    private var mode: SourceMode? = null

    // 当前集数的URL和历史记录ID
    private var currentEpisodeUrl: String = ""
    private var currentEpisodeIndex: Int = 0
    private var historyId: Long = -1L

    // 线路数据
    private var channels: Map<Int, List<Episode>> = emptyMap()
    private var currentChannelIndex: Int = 0

    // 自动连播相关
    private var autoContinuePlayJob: Job? = null
    // 视频加载 Job（用于取消上一条 fetch，避免竞态错误）
    private var videoLoadJob: Job? = null

    /**
     * 视频"骨架"（标题/剧集/线路），不含真实播放地址。
     *
     * 首次加载失败后 _videoState 会是 Error/Loading,此时 [Resource.data] 为 null。
     * 重试（例如登录后重试）需要据此重建 Video,否则会 NPE。
     */
    private var videoSkeleton: Video? = null

    init {
        viewModelScope.launch {
            // 从SavedStateHandle中获取播放模式和视频集数的URL
            savedStateHandle.toRoute<Screen.VideoPlayer>().let {
                val params = PlayerParameters.deserialize(it.parameters)
                this@VideoPlayerViewModel.mode = params.mode
                channels = params.channels
                currentChannelIndex = params.channelIndex
                val url = params.episodes[params.episodeIndex].url
                if (params.isLocalVideo) {
                    // 如果是本地视频，获取本地视频
                    isLocalVideo = true
                    getVideoFromLocal(params)
                } else {
                    currentEpisodeUrl = url
                    currentEpisodeIndex = params.episodeIndex
                    // 如果是远程视频，获取历史记录Id并加载远程视频
                    getHistoryId(url)

                    val currentEpisode = params.episodes[params.episodeIndex]
                    // 先建好骨架,后续无论成功/失败/重试都能据此重建 Video
                    videoSkeleton = Video(
                        title = params.title,
                        url = currentEpisode.url,
                        episodeName = currentEpisode.name,
                        episodeUrl = currentEpisode.url,
                        currentEpisodeIndex = params.episodeIndex,
                        episodes = params.episodes,
                        channels = channels,
                        channelIndex = currentChannelIndex
                    )
                    getWebVideo(episodeUrl = currentEpisode.url, mode = params.mode!!)
                        .onSuccess {
                            val lastPlayPosition =
                                roomRepository.getEpisode(currentEpisode.url)
                                    .first()?.lastPlayPosition ?: 0L
                            Resource.Success(
                                videoSkeleton!!.copy(
                                    url = it.url,
                                    headers = it.headers,
                                    lastPlayPosition = lastPlayPosition
                                )
                            ).let {
                                _videoState.value = it

                                // 获取弹幕会话
                                fetchDanmakuSession()
                            }
                        }
                        .onError {
                            // 数据源可能要求先登录（如次元城），把待办网页暴露给 UI
                            syncPendingWebAuth()
                            _videoState.value = Resource.Error(it)
                        }

                }
            }
        }
    }

    /**
     * 获取历史记录ID，用于后续保存播放进度
     */
    private fun getHistoryId(episodeUrl: String) {
        viewModelScope.launch {
            roomRepository.getEpisode(episodeUrl).collect { episode ->
                episode?.let { historyId = it.historyId }
            }
        }
    }

    /**
     * 获取本地视频信息
     * @param params 格式化的本地视频参数
     */
    private fun getVideoFromLocal(params: PlayerParameters) {
        viewModelScope.launch {
            val title = params.title
            val index = params.episodeIndex
            val episodes = params.episodes

            _videoState.value = Resource.Success(
                Video(
                    title = title,
                    url = episodes[index].url,
                    episodeName = episodes[index].name,
                    episodeUrl = episodes[index].url,
                    currentEpisodeIndex = index,
                    episodes = episodes
                )
            )

            fetchDanmakuSession()
        }
    }

    private suspend fun getWebVideo(episodeUrl: String, mode: SourceMode): Result<WebVideo> {
        return animeRepository.getVideoData(episodeUrl, mode)
    }

    /**
     * 获取远程视频
     * @param episodeUrl 视频集数的URL
     */
    private fun getVideoFromRemote(episodeUrl: String, index: Int) {
        videoLoadJob?.cancel()
        videoLoadJob = viewModelScope.launch {
            // 使用用例从远程获取视频信息
            getWebVideo(episodeUrl, mode!!)
                .onSuccess { webVideo ->
                    val lastPlayPosition =
                        roomRepository.getEpisode(episodeUrl).first()?.lastPlayPosition ?: 0L
                    _videoLoadError.value = null
                    // 切换剧集/线路时用当前数据;首次加载失败后重试时当前是 Error/Loading,
                    // 其 data 为 null,回退到骨架重建(不能直接 data!!,会 NPE 崩溃)
                    val base = _videoState.value.data ?: videoSkeleton
                    if (base == null) {
                        _videoState.value = Resource.Error(
                            IllegalStateException("视频信息缺失,请返回后重新进入")
                        )
                        return@onSuccess
                    }
                    _videoState.value = Resource.Success(
                        base.copy(
                            url = webVideo.url,
                            headers = webVideo.headers,
                            currentEpisodeIndex = index,
                            episodeName = base.episodes.getOrNull(index)?.name
                                ?: base.episodeName,
                            episodeUrl = episodeUrl,
                            lastPlayPosition = lastPlayPosition
                        )
                    )

                    fetchDanmakuSession() // 视频加载成功后获取弹幕
                }
                .onError {
                    // 数据源可能要求先登录（如次元城），把待办网页暴露给 UI
                    syncPendingWebAuth()
                    // 已有正在播放的视频时（切换剧集/线路失败），保留当前视频与播放器 UI，
                    // 仅标记加载失败，用户可继续切换选集/线路；首次加载失败仍走整页失败。
                    if (_videoState.value is Resource.Success) {
                        _videoLoadError.value = it
                    } else {
                        _videoState.value = Resource.Error(it)
                    }
                }
        }
    }

    /**
     * 获取弹幕会话
     * @return 弹幕会话或null
     *
     * TODO: 优化
     */
    private fun fetchDanmakuSession() {
        _danmakuSession.value = null // 清除当前剧集的弹幕

        // 如果未启用了弹幕，直接返回
        if (!_danmakuEnabled.value) return

        viewModelScope.launch {
            _videoState.value.data?.let { video ->
                // 使用视频的标题和集数名获取对应的弹幕
                _danmakuSession.value =
                    danmakuRepository.fetchDanmakuSession(video.title, video.episodeName)
            }
        }
    }

    /**
     * 设置弹幕是否启用
     * @param enabled 弹幕启用状态
     */
    fun setEnabledDanmaku(enabled: Boolean) {
        _danmakuEnabled.value = enabled
        preferences.edit { putBoolean(KEY_DANMAKU_ENABLED, enabled) }
        viewModelScope.launch {
            // 如果启用了弹幕且当前弹幕会话为空，则获取弹幕会话
            if (enabled && _danmakuSession.value == null) {
                fetchDanmakuSession()
            }
        }
    }

    /**
     * 获取当前视频或切换视频集数
     * @param url 当前集数的URL
     * @param episodeName 当前集数的名称
     * @param index 当前集数的索引
     * @param videoPosition 当前视频的播放位置
     */
    fun getVideo(url: String, episodeName: String, index: Int, videoPosition: Long) {
        if (isLocalVideo) {
            // 如果是本地视频，更新状态
            _videoState.value.data?.let { video ->
                _videoState.value = Resource.Success(
                    video.copy(url = url, episodeName = episodeName, currentEpisodeIndex = index)
                )
            }
            fetchDanmakuSession()
        } else {
            // 如果是远程视频，保存播放进度并重新获取远程视频
            currentEpisodeUrl = url
            currentEpisodeIndex = index
            _videoLoadError.value = null
            saveVideoPosition(videoPosition)
            getVideoFromRemote(url, index)
        }
    }

    /**
     * 切换线路，仅更新剧集列表，不中断当前播放
     * @param channelIndex 目标线路索引
     */
    fun switchChannel(channelIndex: Int) {
        if (channelIndex == currentChannelIndex) return
        currentChannelIndex = channelIndex
        _videoState.value.data?.let { video ->
            _videoState.value = Resource.Success(
                video.copy(
                    channelIndex = channelIndex,
                    episodes = channels[channelIndex] ?: video.episodes
                )
            )
        }
    }

    /**
     * 播放器播放结束时触发，启动 3 秒延迟的自动连播
     */
    fun startAutoContinuePlay(currPlayPosition: Long) {
        cancelAutoContinuePlay()
        autoContinuePlayJob = viewModelScope.launch {
            delay(3000)
            playNextEpisode(currPlayPosition)
        }
    }

    fun cancelAutoContinuePlay() {
        autoContinuePlayJob?.cancel()
        autoContinuePlayJob = null
    }

    /**
     * 切换到下一集
     * @param currPlayPosition 当前视频的播放位置, 单位：毫秒
     */
    fun playNextEpisode(currPlayPosition: Long) {
        viewModelScope.launch {
            _videoState.value.data?.let { video ->
                val nextEpisodeIndex = video.currentEpisodeIndex + 1
                if (nextEpisodeIndex < video.episodes.size) {
                    // 获取下一集视频
                    getVideo(
                        video.episodes[nextEpisodeIndex].url,
                        video.episodes[nextEpisodeIndex].name,
                        nextEpisodeIndex,
                        currPlayPosition
                    )
                }
            }
        }
    }

    /**
     * 保存当前视频的播放进度
     * @param currPlayPosition 当前视频的播放位置, 单位：毫秒
     */
    fun saveVideoPosition(currPlayPosition: Long) {
        // 观看时长少于5秒或本地视频的播放时长不保存
        if (currPlayPosition < 5_000 || isLocalVideo) return

        _videoState.value.data?.let { video ->
            viewModelScope.launch {
                val episode = Episode(
                    name = video.episodeName,
                    url = video.episodeUrl,
                    lastPlayPosition = currPlayPosition,
                    historyId = historyId
                )
                // 将当前视频进度保存到Room数据库中
                roomRepository.addEpisode(episode)
            }
        }
    }

    /**
     * 重新尝试加载远程视频
     */
    fun retry() {
        _videoState.value = Resource.Loading
        getVideoFromRemote(currentEpisodeUrl, currentEpisodeIndex)
    }

    /**
     * 在播放器内重试加载（切换剧集/线路失败后使用）。
     * 不置 Loading、不替换整个页面，仅重新拉取当前选中的集。
     */
    fun retryLoad() {
        _videoLoadError.value = null
        getVideoFromRemote(currentEpisodeUrl, currentEpisodeIndex)
    }

    /**
     * 网页登录完成后的重试。
     *
     * 已有正在播放的视频时走 [retryLoad]（只重拉当前集，不打断播放）；
     * 首次加载就失败时走 [retry]（整页重新加载）。
     */
    fun retryAfterWebAuth() {
        if (_videoState.value is Resource.Success) {
            retryLoad()
        } else {
            retry()
        }
    }
}
