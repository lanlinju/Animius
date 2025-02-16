package com.sakura.anime.util

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider.getUriForFile
import coil.ImageLoader
import com.sakura.anime.BuildConfig
import com.sakura.anime.application.AnimeApplication
import com.sakura.anime.data.remote.parse.AnimeSource
import com.sakura.download.utils.decrypt
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


/**
 * 先Base64解码数据，然后再AES解密
 */
fun AnimeSource.decryptData(data: String, key: String, iv: String): String {
    // 解码 Base64 编码的数据
    val bytes = Base64.decode(data, Base64.DEFAULT)

    // 进行解密
    val debytes = bytes.decrypt(key, iv)

    // 将解密后的字节数组转换为字符串
    return debytes.toString(Charsets.UTF_8)
}

private fun ByteArray.decrypt(key: String, iv: String): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
    val ivSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))

    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    return cipher.doFinal(this)
}

val AnimeSource.preferences: SharedPreferences
    get() = AnimeApplication.getInstance().preferences

/**
 * 获取默认的动漫域名
 */
fun AnimeSource.getDefaultDomain(): String {
    return preferences.getString(KEY_SOURCE_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN
}

/*
fun getVersionName(context: Context): String {
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName
}*/

fun Context.installApk(file: File) {
    val intent = Intent(ACTION_VIEW)
    val authority = "$packageName.provider"
    val uri = getUriForFile(this, authority, file)
    intent.setDataAndType(uri, "application/vnd.android.package-archive")
    intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(intent)
}

fun <T> T.log(tag: String, prefix: String = ""): T {
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

val isAndroidTV: Boolean = isAndroidTV(AnimeApplication.getInstance())

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

/**
 * Network util
 */
fun createDefaultHttpClient(
    clientConfig: HttpClientConfig<*>.() -> Unit = {},
) = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(1, TimeUnit.MINUTES)
//            followRedirects(true)
//            followSslRedirects(true)
            sslSocketFactory(createSSLSocketFactory(), TrustAllCerts())
            hostnameVerifier { _, _ -> true }
        }
    }
    install(HttpTimeout) {
        socketTimeoutMillis = 30_000L
        connectTimeoutMillis = 30_000L
    }
    clientConfig()
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun createSSLSocketFactory(): SSLSocketFactory {
    return runCatching {
        SSLContext.getInstance("TLS").let {
            it.init(null, arrayOf(TrustAllManager()), SecureRandom())
            it.socketFactory
        }
    }.getOrElse {
        throw it
    }
}

class TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return emptyArray()
    }
}

class TrustAllCerts : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate?> {
        return arrayOfNulls(0)
    }
}

/*@SuppressLint("CustomX509TrustManager")
fun initUntrustImageLoader(): ImageLoader {
    return UntrustImageLoader
}*/

val UntrustImageLoader: ImageLoader by lazy {
    // Create a trust manager that does not validate certificate chains
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    })

    // Install the all-trusting trust manager
    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, java.security.SecureRandom())

    // Create an ssl socket factory with our all-trusting manager
    val sslSocketFactory = sslContext.socketFactory

    val client = OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }.build()


    ImageLoader.Builder(AnimeApplication.getInstance())
        .okHttpClient(client)
        .build()
}
