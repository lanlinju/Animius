package com.lanlinju.animius.presentation.navigation

import com.lanlinju.animius.domain.model.Episode
import com.lanlinju.animius.util.SourceMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class Screen {
    @Serializable
    object Main : Screen()

    @Serializable
    object Search : Screen()

    @Serializable
    object HistoryScreen : Screen()

    @Serializable
    object Download : Screen()

    @Serializable
    object Appearance : Screen()

    @Serializable
    object DanmakuSettings : Screen()

    @Serializable
    data class DownloadDetail(val detailUrl: String, val title: String, val sourceMode: SourceMode) : Screen()

    @Serializable
    data class AnimeDetail(val detailUrl: String, val mode: SourceMode) : Screen()

    @Serializable
    data class VideoPlayer(val parameters: String) : Screen()
}

@Serializable
data class PlayerParameters(
    val title: String,              /* 动漫名称 */
    val episodeIndex: Int,          /* 剧集列表索引 */
    val episodes: List<Episode>,    /* 剧集列表 */
    val mode: SourceMode?,           /* 来源 */
    val isLocalVideo: Boolean = false,   /* 是否为本地视频 */
    val channels: Map<Int, List<Episode>> = emptyMap(), /* 多线路剧集 */
    val channelIndex: Int = 0,      /* 当前线路索引 */
) {
    companion object {
        suspend fun serialize(
            title: String,
            episodeIndex: Int,
            episodes: List<Episode>,
            mode: SourceMode? = null,
            isLocalVideo: Boolean = false,
            channels: Map<Int, List<Episode>> = emptyMap(),
            channelIndex: Int = 0
        ): String {
            return Json.encodeToString(
                PlayerParameters(
                    title,
                    episodeIndex,
                    episodes,
                    mode,
                    isLocalVideo,
                    channels,
                    channelIndex,
                )
            )
        }

        suspend fun deserialize(value: String): PlayerParameters {
            return Json.decodeFromString<PlayerParameters>(value)
        }
    }
}

/*
val PlayerParametersType = NavType(
    parseValue = { value ->
        Json.decodeFromString<PlayerParameters>(value)
    },
    serializeAsValue = { value ->
        Json.encodeToString(value)
    }
)

val PlayerParametersType = object : NavType<PlayerParameters>(isNullableAllowed = false) {
    override fun get(bundle: Bundle, key: String): PlayerParameters? {
        return bundle.getString(key)?.let { Json.decodeFromString(it) }
    }

    override fun parseValue(value: String): PlayerParameters {
        return Json.decodeFromString(value)
    }

    override fun put(bundle: Bundle, key: String, value: PlayerParameters) {
        bundle.putString(key, Json.encodeToString(value))
    }
}

inline fun <reified T> NavType(
    crossinline parseValue: (String) -> T,
    crossinline serializeAsValue: (T) -> String,
    isNullableAllowed: Boolean = false
) = object : NavType<T>(isNullableAllowed = isNullableAllowed) {

    override fun get(bundle: Bundle, key: String): T? =
        bundle.getString(key)?.let(::parseValue)

    override fun put(bundle: Bundle, key: String, value: T) =
        bundle.putString(key, serializeAsValue(value))

    override fun parseValue(value: String) = parseValue(value)

    override fun serializeAsValue(value: T) = serializeAsValue(value)

    override val name: String = T::class.simpleName ?: super.name
}*/