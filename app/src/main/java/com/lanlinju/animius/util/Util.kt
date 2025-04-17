package com.lanlinju.animius.util

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_VIEW
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.FileProvider.getUriForFile
import com.lanlinju.animius.BuildConfig
import com.lanlinju.animius.application.AnimeApplication
import com.lanlinju.animius.data.remote.parse.AnimeSource
import com.lanlinju.download.utils.decrypt
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.util.Base64

/**
 * 先Base64解码数据，然后再AES解密
 */
fun AnimeSource.decryptData(data: String, key: String, iv: String): String {
    val bytes = Base64.getDecoder().decode(data.toByteArray())
    val debytes = bytes.decrypt(key, iv)
    return debytes.decodeToString()
}

val AnimeSource.preferences: SharedPreferences
    get() = AnimeApplication.getInstance().preferences

/**
 * 获取默认的动漫域名
 */
fun AnimeSource.getDefaultDomain(): String {
    return preferences.getString(KEY_SOURCE_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN
}

suspend fun AnimeSource.getDocument(url: String): Document {
    val source = DownloadManager.getHtml(url)
    val document = Jsoup.parse(source)
    return document
}

/*
fun getVersionName(context: Context): String {
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName
}*/

/**
 * 升级安装应用
 */
fun Context.installApk(file: File) {
    val intent = Intent(ACTION_VIEW)
    val authority = "$packageName.provider"
    val uri = getUriForFile(this, authority, file)
    intent.setDataAndType(uri, "application/vnd.android.package-archive")
    intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(intent)
}

/**
 * 分享崩溃日志文件
 */
fun Context.shareCrashLog() {
    val logUri = getCrashLogUri()
    val intent = Intent(ACTION_SEND).apply {
        setDataAndType(logUri, "text/plain")
        putExtra(Intent.EXTRA_STREAM, logUri)
        addFlags(FLAG_ACTIVITY_NEW_TASK)
        addFlags(FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Share crash log"))
}

fun Context.logCrashToFile(e: Throwable) {
    val logFile = File(externalCacheDir, CRASH_LOG_FILE)
    logFile.writeText(getCrashLogInfo(e))
}

fun Context.getCrashLogInfo(e: Throwable): String {
    return "${getDebugInfo(this)}\n\n${e.stackTraceToString()}"
}

private fun getDebugInfo(context: Context): String {
    return """
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
            WebView: ${getWebViewVersion(context)}
        """.trimIndent()
}

private fun getWebViewVersion(context: Context): String {
    val webView = WebView.getCurrentWebViewPackage() ?: return "how did you get here?"
    val pm = context.packageManager
    val label = webView.applicationInfo!!.loadLabel(pm)
    val version = webView.versionName
    return "$label $version"
}

private fun Context.getCrashLogUri(): Uri {
    val logFile = File(externalCacheDir, CRASH_LOG_FILE)
    return getUriForFile(this, "$packageName.provider", logFile)
}

fun Context.toast(@StringRes resId: Int) {
    Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
}

fun <T> T.log(tag: String = "Debug", prefix: String = ""): T {
    val prefixStr = if (prefix.isEmpty()) "" else "[$prefix] "
    if (BuildConfig.DEBUG) {
        if (this is Throwable) {
            Log.w(tag, prefixStr + this.message, this)
        } else {
            Log.d(tag, prefixStr + toString())
        }
    }
    return this
}

fun openExternalPlayer(videoUrl: String) {
    val context = AnimeApplication.getInstance()
    val intent = Intent(ACTION_VIEW)
    intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
    var uri = Uri.parse(videoUrl)
    if (!videoUrl.contains("http")) {
        val authority = "${context.packageName}.provider"
        uri = getUriForFile(context, authority, File(videoUrl))
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
    }
    intent.setDataAndType(uri, "video/*")
    context.startActivity(intent)
}

/**
 * 判断是否为AndroidTV
 * 用于处理AndroidTV的交互，例如遥控器
 */
fun isAndroidTV(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val isTV = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanbackFeature =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return isTV || hasLeanbackFeature
}

// 判断是否为平板或大屏设备
fun isTabletDevice(context: Context): Boolean {
    val configuration = context.resources.configuration
    val screenWidthDp = configuration.smallestScreenWidthDp

    return screenWidthDp >= 600
}

/**
 * 用于处理宽屏设备布局
 */
fun isWideScreen(context: Context): Boolean {
    val configuration = context.resources.configuration
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    return screenWidthDp > screenHeightDp
}