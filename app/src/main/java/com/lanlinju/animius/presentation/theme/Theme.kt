package com.lanlinju.animius.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.lanlinju.animius.util.SettingsPreferences
import com.lanlinju.animius.util.getSchemeFromSeed

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

const val DISABLED_ALPHA = .38f
const val SECONDARY_ALPHA = .78f

private val DefaultShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Immutable
class Padding {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val extraLarge = 32.dp
}

private val DefaultPadding = Padding()

val MaterialTheme.padding: Padding get() = DefaultPadding

@Composable
fun AnimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customColorScheme: ColorScheme = LightColorScheme,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    pureBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> customColorScheme
    }.let { scheme ->
        if (pureBackground) {
            val bgColor = if (darkTheme) Color(0xFF121212) else Color.White
            scheme.copy(
                background = bgColor,
                surface = bgColor
            )
        } else scheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as Activity
            val insetsController = WindowCompat.getInsetsController(activity.window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DefaultShapes,
        content = content
    )
}

@Composable
fun AnimeTheme(content: @Composable () -> Unit) {
    val themeModeState by SettingsPreferences.themeMode.collectAsState()
    val customColor by SettingsPreferences.customColor.collectAsState()
    val dynamicColor by SettingsPreferences.dynamicColor.collectAsState()
    val pureBackground by SettingsPreferences.pureBackground.collectAsState()

    val darkTheme = when (themeModeState) {
        SettingsPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        SettingsPreferences.ThemeMode.DARK -> true
        else -> false
    }

    AnimeTheme(
        darkTheme = darkTheme,
        customColorScheme = getSchemeFromSeed(customColor, darkTheme),
        dynamicColor = dynamicColor,
        pureBackground = pureBackground,
        content = content
    )
}