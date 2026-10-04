package com.lanlinju.animius.util

import kotlinx.coroutines.flow.MutableStateFlow
import com.lanlinju.animius.application.AnimeApplication
import com.lanlinju.animius.data.remote.parse.AgedmSource
import com.lanlinju.animius.data.remote.parse.AnimeSource
import com.lanlinju.animius.data.remote.parse.CycanimeSource
import com.lanlinju.animius.data.remote.parse.GirigiriSource
import com.lanlinju.animius.data.remote.parse.GogoanimeSource
import com.lanlinju.animius.data.remote.parse.GugufanSource
import com.lanlinju.animius.data.remote.parse.SilisiliSource
import com.lanlinju.animius.data.remote.parse.XifanSource

object SourceHolder {
    private lateinit var _currentSource: AnimeSource
    private lateinit var _currentSourceMode: SourceMode

    /**
     * 默认动漫源
     */
    val DEFAULT_ANIME_SOURCE = SourceMode.Silisili

    val currentSource: AnimeSource
        get() = _currentSource

    val currentSourceMode: SourceMode
        get() = _currentSourceMode

    val isSourceChanged = MutableStateFlow(0)

    init {
        val preferences = AnimeApplication.getInstance().preferences
        initDefaultSource(preferences.getEnum(KEY_SOURCE_MODE, DEFAULT_ANIME_SOURCE))
    }

    /**
     * 初始化加载默认的数据源，切换数据源请用方法[SourceHolder].switchSource()
     */
    private fun initDefaultSource(mode: SourceMode) {
        _currentSource = getSource(mode)
        _currentSourceMode = mode
        _currentSource.onEnter()
    }

    /**
     *切换数据源
     */
    fun switchSource(mode: SourceMode) {
        _currentSource.onExit()

        _currentSource = getSource(mode)
        _currentSourceMode = mode

        _currentSource.onEnter()
    }

    /**
     * 根据[SourceMode]获取对应的[AnimeSource]数据源
     * */
    fun getSource(mode: SourceMode): AnimeSource {
        return when (mode) {
            SourceMode.Silisili -> SilisiliSource
            SourceMode.Agedm -> AgedmSource
            SourceMode.Girigiri -> GirigiriSource
            SourceMode.Cycanime -> CycanimeSource
            SourceMode.Xifan -> XifanSource()
            SourceMode.Gugufan -> GugufanSource()
            SourceMode.Gogoanime -> GogoanimeSource
        }
    }
}

enum class SourceMode {
    Silisili,
    Agedm,
    Girigiri,
    Cycanime,
    Gugufan,
    Xifan,
    Gogoanime;

    companion object {
        /**
         * 按名字查找数据源，名字已不存在时返回 null 而不是抛异常。
         *
         * 收藏/历史/下载表里存的是枚举名，删掉数据源后老记录会留下失效名字，
         * 直接用 [valueOf] 读会抛 IllegalArgumentException 把整个列表一起打挂。
         */
        fun fromName(name: String): SourceMode? = entries.firstOrNull { it.name == name }
    }
}