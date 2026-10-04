package com.lanlinju.animius.parse.cycanime

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanlinju.animius.data.remote.parse.CycanimeSource
import com.lanlinju.animius.data.remote.parse.CycanimeSource.LOGIN_TOKEN_SCRIPT
import com.lanlinju.animius.data.remote.parse.util.SourceAuthManager
import com.lanlinju.animius.data.remote.parse.util.WebAuthSession
import com.lanlinju.animius.util.SourceHolder
import com.lanlinju.animius.util.SourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

/**
 * 次元城登录链路回归测试(不需要真实账号)。
 *
 * 验证三件最容易写错的事:
 *  1. 未登录时播放失败会把"去登录"请求挂到 [SourceAuthManager.requestWebAuth]
 *  2. [LOGIN_TOKEN_SCRIPT] 能从站点真实的 Web Storage 结构里取出 token 与过期时间(空会话也不崩)
 *  3. token 按数据源隔离保存/读取
 *
 * 这些用例需要 WebView / Context / 真实网络,因此只能在设备上跑;
 * 不依赖 Android 的部分(如 Bearer 前缀规范化)见 JVM 单测
 * `app/src/test/.../parse/cycanime/CycanimeSourceTest.kt`。
 */
@RunWith(AndroidJUnit4::class)
class CycaniLoginFlowTest {

    private val tag = "CYC-LOGIN"

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /** 站点真实的会话结构(2026-09-15 实测)：token 自带 Bearer 前缀，expiresAt 为 ISO 字符串 */
    private val sampleSession = """
        {"version":2,"scope":"scope-1","token":"Bearer TEST_TOKEN_123","expiresAt":"2026-09-22T00:42:18.332566444+08:00"}
    """.trimIndent()

    /** [LOGIN_TOKEN_SCRIPT] 的预期返回值：token 与过期时间用 `|` 分隔 */
    private val samplePayload = "Bearer TEST_TOKEN_123|2026-09-22T00:42:18.332566444+08:00"

    @Test
    fun tokenScriptReadsSession() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        withContext(Dispatchers.Main) {
            @SuppressLint("SetJavaScriptEnabled")
            val web = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = desktopUa
            }
            // 必须先落在站点的 origin 上,localStorage 才是同源可读的
            web.loadUrl("https://www.cycani.org/")
            var loaded = false
            for (i in 1..40) {
                kotlinx.coroutines.delay(500)
                if (web.url?.startsWith("https://www.cycani.org") == true) { loaded = true; break }
            }
            Log.i(tag, "页面已加载=$loaded url=${web.url}")
            assertTrue("无法加载次元城页面", loaded)

            // 用假的 localStorage/sessionStorage 覆盖脚本里的同名自由变量,
            // 从而独立验证脚本逻辑,不受站点自身(会在内存里恢复会话)的干扰。
            suspend fun runWith(lsValue: String?, ssValue: String?): String {
                fun storage(v: String?) =
                    "({getItem:function(){return ${v?.let { jsString(it) } ?: "null"};}})"
                val js = "(function(localStorage, sessionStorage){ return $LOGIN_TOKEN_SCRIPT; })" +
                    "(${storage(lsValue)},${storage(ssValue)})"
                return web.eval(js)
            }

            // 1) 空会话 -> 空串
            Log.i(tag, "空会话 => [${runWith(null, null)}]")
            assertEquals("空会话应返回空", "", runWith(null, null))

            // 2) localStorage 的 v2 会话 -> 取到 token 与过期时间
            Log.i(tag, "localStorage v2 => [${runWith(sampleSession, null)}]")
            assertEquals(samplePayload, runWith(sampleSession, null))

            // 3) 只有 sessionStorage 的 v1 会话(未勾选"保持登录")-> 同样能取到
            Log.i(tag, "sessionStorage v1 => [${runWith(null, sampleSession)}]")
            assertEquals(samplePayload, runWith(null, sampleSession))

            // 4) 会话里没有 expiresAt -> 仍返回 token，过期时间留空(协议里允许省略)
            val noExpiry = """{"version":2,"token":"Bearer TEST_TOKEN_123"}"""
            Log.i(tag, "缺 expiresAt => [${runWith(noExpiry, null)}]")
            assertEquals("Bearer TEST_TOKEN_123|", runWith(noExpiry, null))

            // 5) 坏数据 -> 不崩溃,返回空
            Log.i(tag, "坏数据 => [${runWith("not-json", null)}]")
            assertEquals("坏数据应返回空", "", runWith("not-json", null))

