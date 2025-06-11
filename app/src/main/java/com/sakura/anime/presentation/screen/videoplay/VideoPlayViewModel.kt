package com.sakura.anime.presentation.screen.videoplay


import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anime.danmaku.api.DanmakuSession
import com.example.componentsui.anime.domain.model.Episode
import com.sakura.anime.application.AnimeApplication
import com.sakura.anime.domain.model.Video
import com.sakura.anime.domain.repository.DanmakuRepository
import com.sakura.anime.domain.repository.RoomRepository
import com.sakura.anime.domain.usecase.GetVideoFromRemoteUseCase
import com.sakura.anime.presentation.navigation.ROUTE_ARGUMENT_SOURCE_MODE
import com.sakura.anime.presentation.navigation.ROUTE_ARGUMENT_VIDEO_EPISODE_URL
import com.sakura.anime.util.KEY_DANMAKU_ENABLED
import com.sakura.anime.util.KEY_FROM_LOCAL_VIDEO
import com.sakura.anime.util.Resource
import com.sakura.anime.util.SourceMode
import com.sakura.anime.util.preferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoPlayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepository: RoomRepository,
    private val danmakuRepository: DanmakuRepository,
    private val getVideoFromRemoteUseCase: GetVideoFromRemoteUseCase,
) : ViewModel() {

    // 保存视频状态的流，默认为加载中
    private val _videoState: MutableStateFlow<Resource<Video?>> = MutableStateFlow(Resource.Loading)
    val videoState: StateFlow<Resource<Video?>> get() = _videoState

    // 获取保存的偏好设置，初始化弹幕启用状态
    private val preferences = AnimeApplication.getInstance().preferences
    private val _enabledDanmaku =
        MutableStateFlow(preferences.getBoolean(KEY_DANMAKU_ENABLED, true))
    val enabledDanmaku = _enabledDanmaku.asStateFlow()

    private val _danmakuSession = MutableStateFlow<DanmakuSession?>(null)
    val danmakuSession = _danmakuSession.asStateFlow()

    // 判断是否为本地视频
    private var isLocalVideo = false
    lateinit var mode: SourceMode

    // 当前集数的URL和历史记录ID
    private var currentEpisodeUrl: String = ""
    private var historyId: Long = -1L

    // 自动连播相关
    private var autoContinuePlayJob: Job? = null

    init {
        // 从SavedStateHandle中获取播放模式和视频集数的URL
        savedStateHandle.get<String>(ROUTE_ARGUMENT_SOURCE_MODE)?.let { mode ->
            this.mode = SourceMode.valueOf(mode)
        }
        savedStateHandle.get<String>(ROUTE_ARGUMENT_VIDEO_EPISODE_URL)?.let { episodeUrl ->
            val url = Uri.decode(episodeUrl)
            if (url.contains(KEY_FROM_LOCAL_VIDEO)) {
                // 如果是本地视频，获取本地视频
                isLocalVideo = true
                getVideoFromLocal(url)
            } else {
                // 如果是远程视频，获取历史记录Id并加载远程视频
                currentEpisodeUrl = url
                getHistoryId(episodeUrl)
                getVideoFromRemote(url)
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
    private fun getVideoFromLocal(params: String) {
        viewModelScope.launch {
            val list = params.split(":")
            val detailUrl = list[1]
            val title = list[2]
            val episodeName = list[3]

            // 从Room数据库中获取下载的详细信息
            roomRepository.getDownloadDetails(detailUrl).collect { downloadDetails ->
                val episodes = downloadDetails.filter { it.fileSize != 0L }.map {
                    Episode(name = it.title, url = it.path.toUri().toString())
                }
                // 根据集数名获取对应视频
                val index = episodes.indexOfFirst { it.name == episodeName }
                if (index != -1) {
                    _videoState.value = Resource.Success(
                        Video(
                            title = title,
                            url = episodes[index].url,
                            episodeName = episodeName,
                            episodeUrl = episodes[index].url,
                            currentEpisodeIndex = index,
                            episodes = episodes
                        )
                    )
                }
            }
        }
    }

    /**
     * 获取远程视频
     * @param episodeUrl 视频集数的URL
     */
    private fun getVideoFromRemote(episodeUrl: String) {
        _danmakuSession.value = null
        viewModelScope.launch {
            // 使用用例从远程获取视频信息
            _videoState.value = getVideoFromRemoteUseCase(episodeUrl, mode)
            // 如果启用了弹幕，获取弹幕会话
            if (_enabledDanmaku.value) {
                _danmakuSession.value = fetchDanmakuSession()
            }
        }
    }

    /**
     * 获取弹幕会话
     * @return 弹幕会话或null
     */
    private suspend fun fetchDanmakuSession(): DanmakuSession? {
        return _videoState.value.data?.let { video ->
            // 使用视频的标题和集数名获取对应的弹幕
            danmakuRepository.fetchDanmakuSession(video.title, video.episodeName)
        }
    }

    /**
     * 设置弹幕是否启用
     * @param enabled 弹幕启用状态
     */
    fun setEnabledDanmaku(enabled: Boolean) {
        _enabledDanmaku.value = enabled
        preferences.edit { putBoolean(KEY_DANMAKU_ENABLED, enabled) }
        viewModelScope.launch {
            // 如果启用了弹幕且当前弹幕会话为空，则获取弹幕会话
            if (enabled && _danmakuSession.value == null) {
                _danmakuSession.value = fetchDanmakuSession()
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
        } else {
            // 如果是远程视频，保存播放进度并重新获取远程视频
            currentEpisodeUrl = url
            saveVideoPosition(videoPosition)
            getVideoFromRemote(url)
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
     * @param videoPosition 当前视频的播放位置
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
     * @param videoPosition 当前视频的播放位置
     */
    fun saveVideoPosition(videoPosition: Long) {
        // 观看时长少于5秒或本地视频的播放时长不保存
        if (videoPosition < 5_000 || isLocalVideo) return

        _videoState.value.data?.let { video ->
            viewModelScope.launch {
                val episode = Episode(
                    name = video.episodeName,
                    url = video.episodeUrl,
                    lastPosition = videoPosition,
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
        getVideoFromRemote(currentEpisodeUrl)
    }
}
