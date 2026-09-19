package com.lanlinju.animius.presentation.screen.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.anime.danmaku.api.Danmaku
import com.anime.danmaku.api.DanmakuLocation
import com.anime.danmaku.api.DanmakuPresentation
import com.anime.danmaku.ui.DanmakuConfig
import com.anime.danmaku.ui.DanmakuStyle
import com.anime.danmaku.ui.StyledDanmaku
import com.lanlinju.animius.R
import com.lanlinju.animius.presentation.screen.settings.DanmakuConfigData.Companion.Default
import com.lanlinju.animius.util.KEY_DANMAKU_CONFIG_DATA
import com.lanlinju.animius.util.KEY_DANMAKU_ENABLED
import com.lanlinju.animius.util.rememberPreference
import com.lanlinju.animius.util.focus.focusedIconButtonColors
import com.lanlinju.animius.util.focus.rememberIsFocused
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanmakuSettingsScreen(onBackClick: () -> Unit = {}) {
    val topBarBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.danmaku_settings)) },
                scrollBehavior = topBarBehavior,
                navigationIcon = {
                    val (backFocused, backModifier) = rememberIsFocused()
                    IconButton(
                        onClick = onBackClick,
                        colors = focusedIconButtonColors(backFocused),
                        modifier = backModifier
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = if (backFocused) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            val defaultDanmakuConfig = remember { DanmakuConfig.Default }
            val defaultDanmakuStyle = remember { DanmakuStyle.Default }
            var danmakuEnabled by rememberPreference(KEY_DANMAKU_ENABLED, false)
            var config by rememberPreference(
                KEY_DANMAKU_CONFIG_DATA,
                Default,
                DanmakuConfigData.serializer()
            )
            // Enable Danmaku Switch
            SwitchItem(
                title = stringResource(R.string.enable_danmaku),
                checked = danmakuEnabled,
                onCheckedChange = { danmakuEnabled = it }
            )

            // Danmaku Settings Switches
            DanmakuSwitches(config) { updatedConfig ->
                config = updatedConfig
            }

            // Preview Styled Danmaku
            DanmakuFontPreview(config = config.toDanmakuConfig())

            // Danmaku Sliders
            DanmakuSliders(config, defaultDanmakuConfig, defaultDanmakuStyle) { updatedConfig ->
                config = updatedConfig
            }

            // Reset to Default Button
            ResetButton {
                config = Default
            }
        }
    }
}

@Composable
fun DanmakuFontPreview(
    config: DanmakuConfig,
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .zIndex(1f)
    ) {
        val styledDanmaku = StyledDanmaku(
            presentation = DanmakuPresentation(
                Danmaku(
                    UUID.randomUUID().toString(),
                    "dummy",
                    0L, "1",
                    DanmakuLocation.NORMAL, context.getString(R.string.danmaku_font_preview), 0,
                ),
                isSelf = false
            ),
            measurer = textMeasurer,
            baseStyle = baseStyle,
            style = config.style,
            enableColor = false,
            isDebug = false
        )
        with(styledDanmaku) {
            draw(
                screenPosX = { (size.width - styledDanmaku.danmakuWidth) / 2f },
                screenPosY = { (size.height - styledDanmaku.danmakuHeight) / 2f }
            )
        }
    }
}

@Composable
fun ResetButton(onReset: () -> Unit) {
    val (isFocused, focusModifier) = rememberIsFocused()
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .then(focusModifier),
        onClick = onReset,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text = stringResource(R.string.reset_default))
    }
}

@Composable
fun DanmakuSwitches(config: DanmakuConfigData, onConfigChange: (DanmakuConfigData) -> Unit) {
    listOf(
        "顶部弹幕" to config.enableTop,
        "滚动弹幕" to config.enableFloating,
        "底部弹幕" to config.enableBottom,
        "弹幕颜色" to config.enableColor
    ).forEach { (title, checked) ->
        SwitchItem(
            title = title,
            checked = checked,
            onCheckedChange = { newValue ->
                onConfigChange(
                    when (title) {
                        "顶部弹幕" -> config.copy(enableTop = newValue)
                        "滚动弹幕" -> config.copy(enableFloating = newValue)
                        "底部弹幕" -> config.copy(enableBottom = newValue)
                        "弹幕颜色" -> config.copy(enableColor = newValue)
                        else -> config
                    }
                )
            }
        )
    }
}

