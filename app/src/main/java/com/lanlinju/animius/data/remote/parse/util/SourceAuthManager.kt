package com.lanlinju.animius.data.remote.parse.util

import android.content.Context
import androidx.core.content.edit
import com.lanlinju.animius.application.AnimeApplication
import com.lanlinju.animius.util.SourceHolder

/**
 * 一次网页登录的结果。
 *
 * [expiresAt] 是站点下发的过期时间(ISO-8601,如 `2026-09-22T00:42:18.332566444+08:00`),
 * 站点未提供时为空串——此时无法预判过期,只能等 401 再引导重新登录。
 */
data class WebAuthSession(
    val token: String,
    val expiresAt: String = "",
)

/**
 * 站点 Web 鉴权管理器
 *
 * 有些数据源必须先登录网页才能取到播放地址（如次元城），本类按数据源保存登录结果，
 * 并兼作"通知 UI 拉起网页"的通道。
 *
 * - **Token**：网页登录后站点写入 localStorage/sessionStorage 的 Bearer token，用 [getToken] 读取
 * - **请求**：数据源检测到需要登录时调用 [requestWebAuth]，UI 用 [consumePendingWebAuth] 取走后拉起
 *   [com.lanlinju.animius.presentation.screen.webauth.WebAuthActivity]
 *
 * 数据以 [SourceHolder.currentSourceMode] 为 key 隔离，切换数据源互不影响。
 */
object SourceAuthManager {

    /**
     * 待用户处理的登录请求。
     *
     * 数据源在检测到需要用户介入时通过 [requestWebAuth] 提交，UI 取走后拉起 WebView。
     */
    data class PendingWebAuth(
        /** 需要在 WebView 中打开的网址 */
        val url: String,
        /** 提示标题 */
        val title: String,
        /** 用于从页面读取登录会话的 JS 表达式（返回值见 [parseLoginPayload]） */
        val tokenScript: String
    )

    /** [PendingWebAuth.tokenScript] 返回值中分隔 token 与过期时间的字符 */
    private const val PAYLOAD_SEPARATOR = "|"

    private val CUR_KEY_TOKEN: String
        get() {
            return SourceHolder.currentSourceMode.name + "_Token"
        }

    private val CUR_KEY_EXPIRES_AT: String
        get() {
            return SourceHolder.currentSourceMode.name + "_TokenExpiresAt"
        }

    private val CUR_KEY_SAVED_AT: String
        get() {
            return SourceHolder.currentSourceMode.name + "_TokenSavedAt"
        }

    private const val PREF_NAME = "source_auth"

    /**
     * 待用户处理的登录请求，只能经 [requestWebAuth]/[consumePendingWebAuth] 访问。
     */
    private var pendingWebAuth: PendingWebAuth? = null

    /**
     * 数据源检测到需要登录时调用，把请求交给 UI。
     */
    @Synchronized
    fun requestWebAuth(request: PendingWebAuth) {
        pendingWebAuth = request
    }

    /**
     * UI 取走待处理的登录请求并清空；没有待处理请求时返回 null。
     *
     * 取走即清空必须是原子操作：数据源在后台线程写入、ViewModel 在主线程读取，
     * 分开读写会出现同一个请求被消费两次的竞态。
     */
    @Synchronized
    fun consumePendingWebAuth(): PendingWebAuth? {
        val request = pendingWebAuth
        pendingWebAuth = null
        return request
    }

    private val prefs by lazy {
        AnimeApplication.getInstance().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存当前数据源的登录会话，并记录保存时刻。
     *
     * 记录 [getSavedAt] 是为了在不知道站点有效期具体多长的前提下推算本次会话的总时长，
     * 从而按「剩余不足一半」提前续期——站点调整有效期时无需改代码。
     */
    fun saveSession(session: WebAuthSession) {
        prefs.edit {
            putString(CUR_KEY_TOKEN, session.token)
            putString(CUR_KEY_EXPIRES_AT, session.expiresAt)
            putLong(CUR_KEY_SAVED_AT, System.currentTimeMillis())
        }
    }

    /**
     * 获取当前数据源的登录 token，未登录时返回空串
     */
    fun getToken(): String {
        return prefs.getString(CUR_KEY_TOKEN, "") ?: ""
    }

    /**
     * 获取当前数据源登录会话的过期时间（ISO-8601）；未知时返回空串
     */
    fun getExpiresAt(): String {
        return prefs.getString(CUR_KEY_EXPIRES_AT, "") ?: ""
    }

    /**
     * 获取当前数据源上次保存会话的时刻（epoch millis）；未知时返回 0
     */
    fun getSavedAt(): Long {
        return prefs.getLong(CUR_KEY_SAVED_AT, 0L)
    }

    /**
     * 清除当前数据源的登录态（退出登录）
     */
    fun clearToken() {
        prefs.edit {
            remove(CUR_KEY_TOKEN)
            remove(CUR_KEY_EXPIRES_AT)
            remove(CUR_KEY_SAVED_AT)
        }
    }

    /**
     * 解析 [PendingWebAuth.tokenScript] 的返回值，约定为 `<token>|<expiresAt>`
     * （expiresAt 可省略；token 为空视为没读到，返回 null）。
     *
     * 用 `|` 而不是 JSON 或换行分隔：[com.lanlinju.animius.presentation.screen.webauth.WebAuthActivity]
     * 解码 `evaluateJavascript` 结果时只剥掉 JSON 外层引号、不做反转义——换行会变成字面量
     * `\n`，内层引号会带上反斜杠。token（base64url）与 ISO 时间都不含 `|`，天然不需要转义。
     */
    fun parseLoginPayload(payload: String): WebAuthSession? {
        val token = payload.substringBefore(PAYLOAD_SEPARATOR).trim()
        if (token.isEmpty()) return null
        return WebAuthSession(
            token = token,
            expiresAt = payload.substringAfter(PAYLOAD_SEPARATOR, "").trim(),
        )
    }
}