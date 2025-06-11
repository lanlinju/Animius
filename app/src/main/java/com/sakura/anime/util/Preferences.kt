package com.sakura.anime.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

const val KEY_SOURCE_MODE = "animeSourceMode"
const val KEY_HOME_BACKGROUND_URI = "homeBackgroundUri"
const val KEY_AUTO_ORIENTATION_ENABLED = "autoOrientationEnabled"
const val KEY_AUTO_CONTINUE_PLAY_ENABLED = "autoContinuePlayEnabled" // 自动连播（播放结束后自动播放下一个剧集）

// theme
const val KEY_CUSTOM_COLOR = "customColor"
const val KEY_THEME_MODE = "themeMode"
const val KEY_DYNAMIC_COLOR = "dynamicColor"
const val KEY_DYNAMIC_IMAGE_COLOR = "dynamicImageColor"

// danmaku
const val KEY_DANMAKU_ENABLED = "danmakuEnabled"
const val KEY_DANMAKU_CONFIG_DATA = "danmakuConfigData"


fun <T> SharedPreferences.getObject(
    key: String,
    defaultValue: T,
    serializer: KSerializer<T>
): T {
    val json = getString(key, null) ?: return defaultValue
    return try {
        Json.decodeFromString(serializer, json)
    } catch (e: Exception) {
        defaultValue
    }
}

fun <T> SharedPreferences.Editor.putObject(
    key: String,
    value: T,
    serializer: KSerializer<T>
): SharedPreferences.Editor {
    val json = Json.encodeToString(serializer, value)
    return putString(key, json)
}


inline fun <reified T : Enum<T>> SharedPreferences.getEnum(
    key: String,
    defaultValue: T
): T =
    getString(key, null)?.let {
        try {
            enumValueOf<T>(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    } ?: defaultValue

inline fun <reified T : Enum<T>> SharedPreferences.Editor.putEnum(
    key: String,
    value: T
): SharedPreferences.Editor =
    putString(key, value.name)

val Context.preferences: SharedPreferences
    get() = getSharedPreferences("preferences", Context.MODE_PRIVATE)

@Composable
fun rememberPreference(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    return remember {
        mutableStatePreferenceOf(context.preferences.getBoolean(key, defaultValue)) {
            context.preferences.edit { putBoolean(key, it) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Int): MutableState<Int> {
    val context = LocalContext.current
    return remember {
        mutableStatePreferenceOf(context.preferences.getInt(key, defaultValue)) {
            context.preferences.edit { putInt(key, it) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: String): MutableState<String> {
    val context = LocalContext.current
    return remember {
        mutableStatePreferenceOf(context.preferences.getString(key, null) ?: defaultValue) {
            context.preferences.edit { putString(key, it) }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberPreference(key: String, defaultValue: T): MutableState<T> {
    val context = LocalContext.current
    return remember {
        mutableStatePreferenceOf(context.preferences.getEnum(key, defaultValue)) {
            context.preferences.edit { putEnum(key, it) }
        }
    }
}

@Composable
fun <T> rememberPreference(
    key: String,
    defaultValue: T,
    serializer: KSerializer<T>
): MutableState<T> {
    val context = LocalContext.current
    return remember {
        mutableStatePreferenceOf(context.preferences.getObject(key, defaultValue, serializer)) {
            context.preferences.edit { putObject(key, it, serializer) }
        }
    }
}

inline fun <T> mutableStatePreferenceOf(
    value: T,
    crossinline onStructuralInequality: (newValue: T) -> Unit
) = mutableStateOf(
        value = value,
        policy = object : SnapshotMutationPolicy<T> {
            override fun equivalent(a: T, b: T): Boolean {
                val areEquals = a == b
                if (!areEquals) onStructuralInequality(b)
                return areEquals
            }
        })