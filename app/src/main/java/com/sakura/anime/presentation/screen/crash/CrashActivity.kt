package com.sakura.anime.presentation.screen.crash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sakura.anime.MainActivity
import com.sakura.anime.presentation.theme.AnimeTheme

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 获取传递的崩溃日志
        val crashLog = intent.getStringExtra("crash_log") ?: "No crash log available"

        setContent {
            AnimeTheme {
                CrashScreen(
                    crashLog = crashLog,
                    onRestartClick = {
                        finishAffinity()
                        startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                    },
                )
            }
        }
    }
}