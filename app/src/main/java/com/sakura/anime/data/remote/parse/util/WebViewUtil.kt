package com.sakura.anime.data.remote.parse.util

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import com.sakura.anime.application.AnimeApplication
import com.sakura.anime.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("StaticFieldLeak", "SetJavaScriptEnabled")
class WebViewUtil {
    companion object {
        private val LOG_TAG = "WebViewUtil"
    }

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
        userAgent: String? = null
    ): String = withContext(Dispatchers.Main) {

        createWebView(userAgent)

        var hasResume = false

        suspendCancellableCoroutine { continuation ->

            continuation.invokeOnCancellation {
                "invokeOnCancellation".log(LOG_TAG)
                destroyWebView()
            }

            webView?.webViewClient = object : BlockedResWebViewClient() {
                override fun onLoadResource(view: WebView?, requestUrl: String) {

                    if (hasResume || requestUrl.containStrs(*filterRequestUrl)) return

                    requestUrl.log(LOG_TAG, "interceptRequest")
                    launch(Dispatchers.Default) {
                        if (requestUrl.contains(regex.toRegex()) || predicate(requestUrl)) {
                            requestUrl.log(LOG_TAG, "interceptRequest: regex resume")
                            hasResume = true
                            continuation.resume(requestUrl)
                        }
                    }
                }
            }

            webView?.loadUrl(url) // 加载视频播放所在的Web网页

            launch(Dispatchers.Main) {
                var elapsedTime = 0
                while (elapsedTime < timeoutMs && !hasResume && isActive) {
                    delay(200)
                    elapsedTime += 200
                    //elapsedTime.log(LOG_TAG, "elapsed")
                }

                destroyWebView()

                if (!hasResume) {
                    "resumeWithException".log(LOG_TAG)
                    continuation.resumeWithException(SocketTimeoutException("webView connection timeout exception"))
                }
            }
        }
    }

    abstract class BlockedResWebViewClient(
        private val blockRes: Array<String> = arrayOf(
            ".css", ".ts",
            ".mp3", ".m4a",
            ".gif", ",jpg", ".png", ".webp"
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
            val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
            if (blockRes.any { url.contains(it) }) {
                view.post {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        view.webViewClient.onLoadResource(view, url)
                    }
                }
                blockWebResourceRequest
            }
            super.shouldInterceptRequest(view, request)
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
        "destroyWebView".log(LOG_TAG)
    }

    fun clearWeb() {
//        createWebView()
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