            // 6) 会话里没有 token 字段 -> 空串
            Log.i(tag, "缺 token 字段 => [${runWith("""{"version":2,"scope":"s"}""", null)}]")
            assertEquals("", runWith("""{"version":2,"scope":"s"}""", null))

            web.destroy()
        }
    }

    @Test
    fun playRequiresLoginWhenNoToken() = runBlocking<Unit> {
        SourceHolder.switchSource(SourceMode.Cycanime)
        SourceAuthManager.consumePendingWebAuth()

        // 该断言只在"未登录"时成立。若设备上已保存 token(用户在 App 里登录过)，
        // 播放会直接成功——此时改为验证登录态确实生效，避免测试被环境状态卡住。
        val savedToken = SourceAuthManager.getToken()
        if (savedToken.isNotEmpty()) {
            Log.i(tag, "已保存 token(长度=${savedToken.length}),验证登录态生效")
            val ok = runCatching {
                withTimeout(30_000L) { CycanimeSource.getVideoData("/api/v2/sections/50650/play-url") }
            }
            Log.i(tag, "已登录播放 => 成功=${ok.isSuccess} url=${ok.getOrNull()?.videoUrl?.take(80)}")
            assertTrue("已登录时应当能取到播放地址", ok.isSuccess)
            assertTrue("已登录时不应再要求登录", SourceAuthManager.consumePendingWebAuth() == null)
            return@runBlocking
        }

        val result = runCatching {
            withTimeout(30_000L) { CycanimeSource.getVideoData("/api/v2/sections/50650/play-url") }
        }
        Log.i(tag, "未登录播放 => 失败=${result.isFailure} 原因=${result.exceptionOrNull()?.message}")

        val pending = SourceAuthManager.consumePendingWebAuth()
        assertNotNull("未登录时应挂起登录请求", pending)
        Log.i(tag, "pendingWebAuth url=${pending!!.url} title=${pending.title} hasScript=${pending.tokenScript.isNotEmpty()}")
        assertTrue("登录地址应以 /login 结尾", pending.url.endsWith("/login"))
        assertTrue("登录模式必须带 tokenScript", pending.tokenScript.isNotEmpty())

        SourceAuthManager.consumePendingWebAuth()
    }

    @Test
    fun tokenIsolatedPerSource() {
        SourceHolder.switchSource(SourceMode.Cycanime)
        // 注意:必须备份并还原,否则会把用户真实登录的 token 删掉
        val backup = SourceAuthManager.getToken()
        val backupExpiresAt = SourceAuthManager.getExpiresAt()
        try {
            SourceAuthManager.clearToken()
            assertEquals("", SourceAuthManager.getToken())

            SourceAuthManager.saveSession(WebAuthSession("CYC_TOKEN", "2026-09-22T00:42:18+08:00"))
            assertEquals("CYC_TOKEN", SourceAuthManager.getToken())
            assertEquals("2026-09-22T00:42:18+08:00", SourceAuthManager.getExpiresAt())

            // 切到其他数据源,读到的应是各自的 token(空),互不影响
            SourceHolder.switchSource(SourceMode.Agedm)
            assertEquals("不同数据源不应共享 token", "", SourceAuthManager.getToken())

            SourceHolder.switchSource(SourceMode.Cycanime)
            assertEquals("CYC_TOKEN", SourceAuthManager.getToken())
            assertEquals("2026-09-22T00:42:18+08:00", SourceAuthManager.getExpiresAt())
        } finally {
            // 还原用户原本的登录态
            SourceAuthManager.clearToken()
            if (backup.isNotEmpty()) {
                SourceAuthManager.saveSession(WebAuthSession(backup, backupExpiresAt))
            }
        }

        assertEquals("测试不应改动用户的登录态", backup, SourceAuthManager.getToken())
    }

    private suspend fun WebView.eval(js: String): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                evaluateJavascript(js) { value ->
                    if (cont.isActive) cont.resume(value.parseJsString())
                }
            }
        }

    /** evaluateJavascript 返回的是 JSON 编码字符串,去掉外层引号 */
    private fun String?.parseJsString(): String {
        val raw = this ?: return ""
        if (raw == "null" || raw.length < 2) return ""
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1)
        } else raw
    }

    /** 把字符串安全地嵌进 JS 字面量 */
    private fun jsString(s: String): String =
        "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"
}
