package com.lanlinju.animius.data.remote.dandanplay

import com.lanlinju.animius.data.remote.dandanplay.dto.DandanplayDanmaku
import com.lanlinju.animius.data.remote.dandanplay.dto.DandanplayDanmakuListResponse
import com.lanlinju.animius.data.remote.dandanplay.dto.DandanplaySearchEpisodeResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType

class DandanplayClient(
    private val client: HttpClient,
) {

    suspend fun searchEpisode(
        subjectName: String,
        episodeName: String?,
    ): DandanplaySearchEpisodeResponse {
        val response = client.get("https://api.dandanplay.net/api/v2/search/episodes") {
            accept(ContentType.Application.Json)
            parameter("anime", subjectName)
            parameter("episode", episodeName)
        }

        return response.body<DandanplaySearchEpisodeResponse>()
    }

    suspend fun getDanmakuList(
        episodeId: Long,
    ): List<DandanplayDanmaku> {
        val chConvert = 0
        val response =
            client.get("https://api.dandanplay.net/api/v2/comment/${episodeId}?chConvert=$chConvert&withRelated=true") {
                accept(ContentType.Application.Json)
            }.body<DandanplayDanmakuListResponse>()

        return response.comments
    }
}