package com.lanlinju.animius

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.navigation.compose.rememberNavController
import com.lanlinju.animius.presentation.navigation.AnimeNavHost
import com.lanlinju.animius.presentation.navigation.Screen
import com.lanlinju.animius.presentation.screen.crash.CrashActivity
import com.lanlinju.animius.presentation.theme.AnimeTheme
import com.lanlinju.animius.util.getCrashLogInfo
import com.lanlinju.animius.util.logCrashToFile
import dagger.hilt.android.AndroidEntryPoint
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置全局异常捕获处理
        setGlobalExceptionHandler()

        installSplashScreen()
        enableEdgeToEdge()

        //https://github.com/android/compose-samples/issues/1256
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets -> insets }

        setContent {
            AnimeTheme {
                App()
            }
        }
    }

    private fun setGlobalExceptionHandler() {
        if (BuildConfig.DEBUG) return // 调试模式下使用控制台查看崩溃日志
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            logCrashToFile(e)
            launchCrashActivity(e)
        }
    }

    private fun launchCrashActivity(e: Throwable) {
        val crashLog = getCrashLogInfo(e)
        val intent = Intent(this, CrashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("crash_log", crashLog)
        }
        startActivity(intent)
        finish()
        exitProcess(0)
    }
}

@Composable
private fun App(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    AnimeNavHost(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        navController = navController,
        onNavigateToAnimeDetail = { detailUrl, mode ->
            navController.navigate(route = Screen.AnimeDetail(detailUrl, mode))
        },
        onNavigateToVideoPlay = { parameters ->
            navController.navigate(Screen.VideoPlayer(parameters))
        },
        onBackClick = {
            navController.popBackStack()
        },
        onNavigateToHistory = {
            navController.navigate(Screen.HistoryScreen)
        },
        onNavigateToDownload = {
            navController.navigate(Screen.Download)
        },
        onNavigateToDownloadDetail = { detailUrl, title, sourceMode ->
            navController.navigate(Screen.DownloadDetail(detailUrl, title, sourceMode))
        },
        onNavigateToSearch = {
            navController.navigate(Screen.Search)
        },
        onNavigateToAppearance = {
            navController.navigate(Screen.Appearance)
        },
        onNavigateToDanmakuSettings = {
            navController.navigate(Screen.DanmakuSettings)
        },
    )
}



