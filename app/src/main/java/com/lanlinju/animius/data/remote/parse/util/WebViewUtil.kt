package com.lanlinju.animius.data.remote.parse.util

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import com.lanlinju.animius.application.AnimeApplication
import com.lanlinju.animius.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

private const val LOG_TAG = "WebViewUtil"

@SuppressLint("StaticFieldLeak", "SetJavaScriptEnabled")
class WebViewUtil {

    private var webView: WebView? = null

    /**
     * 根据正则表达式regex获取视频链接，通过过滤拦截[WebView]所有发送的http url请求
     *
     * @param url 要访问的视频所在网页的url
     * @param regex 通过regex匹配拦截对应视频链接
     * @param predicate 自定义额外的判断条件,当regex不能满足匹配要求时会执行
     * @param filterRequestUrl 过滤不需要匹配的请求url
     * @param timeoutMs 请求超时的时间,单位毫秒
     *
     * @return 返回匹配到的视频链接url，匹配不到结果会报一个[SocketTimeoutException]超时异常
     */
    suspend fun interceptRequest(
        url: String,
        regex: String = ".mp4|.m3u8",
        predicate: suspend (requestUrl: String) -> Boolean = { false },
        filterRequestUrl: Array<String> = arrayOf(),
        timeoutMs: Long = 10_000L,
        userAgent: String? = null,
    ): String = withContext(Dispatchers.Main) {

        createWebView(userAgent)

        var matchedUrl: String? = null

        webView?.webViewClient = object : BlockedResWebViewClient() {
            override fun onLoadResource(view: WebView?, requestUrl: String) {

                // 过滤不需要的请求
                if (filterRequestUrl.any { requestUrl.contains(it) }) {
                    return
                }

                requestUrl.log(LOG_TAG, "InterceptRequest")
                if (requestUrl.contains(regex.toRegex()) || runBlocking { predicate(requestUrl) }) {
                    matchedUrl = requestUrl
                    requestUrl.log(LOG_TAG, "Regex match succeeded")
                }
            }
        }

        webView?.loadUrl(url) // 加载视频播放所在的Web网页

        // 等待匹配结果或超时
        try {
            withTimeout(timeoutMs) {
                while (matchedUrl == null) {
                    delay(100)
                }
            }
            matchedUrl ?: throw TimeoutException("No matching URL found")
        } catch (_: TimeoutCancellationException) {
            throw TimeoutException("Web connection timeout exception")
        } finally {
            destroyWebView()
        }
    }

    private fun createWebView(userAgent: String? = null) {
        destroyWebView()
        webView = WebView(AnimeApplication.getInstance()).apply {
            settings.javaScriptEnabled = true
            if (userAgent != null) {
                settings.userAgentString = userAgent
            }
        }
    }

    private fun destroyWebView() {
        webView?.destroy()
        webView = null
        "DestroyWebView".log(LOG_TAG)
    }

    fun clearWeb() {
        webView?.clear()
        destroyWebView()
    }

    private fun CharSequence.containStrs(vararg strs: CharSequence) =
        strs.find { contains(it) } != null

    private fun WebView.clear() {
        clearCache(true)
        clearHistory()
        clearFormData()
        clearMatches()
    }
}

abstract class BlockedResWebViewClient(
    private val blockRes: Array<String> = arrayOf(
        ".css", ".ts",
        ".mp3", ".m4a",
        ".gif", ".jpg", ".png", ".webp"
    )
) : WebViewClient() {

    private val blockWebResourceRequest =
        WebResourceResponse("text/html", "utf-8", ByteArrayInputStream("".toByteArray()))

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        handler?.proceed()
    }

    // Reference code: https://github.com/RyensX/MediaBox/blob/1aefca13656eada4da2ff515cc9f893f407c53e0/app/src/main/java/com/su/mediabox/plugin/WebUtilImpl.kt#L138
    /**
     * 拦截无关资源文件
     *
     * 注意，该方法运行在线程池内
     */
    @CallSuper
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest?
    ) = run {
        val url = request?.url?.toString() ?: return null
        if (blockRes.any { url.contains(it) }) {
            url.log(LOG_TAG,"BlockedRes")
            view.post { view.webViewClient.onLoadResource(view, url) }
            blockWebResourceRequest
        } else {
            null
        }
    }
}