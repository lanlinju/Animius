package com.sakura.anime.presentation.screen.crash

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sakura.anime.R
import com.sakura.anime.presentation.theme.AnimeTheme
import com.sakura.anime.util.shareCrashLog
import com.sakura.anime.util.toast
import kotlinx.coroutines.launch

@Composable
fun CrashScreen(
    crashLog: String,
    onRestartClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    InfoScreen(
        icon = Icons.Outlined.BugReport,
        headingText = stringResource(R.string.crash_screen_title),
        subtitleText = stringResource(
            R.string.crash_screen_description,
            stringResource(R.string.app_name)
        ),
        acceptText = stringResource(R.string.pref_dump_crash_logs),
        onAcceptClick = {
            scope.launch {
//                CrashLogUtil(context).dumpLogs(exception)
                context.shareCrashLog()
            }
        },
        rejectText = stringResource(R.string.crash_screen_restart_application),
        onRejectClick = onRestartClick,
        logText = stringResource(R.string.copy_crash_log),
        onCopyClick = {
            clipboardManager.setClip(ClipEntry(ClipData.newPlainText("Crash Log", crashLog)))
            context.toast(R.string.crash_log_has_been_copied)
        }
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small)
                .clip(MaterialTheme.shapes.small)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = crashLog,
                modifier = Modifier
                    .padding(all = MaterialTheme.padding.small),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        }
    }
}

//@PreviewLightDark
@Preview
@Composable
private fun CrashScreenPreview() {
    AnimeTheme {
        CrashScreen(RuntimeException("Dummy").stackTraceToString()) {}
    }
}

const val DISABLED_ALPHA = .38f
const val SECONDARY_ALPHA = .78f

class Padding {

    val extraLarge = 32.dp

    val large = 24.dp

    val medium = 16.dp

    val small = 8.dp

    val extraSmall = 4.dp
}

val MaterialTheme.padding: Padding
    get() = Padding()

@Composable
fun InfoScreen(
    icon: ImageVector,
    headingText: String,
    subtitleText: String,
    acceptText: String,
    logText: String,
    onAcceptClick: () -> Unit,
    onCopyClick: (() -> Unit)? = null,
    canAccept: Boolean = true,
    rejectText: String? = null,
    onRejectClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        bottomBar = {
            val strokeWidth = Dp.Hairline
            val borderColor = MaterialTheme.colorScheme.outline
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind {
                        drawLine(
                            borderColor,
                            Offset(0f, 0f),
                            Offset(size.width, 0f),
                            strokeWidth.value,
                        )
                    }
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = canAccept,
                        onClick = onAcceptClick,
                    ) {
                        Text(text = acceptText)
                    }
                    if (onCopyClick != null) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onCopyClick,
                        ) {
                            Text(text = logText)
                        }
                    }
                }
                if (rejectText != null && onRejectClick != null) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRejectClick,
                    ) {
                        Text(text = rejectText)
                    }
                }
            }
        },
    ) { paddingValues ->
        // Status bar scrim
        Box(
            modifier = Modifier
                .zIndex(2f)
                .secondaryItemAlpha()
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .height(paddingValues.calculateTopPadding()),
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(top = 48.dp)
                .padding(horizontal = MaterialTheme.padding.medium),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = MaterialTheme.padding.small)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = headingText,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = subtitleText,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(vertical = MaterialTheme.padding.small),
                style = MaterialTheme.typography.titleSmall,
            )

            content()
        }
    }
}

private fun Modifier.secondaryItemAlpha(): Modifier = this.alpha(SECONDARY_ALPHA)

//@PreviewLightDark
@Preview
@Composable
private fun InfoScaffoldPreview() {
    InfoScreen(
        icon = Icons.Outlined.Newspaper,
        headingText = "Heading",
        subtitleText = "Subtitle",
        acceptText = "Accept",
        logText = "log error",
        onAcceptClick = {},
        rejectText = "Reject",
        onRejectClick = {},
    ) {
        Text("Hello world")
    }
}
/*
class CrashLogUtil(
    private val context: Context,
) {
    suspend fun dumpLogs(exception: Throwable? = null) = withContext(NonCancellable) {
        try {
            val file = context.createFileInCacheDir("anime_crash_logs.txt")

            file.appendText(getDebugInfo() + "\n\n")
            exception?.let { file.appendText("$it\n\n") }

            Runtime.getRuntime().exec("logcat *:E -d -f ${file.absolutePath}").waitFor()

            val uri = file.getUriCompat(context)
            context.startActivity(uri.toShareIntent(context, "text/plain"))
        } catch (e: Throwable) {
            context.toast("Failed to get logs")
        }
    }

    private fun Context.toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    fun getDebugInfo(): String {
        return """
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
        """.trimIndent()
    }

    private fun Context.createFileInCacheDir(name: String): File {
        val file = File(externalCacheDir, name)
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()
        return file
    }

    fun File.getUriCompat(context: Context): Uri {
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", this)
    }

    fun Uri.toShareIntent(context: Context, type: String = "image/*", message: String? = null): Intent {
        val uri = this

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            when (uri.scheme) {
                "http", "https" -> {
                    putExtra(Intent.EXTRA_TEXT, uri.toString())
                }

                "content" -> {
                    message?.let { putExtra(Intent.EXTRA_TEXT, it) }
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            }
            clipData = ClipData.newRawUri(null, uri)
            setType(type)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        return Intent.createChooser(shareIntent, context.getString(R.string.action_share)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
} */