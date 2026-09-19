package com.lanlinju.animius.presentation.screen.captcha

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lanlinju.animius.data.remote.parse.util.CaptchaCookieManager
import com.lanlinju.animius.presentation.theme.AnimeTheme
import com.lanlinju.animius.util.focus.rememberIsFocused

class CaptchaWebViewActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_COOKIES = "extra_cookies"

        fun createIntent(context: Context, url: String): Intent {
            return Intent(context, CaptchaWebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }

        setContent {
            AnimeTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("验证码验证") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    CaptchaWebViewContent(
                        url = url,
                        onVerificationComplete = { cookies ->
                            // 保存 Cookie 到本地存储
                            CaptchaCookieManager.saveCookies(
                                CaptchaCookieManager.CUR_KEY_COOKIE,
                                cookies
                            )
                            val resultIntent = Intent().apply {
                                putExtra(EXTRA_COOKIES, cookies)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                        onCancel = {
                            setResult(RESULT_CANCELED)
                            finish()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaWebViewContent(
    url: String,
    onVerificationComplete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val buttonFocusRequester = remember { FocusRequester() }
    val (isButtonFocused, buttonFocusModifier) = rememberIsFocused()
    // 用 View 级别的焦点监听,Compose 的 onFocusChanged 无法正确捕获 WebView 内部焦点
    var isWebViewFocused by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // 焦点流转: WebView 焦点时按返回 -> 转移到按钮; 按钮焦点时按返回 -> 退出页面
    BackHandler(enabled = isWebViewFocused) {
        buttonFocusRequester.requestFocus()
    }
    BackHandler(enabled = isButtonFocused) {
        onCancel()
    }

    Column(modifier = modifier) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 先让 WebView 获得焦点，否则 JS 的 input.focus() 会失效
                            view?.requestFocus()
                            // 轮询检测元素出现后立即操作，比固定延迟更快更可靠
                            view?.evaluateJavascript("""
                                (function() {
                                    var tries = 0;
                                    function clickAnnouncement() {
                                        var els = document.querySelectorAll('button, a');
                                        for (var i = 0; i < els.length; i++) {
                                            var t = (els[i].textContent || '').trim();
                                            if (t.indexOf('我已了解') >= 0 || t.indexOf('知道了') >= 0 || t === '确定') {
                                                els[i].click();
                                                return true;
                                            }
                                        }
                                        return false;
                                    }
                                    function focusInput() {
                                        var input = document.querySelector('input[name=verify]');
                                        if (input) { input.focus(); input.click(); return true; }
                                        return false;
                                    }
                                    function poll() {
                                        tries++;
                                        if (tries > 50) return; // 最多轮询 5 秒
                                        if (!clickAnnouncement() || !focusInput()) {
                                            setTimeout(poll, 100);
                                        }
                                    }
                                    poll();
                                })();
                            """.trimIndent(), null)
                        }
                    }

                    // 用 View 级别的焦点监听
                    setOnFocusChangeListener { _, hasFocus ->
                        isWebViewFocused = hasFocus
                    }

                    loadUrl(url)
                    webView = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // 用户完成验证码后点击此按钮
        Button(
            onClick = {
                val currentUrl = webView?.url ?: url
                val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                onVerificationComplete(cookies)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(buttonFocusRequester)
                .then(buttonFocusModifier)
                .border(
                    width = if (isButtonFocused) 3.dp else 0.dp,
                    color = if (isButtonFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.medium
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("已完成验证")
        }
    }
}