@Composable
fun DanmakuSliders(
    config: DanmakuConfigData,
    defaultDanmakuConfig: DanmakuConfig,
    defaultDanmakuStyle: DanmakuStyle,
    onConfigChange: (DanmakuConfigData) -> Unit
) {
    var alpha by remember(config) { mutableStateOf(config.alpha) }
    SliderItem(
        title = stringResource(R.string.danmaku_opacity),
        valueLabel = "${(alpha * 100).roundToInt()}%",
        value = alpha,
        onValueChange = { alpha = it },
        onValueChangeFinished = {
            onConfigChange(config.copy(alpha = alpha))
        },
        dpadStep = 0.05f
    )

    var fontSize by remember(config) { mutableFloatStateOf(config.fontSize / defaultDanmakuStyle.fontSize.value) }
    SliderItem(
        title = stringResource(R.string.danmaku_font_size),
        value = fontSize,
        onValueChange = { fontSize = it },
        valueRange = 0.50f..2f,
        onValueChangeFinished = {
            onConfigChange(
                config.copy(fontSize = fontSize * defaultDanmakuStyle.fontSize.value)
            )
        },
        valueLabel = "${(fontSize * 100).roundToInt()}%",
        dpadStep = 0.05f
    )

    var strokeWidth by remember(config) {
        mutableFloatStateOf(config.strokeWidth / defaultDanmakuStyle.strokeWidth)
    }
    SliderItem(
        value = strokeWidth,
        onValueChange = {
            strokeWidth = it
        },
        onValueChangeFinished = {
            onConfigChange(config.copy(strokeWidth = strokeWidth * defaultDanmakuStyle.strokeWidth))
        },
        valueRange = 0f..2f,
        title = stringResource(R.string.danmaku_stroke_width),
        valueLabel = "${(strokeWidth * 100).roundToInt()}%",
        dpadStep = 0.1f
    )

    var fontWeight by remember(config) { mutableStateOf(config.fontWeight.toFloat()) }
    SliderItem(
        value = fontWeight,
        onValueChange = {
            fontWeight = it
        },
        onValueChangeFinished = {
            onConfigChange(config.copy(fontWeight = fontWeight.toInt()))
        },
        valueRange = 100f..900f,
        title = stringResource(R.string.danmaku_font_weight),
        valueLabel = "${fontWeight.toInt()}",
    )

    var speed by remember(config) {
        mutableFloatStateOf(config.speed / defaultDanmakuConfig.baseSpeed)
    }
    SliderItem(
        value = speed,
        onValueChange = { speed = it },
        onValueChangeFinished = {
            onConfigChange(config.copy(speed = speed * defaultDanmakuConfig.baseSpeed))
        },
        valueRange = 0.2f..3f,
        title = stringResource(R.string.danmaku_speed),
        valueLabel = "${(speed * 100).roundToInt()}%",
        dpadStep = 0.1f
    )

    var displayArea by remember(config) {
        mutableFloatStateOf(
            when (config.displayArea) {
                0.125f -> 1f
                0.25f -> 2f
                0.50f -> 3f
                0.75f -> 4f
                1f -> 5f
                else -> 2f
            },
        )
    }
    SliderItem(
        value = displayArea,
        onValueChange = {
            displayArea = it
        },
        onValueChangeFinished = {
            val c = config.copy(
                displayArea = when (displayArea) {
                    1f -> 0.125f
                    2f -> 0.25f
                    3f -> 0.50f
                    4f -> 0.75f
                    5f -> 1f
                    else -> 0.25f
                },
            )
            onConfigChange(c)
        },
        valueRange = 1f..5f,
        steps = 3,
        title = stringResource(R.string.danmaku_display_area),
        valueLabel = when (displayArea) {
            1f -> "1/8 屏"
            2f -> "1/4 屏"
            3f -> "半屏"
            4f -> "3/4 屏"
            else -> "全屏"
        }
    )
}

@Composable
fun SwitchItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SwitchPref(
        title = title,
        checked = checked,
        titleStyle = MaterialTheme.typography.titleMedium,
        onCheckedChange = onCheckedChange
    )
}

@Composable
fun SliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    valueLabel: String = "",
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    dpadStep: Float = Float.NaN,
) {
    val (isFocused, focusModifier) = rememberIsFocused()
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .then(focusModifier),
        colors = ListItemDefaults.colors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.surfaceVariant
            else Color.Transparent
        ),
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = if (isFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val range = valueRange.endInclusive - valueRange.start
                        val step = if (!dpadStep.isNaN()) dpadStep
                        else if (steps > 0) range / (steps + 1)
                        else range / 20f
                        when (event.key) {
                            Key.DirectionRight -> {
                                onValueChange((value + step).coerceIn(valueRange))
                                onValueChangeFinished?.invoke()
                                true
                            }
                            Key.DirectionLeft -> {
                                onValueChange((value - step).coerceIn(valueRange))
                                onValueChangeFinished?.invoke()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
            )
        },
    )
}

@Serializable
data class DanmakuConfigData(
    val enableTop: Boolean = true,
    val enableFloating: Boolean = true,
    val enableBottom: Boolean = false,
    val enableColor: Boolean = true,
    val speed: Float = DanmakuConfig.Default.baseSpeed,
    val safeSeparation: Float = DanmakuConfig.Default.safeSeparation.value,
    val displayArea: Float = DanmakuConfig.Default.displayArea,
    val alpha: Float = DanmakuConfig.Default.style.alpha,
    val fontSize: Float = DanmakuConfig.Default.style.fontSize.value,
    val fontWeight: Int = DanmakuConfig.Default.style.fontWeight.weight,
    val strokeWidth: Float = DanmakuConfig.Default.style.strokeWidth,
    // val strokeColor: ULong = DanmakuConfig.Default.style.strokeColor.value,
    // val isDebug: Boolean = false,
) {
    companion object {
        val Default = DanmakuConfigData()
    }

    fun toDanmakuConfig(): DanmakuConfig {
        return DanmakuConfig(
            enableTop = enableTop,
            enableFloating = enableFloating,
            enableBottom = enableBottom,
            enableColor = enableColor,
            displayArea = displayArea,
            baseSpeed = speed,
            style = DanmakuStyle.Default.copy(
                alpha = alpha,
                fontSize = fontSize.sp,
                fontWeight = FontWeight(fontWeight),
                strokeWidth = strokeWidth
            ),
        )
    }
}
