package com.example.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.viewmodel.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore(private val context: Context) {
    companion object {
        val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_FONT_SIZE = intPreferencesKey("font_size")
        val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        val KEY_TTS_GENDER = stringPreferencesKey("tts_gender") // "male" / "female"
        val KEY_DEFAULT_SOURCE_LANG = stringPreferencesKey("source_lang")
        val KEY_DEFAULT_TARGET_LANG = stringPreferencesKey("target_lang")
        val KEY_SCROLL_DIRECTION = stringPreferencesKey("scroll_direction") // "vertical" / "horizontal"
        val KEY_VIEW_MODE = stringPreferencesKey("view_mode") // "single" / "dual" / "continuous" / "book"
        val KEY_HIGHLIGHT_COLOR = stringPreferencesKey("highlight_color")
        val KEY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock")
        val KEY_SCREEN_SECURITY = booleanPreferencesKey("screen_security")
        val KEY_BRIGHTNESS = floatPreferencesKey("brightness") // -1 = system
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
    }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_FIRST_LAUNCH] ?: true }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map {
            try {
                ThemeMode.valueOf(it[KEY_THEME_MODE] ?: ThemeMode.DARK.name)
            } catch (e: Exception) {
                ThemeMode.DARK
            }
        }

    val isDarkTheme: Flow<Boolean> = themeMode
        .map { it == ThemeMode.DARK || it == ThemeMode.SEPIA }

    val fontSize: Flow<Int> = context.dataStore.data
        .map { it[KEY_FONT_SIZE] ?: 16 }

    val lineSpacing: Flow<Float> = context.dataStore.data
        .map { it[KEY_LINE_SPACING] ?: 1.5f }

    val ttsSpeed: Flow<Float> = context.dataStore.data
        .map { it[KEY_TTS_SPEED] ?: 1.0f }

    val ttsGender: Flow<String> = context.dataStore.data
        .map { it[KEY_TTS_GENDER] ?: "female" }

    val sourceLang: Flow<String> = context.dataStore.data
        .map { it[KEY_DEFAULT_SOURCE_LANG] ?: "de" }

    val targetLang: Flow<String> = context.dataStore.data
        .map { it[KEY_DEFAULT_TARGET_LANG] ?: "ar" }

    val scrollDirection: Flow<String> = context.dataStore.data
        .map { it[KEY_SCROLL_DIRECTION] ?: "vertical" }

    val viewMode: Flow<String> = context.dataStore.data
        .map { it[KEY_VIEW_MODE] ?: "continuous" }

    val highlightColor: Flow<String> = context.dataStore.data
        .map { it[KEY_HIGHLIGHT_COLOR] ?: "#fbbf24" }

    val highContrast: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_HIGH_CONTRAST] ?: false }

    val reduceMotion: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_REDUCE_MOTION] ?: false }

    val appLock: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_APP_LOCK] ?: false }

    val screenSecurity: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_SCREEN_SECURITY] ?: false }

    val brightness: Flow<Float> = context.dataStore.data
        .map { it[KEY_BRIGHTNESS] ?: -1f }

    val fontFamily: Flow<String> = context.dataStore.data
        .map { it[KEY_FONT_FAMILY] ?: "Noto Sans Arabic" }

    suspend fun setFirstLaunchDone() {
        context.dataStore.edit { it[KEY_FIRST_LAUNCH] = false }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setFontSize(size: Int) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = size }
    }

    suspend fun setLineSpacing(spacing: String) {
        val spValue = when(spacing) {
            "tight" -> 1.1f
            "wide" -> 1.8f
            else -> 1.5f
        }
        context.dataStore.edit { it[KEY_LINE_SPACING] = spValue }
    }

    suspend fun setScrollDirection(dir: String) {
        context.dataStore.edit { it[KEY_SCROLL_DIRECTION] = dir }
    }

    suspend fun setViewMode(mode: String) {
        context.dataStore.edit { it[KEY_VIEW_MODE] = mode }
    }

    suspend fun setSourceLang(lang: String) {
        context.dataStore.edit { it[KEY_DEFAULT_SOURCE_LANG] = lang }
    }

    suspend fun setTargetLang(lang: String) {
        context.dataStore.edit { it[KEY_DEFAULT_TARGET_LANG] = lang }
    }

    suspend fun setTtsSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_TTS_SPEED] = speed }
    }

    suspend fun setTtsVoice(voice: String) {
        context.dataStore.edit { it[KEY_TTS_GENDER] = voice }
    }

    suspend fun setHighlightColor(hex: String) {
        context.dataStore.edit { it[KEY_HIGHLIGHT_COLOR] = hex }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HIGH_CONTRAST] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REDUCE_MOTION] = enabled }
    }

    suspend fun setScreenSecurity(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SCREEN_SECURITY] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean, type: String = "biometric") {
        context.dataStore.edit {
            it[KEY_APP_LOCK] = enabled
            it[stringPreferencesKey("lock_type")] = type
        }
    }

    suspend fun setBrightness(value: Float) {
        context.dataStore.edit { it[KEY_BRIGHTNESS] = value }
    }

    suspend fun setFontFamily(font: String) {
        context.dataStore.edit { it[KEY_FONT_FAMILY] = font }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
