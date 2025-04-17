package com.lanlinju.animius.presentation.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lanlinju.animius.domain.model.AnimeDetail
import com.lanlinju.animius.domain.model.Download
import com.lanlinju.animius.domain.model.Episode
import com.lanlinju.animius.domain.model.Favourite
import com.lanlinju.animius.domain.model.History
import com.lanlinju.animius.domain.repository.AnimeRepository
import com.lanlinju.animius.domain.repository.RoomRepository
import com.lanlinju.animius.domain.usecase.GetAnimeDetailUseCase
import com.lanlinju.animius.presentation.navigation.Screen
import com.lanlinju.animius.util.Resource
import com.lanlinju.animius.util.SourceMode
import com.lanlinju.animius.util.onSuccess
import com.lanlinju.download.download
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val animeRepository: AnimeRepository,
    private val roomRepository: RoomRepository,
    private val getAnimeDetailUseCase: GetAnimeDetailUseCase
) : ViewModel() {

    private val _animeDetailState: MutableStateFlow<Resource<AnimeDetail?>> =
        MutableStateFlow(value = Resource.Loading)
    val animeDetailState: StateFlow<Resource<AnimeDetail?>>
        get() = _animeDetailState

    private val _isFavourite: MutableStateFlow<Boolean> =
        MutableStateFlow(value = false)
    val isFavourite: StateFlow<Boolean>
        get() = _isFavourite

    lateinit var detailUrl: String
    lateinit var mode: SourceMode

    init {
        savedStateHandle.toRoute<Screen.AnimeDetail>().let {
            this.mode = it.mode
            this.detailUrl = it.detailUrl
            getAnimeDetail(this.detailUrl)
        }
    }

    private fun getAnimeDetail(detailUrl: String) {
        viewModelScope.launch {
            _isFavourite.value = roomRepository.checkFavourite(detailUrl).first()
            getAnimeDetailUseCase(detailUrl, mode).collect {
                _animeDetailState.value = it
            }
        }
    }

    fun favourite(favourite: Favourite) {
        viewModelScope.launch {
            _isFavourite.value = !_isFavourite.value
            roomRepository.addOrRemoveFavourite(favourite)
        }
    }

    fun addHistory(history: History) {
        viewModelScope.launch {
            roomRepository.addHistory(history)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun addDownload(download: Download, episodeUrl: String, file: File) {
        viewModelScope.launch {
            animeRepository.getVideoData(episodeUrl, mode)
                .onSuccess {
                    val videoUrl = it.url
                    val downloadDetail =
                        download.downloadDetails.first().copy(downloadUrl = videoUrl)
                    roomRepository.addDownload(download.copy(downloadDetails = listOf(downloadDetail)))
                    // 开始下载视频
                    GlobalScope.download(videoUrl, saveName = file.name, savePath = file.parent!!)
                        .start()
                }
        }
    }

    fun handleDownloadedEpisode(episodes: List<Episode>): Flow<List<Episode>> {
        return flow {
            roomRepository.checkDownload(detailUrl).collect { isStoredDownload ->
                if (!isStoredDownload) {
                    emit(episodes)
                } else {
                    roomRepository.getDownloadDetails(detailUrl).collect { downloadedEpisodes ->
                        val episodeList = episodes.map { episode ->

                            val downloadIndex =
                                downloadedEpisodes.indexOfFirst { d -> d.title == episode.name }

                            episode.copy(isDownloaded = downloadIndex != -1)
                        }

                        emit(episodeList)
                    }
                }
            }
        }
    }

    fun retry() {
        _animeDetailState.value = Resource.Loading
        getAnimeDetail(this.detailUrl)
    }

    fun onChannelClick(index: Int, episodes: List<Episode>) {
        _animeDetailState.value = Resource.Success(
            _animeDetailState.value.data!!.copy(
                channelIndex = index,
                episodes = episodes
            )
        )
    }
}