package com.lanlinju.animius.presentation.screen.webauth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lanlinju.animius.data.remote.parse.util.SourceAuthManager
import com.lanlinju.animius.data.remote.parse.util.WebAuthSession
import com.lanlinju.animius.presentation.theme.AnimeTheme
import com.lanlinju.animius.util.focus.rememberIsFocused
import com.lanlinju.animius.util.isAndroidTV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class WebAuthActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TOKEN_SCRIPT = "extra_token_script"

        /**
         * @param tokenScript 读取登录会话的 JS 表达式（返回值见
         *   [SourceAuthManager.parseLoginPayload]），轮询到 token 发生变化即视为登录完成。
         */
        fun createIntent(
            context: Context,
            url: String,
            title: String,
            tokenScript: String
        ): Intent {
            return Intent(context, WebAuthActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TOKEN_SCRIPT, tokenScript)
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
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "登录"
        val tokenScript = intent.getStringExtra(EXTRA_TOKEN_SCRIPT).orEmpty()

        setContent {
            AnimeTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(title) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    WebAuthWebViewContent(
                        url = url,
                        tokenScript = tokenScript,
                        onLoginComplete = { session ->
                            SourceAuthManager.saveSession(session)
                            setResult(RESULT_OK)
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

/**
 * 文本输入元素选择器(排除勾选框/按钮等不需要软键盘的控件)。
 * 这些元素上按 OK 应当交给网页处理(勾选/点击),而不是弹键盘。
 */
private const val TEXT_ENTRY_SELECTOR =
    "input:not([type=hidden]):not([type=checkbox]):not([type=radio])" +
        ":not([type=button]):not([type=submit]):not([type=reset])" +
        ":not([type=file]):not([type=image]),textarea"

/**
 * TV 上 WebView 里的 D-pad 导航 + 软键盘时机脚本。
 *
 * 实测(真机探针)得出的三条结论:
 *  1. 按键确实到达网页(keyCode 38/40/13),但 Chromium **不会**跟着移动 DOM 焦点,
 *     `document.activeElement` 始终是 BODY → 自己实现焦点移动。
 *  2. 输入连接(onCreateInputConnection)必须存在,否则软键盘永远弹不出来;
 *     但只要存在,Chromium 就会在输入框获得焦点时自动弹键盘。
 *     → 用 `readonly` 抑制自动弹出(只读输入框 Chromium 不会弹键盘;
 *       实测 `inputmode="none"` 在本 WebView 上**无效**);
 *       用户按 OK 时再摘掉只读并重新聚焦,键盘就会弹出。
 *  3. 焦点在输入框上时 OK 键不能交给 Chromium(会吞掉按键并把焦点弄丢到 DIV),
 *     由 App 侧领先消费后调用本脚本的 `__animiusAllowKb()`。
 */
private val TV_NAV_SCRIPT = """
    (function () {
      if (window.__animiusTvNav) return 'already';
      // 触摸设备(mobile/平板)走系统默认触摸弹键盘,不要抑制。
      // Android 侧已经按 isAndroidTV 分流,这里是兜底:TV 盒子误报触摸也能靠 Android 侧强制,
      // mobile 接了键盘/遥控器时也不会被卡住。
      try {
        var coarse = window.matchMedia && window.matchMedia('(pointer: coarse)').matches;
        var touch = ('ontouchstart' in window) || (navigator.maxTouchPoints > 0);
        if (!window.__animiusForceTv && (coarse || touch)) return 'skipped-touch';
      } catch (e) {}
      window.__animiusTvNav = true;

      var TEXT = "$TEXT_ENTRY_SELECTOR";

      function isTextEntry(el) {
        if (!el || !el.matches) return false;
        if (el.isContentEditable) return true;
        try { return el.matches(TEXT); } catch (e) { return false; }
      }

      function isVisible(el) {
        if (!el || el.disabled) return false;
        var r = el.getBoundingClientRect();
        if (r.width < 2 || r.height < 2) return false;
        var s = window.getComputedStyle(el);
        return s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0';
      }

      function focusables() {
        var sel = 'a[href], button, input:not([type=hidden]), select, textarea, [tabindex]:not([tabindex="-1"])';
        return Array.prototype.filter.call(document.querySelectorAll(sel), isVisible);
      }

      // 抑制软键盘:把文本输入框设为只读(Chromium 不会为只读输入框弹键盘)
      function suppressKeyboard() {
        try {
          var list = document.querySelectorAll(TEXT);
          for (var i = 0; i < list.length; i++) {
            if (list[i] !== document.activeElement) {
              list[i].readOnly = true;
              list[i].setAttribute('inputmode', 'none');
            }
          }
        } catch (e) {}
      }

      var skipping = false;

      // 用户按 OK:放开当前输入框的键盘限制并重新聚焦,让 Chromium 弹出键盘
      window.__animiusAllowKb = function () {
        var a = document.activeElement;
        if (!isTextEntry(a)) { suppressKeyboard(); return false; }
        skipping = true;
        try {
          a.readOnly = false;
          a.removeAttribute('readonly');
          a.removeAttribute('inputmode');
          if (a.blur) a.blur();
          if (a.focus) a.focus();
        } finally {
          skipping = false;
        }
        return true;
      };

      // 焦点离开输入框后重新抑制键盘(否则下次聚焦到该框又会自动弹出)
      document.addEventListener('focusout', function (e) {
        if (skipping) return;
        var t = e.target;
        if (isTextEntry(t)) {
          t.readOnly = true;
          t.setAttribute('inputmode', 'none');
        }
      }, true);

      document.addEventListener('focusin', function () {
        suppressKeyboard();
      }, true);

      document.addEventListener('keydown', function (e) {
        var k = e.keyCode;

        // 上下:移动焦点(Chromium 自己不会动,只能自己来)
        if (k === 38 || k === 40) {
          var list = focusables();
          if (!list.length) return;
          var idx = list.indexOf(document.activeElement);
          var target = idx < 0
            ? (k === 40 ? list[0] : list[list.length - 1])
            : list[idx + (k === 40 ? 1 : -1)];
          if (!target) return;                 // 到边界:不阻止,让页面自己滚动
          suppressKeyboard();
          target.focus();
          if (target.scrollIntoView) target.scrollIntoView({block: 'center'});
          e.preventDefault();
          return;
        }

        // 回车/OK:输入框里不要提交表单(交给 App 弹键盘)
        if ((k === 13 || k === 23) && isTextEntry(document.activeElement)) {
          e.preventDefault();
        }
      }, true);

      suppressKeyboard();
      // SPA 动态渲染的输入框也要覆盖
      try {
        new MutationObserver(suppressKeyboard).observe(document.documentElement, {
          childList: true, subtree: true
        });
      } catch (e) {}

      return 'installed';
    })()
""".trimIndent()

/** 判断网页当前聚焦的是否为文本输入元素(返回 JSON true/false) */
private val EDITABLE_FOCUSED_SCRIPT = """
    (function () {
      var a = document.activeElement;
      if (!a) return false;
      if (a.isContentEditable) return true;
      try { return !!(a.matches && a.matches("$TEXT_ENTRY_SELECTOR")); } catch (e) { return false; }
    })()
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebAuthWebViewContent(
    url: String,
    tokenScript: String,
    onLoginComplete: (WebAuthSession) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()
    val buttonFocusRequester = remember { FocusRequester() }
    val (isButtonFocused, buttonFocusModifier) = rememberIsFocused()
    // 用 View 级别的焦点监听,Compose 的 onFocusChanged 无法正确捕获 WebView 内部焦点
    var isWebViewFocused by remember { mutableStateOf(true) }

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
                val tvMode = isAndroidTV(context)
                TvWebView(context, tvMode).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // 次元城等站点会按 UA 判断"Android 设备不支持网页版"并拦截,
                    // 登录页必须用桌面 UA 才能正常渲染
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

                    // 让 WebView 自身可聚焦,且允许页面内元素接管焦点(否则 D-pad 进不来)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

                    // 注意:不能在这里用 setOnKeyListener —— WebView 重写了 dispatchKeyEvent
                    // 直接交给 Chromium,不会走 View 的按键监听器(setOnKeyListener 从不触发)。
                    // 因此按键处理放在 TvWebView.dispatchKeyEvent 里。

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 先让 WebView 获得焦点，否则按键不会送进渲染进程
                            view?.requestFocus()
                            // 非 TV(手机/平板触摸设备):走系统默认触摸弹键盘,不注入抑制脚本。
                            // 否则 readonly + inputmode=none 会让触摸点输入框也弹不出键盘。
                            if (view != null && !isAndroidTV(view.context)) {
                                return
                            }
                            // 页面(或浏览器)可能自动聚焦某个元素,导致加载完就滚到底部、
                            // 焦点停在页面底部的链接上。先清掉自动聚焦并回到顶部,
                            // 再由下面的导航脚本把焦点交给用户操作。
                            view?.evaluateJavascript(
                                "(function(){var a=document.activeElement;" +
                                    "if(a&&a!==document.body){a.blur();}" +
                                    "window.scrollTo(0,0);})()",
                                null
                            )
                            // 注入 D-pad 导航(Chromium 自己不会移动 DOM 焦点)
                            view?.evaluateJavascript("window.__animiusForceTv=true", null)
                            view?.evaluateJavascript(TV_NAV_SCRIPT) { r ->
                                if (r?.contains("installed") != true && r?.contains("already") != true) {
                                    Log.w("WebAuth", "TV 导航脚本注入失败: $r")
                                }
                            }
                        }
                    }

                    // 用 View 级别的焦点监听
                    setOnFocusChangeListener { _, hasFocus ->
                        isWebViewFocused = hasFocus
                    }

                    loadUrl(url)
                    // 尽早请求焦点,避免初始焦点落到下方按钮上
                    requestFocus()
                    webView = this
                }
            },
            // destroy() 必须在 View 从父容器摘除之后调用,onRelease 正是这个时机。
            // 放进 DisposableEffect.onDispose 会在还挂载着的时候销毁,渲染进程/Adapter 泄漏并打 Chromium 警告。
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // 轮询页面上的 token,拿到即自动完成(用户无需点按钮)
        LaunchedEffect(webView) {
            val view = webView ?: return@LaunchedEffect
            // 先记下打开页面时已有的 token(可能来自上次登录,可能已过期)。
            // 只有检测到 token 发生变化才自动完成——否则会拿着旧 token 立刻结束,
            // 服务端仍然 401,用户看到的是"登录了还让去登录"的死循环。
            val initialToken = view.readLoginSession(tokenScript)?.token.orEmpty()
            repeat(600) { // 最多轮询约 10 分钟
                delay(1000)
                val session = view.readLoginSession(tokenScript)
                if (session != null && session.token != initialToken) {
                    view.post { onLoginComplete(session) }
                    return@LaunchedEffect
                }
            }
        }

        // 用户完成登录后点击此按钮
        Button(
            onClick = {
                // 手动点击时重新读一次会话(轮询还没轮到,或用户想立即完成)。
                // 这里必须挂起等待:evaluateJavascript 是异步的,同步取只会拿到空串。
                scope.launch {
                    val session = webView?.readLoginSession(tokenScript)
                    if (session != null) onLoginComplete(session)
                }
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
            Text("已完成登录")
        }
    }
}

/**
 * TV 友好的 WebView。
 *
 * 软键盘时机的实现要点(均由真机探针实测得出):
 *  1. `onCreateInputConnection` **必须**正常返回连接,否则软键盘永远弹不出来
 *     (返回 null 会让 IMM 认为该视图不接受输入,之后 showSoftInput 一律无效)。
 *  2. 自动弹出由网页侧的 `inputmode="none"` 抑制(见 [TV_NAV_SCRIPT]),不是靠拦连接。
 *  3. 焦点在输入框上时,OK 键必须由本类**领先消费**:交给 Chromium 会被吞掉按键并把
 *     输入框焦点弄丢(focusout -> focusin:DIV)。消费后由网页重新聚焦并放开键盘限制。
 */
@SuppressLint("ViewConstructor")
private class TvWebView(context: Context, private val tvMode: Boolean) : WebView(context) {

    private companion object {
        /** 焦点监视间隔:用于同步判断 OK 键是否该被拦截 */
        const val FOCUS_WATCH_INTERVAL_MS = 120L
    }

    /** 页面当前聚焦的是否为文本输入框(由焦点监视器维护,供按键时同步判断) */
    @Volatile
    private var editableFocused = false

    private var focusWatchTask: Runnable? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startWatchingFocus()
    }

    override fun onDetachedFromWindow() {
        stopWatchingFocus()
        super.onDetachedFromWindow()
    }

    /**
     * 按键入口。
     *
     * WebView 自己重写了 dispatchKeyEvent 并把事件直接交给 Chromium,
     * 因此 `setOnKeyListener` 永远不会被调用,必须在子类里拦。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 非 TV 不拦截:触摸设备的软键盘回车/换行/搜索等必须交给 Chromium/IME。
        if (tvMode && isConfirmKey(event.keyCode) && editableFocused) {
            // 等本次按键派发结束后再动:派发过程中操作焦点会打乱 Chromium 状态
            if (event.action == KeyEvent.ACTION_UP) {
                post { allowKeyboardInPage() }
            }
            return true // 不下发:见类注释第 3 条
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 键盘弹出时,返回键只收起键盘,不要冒泡到 Activity(否则会先跳焦到下方按钮、再退出页面)。
     */
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP &&
            inputMethodManager()?.isAcceptingText() == true
        ) {
            hideKeyboard()
            return true // 消费掉:仅收键盘
        }
        return super.onKeyPreIme(keyCode, event)
    }

    private fun isConfirmKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun inputMethodManager(): InputMethodManager? =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    /**
     * 请网页放开当前输入框的键盘限制(摘掉 inputmode=none 并重新聚焦),
     * Chromium 随即会弹出软键盘。返回 false 表示当前焦点不在输入框上。
     */
    private fun allowKeyboardInPage() {
        evaluateJavascript("window.__animiusAllowKb ? window.__animiusAllowKb() : false") { value ->
            if (value != "true") {
                // 脚本未注入时的兜底
                inputMethodManager()?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun hideKeyboard() {
        inputMethodManager()?.hideSoftInputFromWindow(windowToken, 0)
    }

    /**
     * 监视页面焦点,两个作用:
     *  1. 维护 [editableFocused],供 OK 键同步判断
     *  2. 焦点离开输入框时收起键盘,避免键盘一直挂着遮挡页面
     *     (输入框之间切换时两者都可输入,键盘保持)
     */
    private fun startWatchingFocus() {
        // 非 TV 不需要 OK 键同步判断,也不在焦点切换时强制收键盘,避免干扰触摸输入。
        if (!tvMode) return
        if (focusWatchTask != null) return
        val task = object : Runnable {
            override fun run() {
                if (!isAttachedToWindow) return
                evaluateJavascript(EDITABLE_FOCUSED_SCRIPT) { value ->
                    val editable = value == "true"
                    // 焦点离开输入框 -> 收键盘
                    if (!editable && editableFocused && inputMethodManager()?.isAcceptingText() == true) {
                        hideKeyboard()
                    }
                    editableFocused = editable
                    postDelayed(this, FOCUS_WATCH_INTERVAL_MS)
                }
            }
        }
        focusWatchTask = task
        postDelayed(task, FOCUS_WATCH_INTERVAL_MS)
    }

    private fun stopWatchingFocus() {
        focusWatchTask?.let { removeCallbacks(it) }
        focusWatchTask = null
    }
}

/**
 * 执行读取登录会话的 JS 并解析结果(挂起)。
 *
 * 注意:WebView.evaluateJavascript 的回调在主线程派发,因此不能用 CountDownLatch 阻塞主线程
 * (会死锁并永远返回空值),必须用 suspendCancellableCoroutine 等待。
 */
private suspend fun WebView.readLoginSession(tokenScript: String): WebAuthSession? =
    SourceAuthManager.parseLoginPayload(evalJsAwait(tokenScript))

/**
 * 执行 JS 并把结果当作字符串返回(挂起)。
 *
 * 注意:WebView.evaluateJavascript 的回调在主线程派发,因此不能用 CountDownLatch 阻塞主线程
 * (会死锁并永远返回空值),必须用 suspendCancellableCoroutine 等待。
 */
private suspend fun WebView.evalJsAwait(js: String): String =
    withContext(Dispatchers.Main) {
        withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                evaluateJavascript(js) { value ->
                    if (cont.isActive) cont.resume(value.parseJsStringResult())
                }
            }
        } ?: ""
    }

/**
 * evaluateJavascript 会返回 JSON 编码的结果:字符串会带引号(如 "\"abc\""),
 * 空结果为 "\"\""。这里还原成普通字符串。
 */
private fun String?.parseJsStringResult(): String {
    val raw = this ?: return ""
    if (raw == "null" || raw.length < 2) return ""
    return if (raw.startsWith("\"") && raw.endsWith("\"")) {
        raw.substring(1, raw.length - 1)
    } else {
        raw
    }
}