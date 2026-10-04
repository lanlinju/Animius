package com.lanlinju.animius.domain.model

data class Video(
    val title: String,
    val url: String, /* 视频播放地址 */
    val episodeName: String,
    val episodeUrl: String,    /* 当前播放的剧集url */
    val lastPlayPosition: Long = 0L, /* 记忆播放视频位置，单位：毫秒 */
    val currentEpisodeIndex: Int, /* 当前播放剧集索引，根据[episodeName]计算得出*/
    val episodes: List<Episode>,
    val headers: Map<String, String> = emptyMap(), /* 用于配置Referer, User-Agent等*/
    val channels: Map<Int, List<Episode>> = emptyMap(), /* 多线路剧集 */
    val channelIndex: Int = 0, /* 当前线路索引 */
)
