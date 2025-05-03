package com.lanlinju.animius.presentation.screen.videoplayer

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anime.danmaku.api.DanmakuSession
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

    // 自动连播相关
    private var autoContinuePlayJob: Job? = null

    init {
        viewModelScope.launch {
            // 从SavedStateHandle中获取播放模式和视频集数的URL
            savedStateHandle.toRoute<Screen.VideoPlayer>().let {
                val params = PlayerParameters.deserialize(it.parameters)
                this@VideoPlayerViewModel.mode = params.mode
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
                    getWebVideo(episodeUrl = currentEpisode.url, mode = params.mode!!)
                        .onSuccess {
                            val lastPlayPosition =
                                roomRepository.getEpisode(currentEpisode.url)
                                    .first()?.lastPlayPosition ?: 0L
                            Video(
                                title = params.title,
                                url = it.url,
                                episodeName = currentEpisode.name,
                                episodeUrl = currentEpisode.url,
                                lastPlayPosition = lastPlayPosition,
                                currentEpisodeIndex = params.episodeIndex,
                                episodes = params.episodes,
                                headers = it.headers
                            ).let {
                                _videoState.value = Resource.Success(it)

                                // 获取弹幕会话
                                fetchDanmakuSession()
                            }
                        }
                        .onError {
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
        viewModelScope.launch {
            // 使用用例从远程获取视频信息
            getWebVideo(episodeUrl, mode!!)
                .onSuccess { webVideo ->
                    val lastPlayPosition =
                        roomRepository.getEpisode(episodeUrl).first()?.lastPlayPosition ?: 0L
                    _videoState.value = Resource.Success(
                        _videoState.value.data!!.let {
                            it.copy(
                                url = webVideo.url,
                                currentEpisodeIndex = index,
                                episodeName = it.episodes[index].name,
                                episodeUrl = episodeUrl,
                                lastPlayPosition = lastPlayPosition
                            )
                        }
                    )

                    fetchDanmakuSession() // 视频加载成功后获取弹幕
                }
                .onError {
                    _videoState.value = Resource.Error(it)
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
            saveVideoPosition(videoPosition)
            getVideoFromRemote(url, index)
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
}
