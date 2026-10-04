package com.lanlinju.animius.presentation.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lanlinju.animius.domain.model.Anime
import com.lanlinju.animius.domain.repository.AnimeRepository
import com.lanlinju.animius.util.SourceHolder
import com.lanlinju.animius.util.SourceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {
    private val _animesState: MutableStateFlow<PagingData<Anime>> =
        MutableStateFlow(value = PagingData.empty())
    val animesState: StateFlow<PagingData<Anime>>
        get() = _animesState

    private val _query: MutableStateFlow<String> = MutableStateFlow(value = "")
    val query: StateFlow<String>
        get() = _query

    /**
     * 用于标识使用当前动漫源搜索数据
     *
     * Note: 在Compose 组合函数中，从上一个界面返当前界面时，[remember]保存的数据状态会丢失。
     */
    var currentSourceMode = SourceHolder.currentSourceMode

    fun onSearch(query: String, mode: SourceMode) {
        getSearchData(query, mode)
    }

    fun clearSearchQuery() {
        _query.value = ""
    }

    fun onQuery(query: String) {
        _query.value = query
    }

    fun getSearchData(query: String, mode: SourceMode) {
        if (query.isEmpty()) return

        viewModelScope.launch {
            repository.getSearchData(query, mode)
                .distinctUntilChanged()
                .cachedIn(viewModelScope)
                .collect {
                    _animesState.value = it
                }
        }
    }
}