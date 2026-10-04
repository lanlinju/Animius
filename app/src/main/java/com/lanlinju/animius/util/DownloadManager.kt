package com.lanlinju.animius.util

import com.lanlinju.animius.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.withCharset

/**
 * URL 查询参数编码（UTF-8）。
 *
 * 各数据源搜索关键词都含中文，必须编码后才能拼进 query，统一放在这里避免重复实现。
 */
fun String.encodeForUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

/**
 * Network util
 */
fun createHttpClient(
    clientConfig: HttpClientConfig<*>.() -> Unit = {},
) = HttpClient(OkHttp) {
    install(HttpCookies)
    install(HttpTimeout) {
        requestTimeoutMillis = 300_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    BrowserUserAgent()
    followRedirects = true
    install(HttpRedirect) {
        checkHttpMethod = false
        allowHttpsDowngrade = true
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                message.log("HttpClient")
            }
        }
        // logger 函数本身已被 BuildConfig.DEBUG 拦住(release 下什么都不输出),但 ktor 仍会
        // 先把请求头拼成字符串再交给 logger——次元城的播放请求带着 Authorization: Bearer <token>,
        // release 下这份含令牌的字符串会被白造一次。直接关掉,连拼接都省了。
        level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
    }
    clientConfig()
}

object DownloadManager {
    private val httpClient = createHttpClient()

    /*private const val FAKE_BASE_URL = "http://www.example.com"

    private val client = OkHttpClient.Builder()
//        .addInterceptor(interceptor)
        .readTimeout(1L, TimeUnit.MINUTES)
        .build()

    private fun apiCreator(): Api {
        val retrofit = Retrofit.Builder()
            .baseUrl(FAKE_BASE_URL)
            .client(client)
            .build()
        return retrofit.create(Api::class.java)
    }

    private val api = apiCreator()

    suspend fun request(
        url: String,
        header: Map<String, String> = emptyMap()
    ): Response<ResponseBody> {
        return api.get(url, header)
    }*/

    suspend fun getHtml(url: String, headers: Map<String, String> = emptyMap()): String {
        val html = httpClient.get(url) {
            headers {
                headers.forEach { (key, value) ->
                    append(key, value)
                }
            }
        }.bodyAsText()
        return html
    }

    /**
     * 发送 application/x-www-form-urlencoded 格式的 POST 请求
     */
    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String {
        val body = form.entries.joinToString("&") { (k, v) ->
            "${k.encodeForUrl()}=${v.encodeForUrl()}"
        }
        return httpClient.post(url) {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            headers {
                headers.forEach { (key, value) -> append(key, value) }
            }
            setBody(body)
        }.bodyAsText()
    }

    /**
     * 发送 application/json 格式的 POST 请求。
     *
     * charset 显式写成 utf-8：请求体里会有中文(如稀饭动漫的搜索关键词)，一旦被按 latin-1
     * 发出去，服务端只会回 `400 {"code":"PGRST102","message":"Empty or invalid json"}`——
     * 从异常信息里完全看不出是编码问题。ktor 对 String body 默认就用 UTF-8，这里写死只是
     * 不依赖默认值。
     */
    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        return httpClient.post(url) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.withCharset(Charsets.UTF_8).toString())
            headers {
                headers.forEach { (key, value) -> append(key, value) }
            }
            setBody(body)
        }.bodyAsText()
    }

    /*
        suspend fun getHtml(url: String, headers: Map<String, String> = emptyMap()): String {
            return withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).headers(headers.toHeaders()).get().build()
                val response = client.newCall(request).execute()
                var html: String
                if (response.isSuccessful) {
                    response.body!!.let { body ->
                        html = body.charStream().readText()
                    }
                } else {
                    throw IOException(response.toString())
                }
                html
            }
        }

        private fun Map<String, String>.toHeaders(): Headers {
            val builder = Headers.Builder()
            if (isEmpty()) return builder.build()

            for ((name, value) in this) {
                builder.add(name, value)
            }
            return builder.build()
        }*/
}

/*
interface Api {

    @GET
    @Streaming
    suspend fun get(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>
}

val interceptor = Interceptor { chain: Interceptor.Chain ->
    var request = chain.request()

    if (request.url.toString().contains("silisili")) {
        request = request.newBuilder()
            .addHeader("Cookie", "silisili=on;path=/;max-age=86400")
            .build()
    }

    chain.proceed(request)
}*/



