package com.sakura.anime.data.remote.dandanplay

import com.sakura.anime.BuildConfig
import com.sakura.anime.data.remote.dandanplay.dto.DandanplayDanmaku
import com.sakura.anime.data.remote.dandanplay.dto.DandanplayDanmakuListResponse
import com.sakura.anime.data.remote.dandanplay.dto.DandanplaySearchEpisodeResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodedPath
import java.lang.System.currentTimeMillis
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class DandanplayClient(
    private val client: HttpClient,
    private val appId: String = BuildConfig.DANDANPLAY_APP_ID,
    private val appSecret: String = BuildConfig.DANDANPLAY_APP_SECRET,
) {

    suspend fun searchEpisode(
        subjectName: String,
        episodeName: String?,
    ): DandanplaySearchEpisodeResponse {
        val response = client.get("https://api.dandanplay.net/api/v2/search/episodes") {
            configureTimeout()
            accept(ContentType.Application.Json)
            addAuthorizationHeaders()
            parameter("anime", subjectName)
            parameter("episode", episodeName)
        }

        return response.body<DandanplaySearchEpisodeResponse>()
    }

    suspend fun getDanmakuList(episodeId: Long): List<DandanplayDanmaku> {
        val chConvert = 0
        val response =
            client.get("https://api.dandanplay.net/api/v2/comment/${episodeId}?chConvert=$chConvert&withRelated=true") {
                configureTimeout()
                accept(ContentType.Application.Json)
                addAuthorizationHeaders()
            }.body<DandanplayDanmakuListResponse>()

        return response.comments
    }

    private fun HttpRequestBuilder.addAuthorizationHeaders() {
        val timestamp = currentTimeMillis() / 1000
        header("X-AppId", appId)
        header("X-Timestamp", timestamp)
        header("X-Signature", generateSignature(appId, timestamp, url.encodedPath, appSecret))
    }

    private fun HttpRequestBuilder.configureTimeout() {
        timeout {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun generateSignature(
        appId: String,
        timestamp: Long,
        path: String,
        appSecret: String
    ): String {
        val data = appId + timestamp + path + appSecret
        val hash = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
        return Base64.encode(hash)
    }
}