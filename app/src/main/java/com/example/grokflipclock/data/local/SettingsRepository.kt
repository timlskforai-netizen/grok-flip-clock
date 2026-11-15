package com.example.grokflipclock.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clock_settings")

enum class ClockThemeMode {
    OLED_BLACK,
    JAPANESE_MINIMAL
}

data class UserSettings(
    val apiKey: String = "",
    val modelName: String = "grok-3",
    val systemPrompt: String = "你係一個貼心、簡潔有幽默感嘅桌面智能助手。請用自然廣東話（可夾雜常用英文）簡短回覆，每次盡量控制喺 80 字以內，方便擺喺桌面時鐘旁邊快速閱讀。",
    val themeMode: ClockThemeMode = ClockThemeMode.OLED_BLACK,
    val is24Hour: Boolean = true,
    val useDeviceLocation: Boolean = true,
    val manualCity: String = "Hong Kong",
    val ambientNote: String = "歡迎使用 Grok 桌面翻頁鐘！請先喺右上角 ⚙️ 設定輸入 xAI API Key 啟用智能助理。"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_24_HOUR = booleanPreferencesKey("is_24_hour")
        val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
        val MANUAL_CITY = stringPreferencesKey("manual_city")
        val AMBIENT_NOTE = stringPreferencesKey("ambient_note")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ClockThemeMode.OLED_BLACK.name
        val theme = runCatching { ClockThemeMode.valueOf(themeStr) }.getOrDefault(ClockThemeMode.OLED_BLACK)

        UserSettings(
            apiKey = prefs[Keys.API_KEY] ?: "",
            modelName = prefs[Keys.MODEL_NAME] ?: "grok-3",
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: UserSettings().systemPrompt,
            themeMode = theme,
            is24Hour = prefs[Keys.IS_24_HOUR] ?: true,
            useDeviceLocation = prefs[Keys.USE_DEVICE_LOCATION] ?: true,
            manualCity = prefs[Keys.MANUAL_CITY] ?: "Hong Kong",
            ambientNote = prefs[Keys.AMBIENT_NOTE] ?: UserSettings().ambientNote
        )
    }

    suspend fun saveSettings(
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        themeMode: ClockThemeMode,
        is24Hour: Boolean,
        useDeviceLocation: Boolean,
        manualCity: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.API_KEY] = apiKey.trim()
            prefs[Keys.MODEL_NAME] = modelName.trim().ifEmpty { "grok-3" }
            prefs[Keys.SYSTEM_PROMPT] = systemPrompt.trim()
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.IS_24_HOUR] = is24Hour
            prefs[Keys.USE_DEVICE_LOCATION] = useDeviceLocation
            prefs[Keys.MANUAL_CITY] = manualCity.trim().ifEmpty { "Hong Kong" }
        }
    }

    suspend fun toggleTheme(current: ClockThemeMode) {
        val next = if (current == ClockThemeMode.OLED_BLACK) {
            ClockThemeMode.JAPANESE_MINIMAL
        } else {
            ClockThemeMode.OLED_BLACK
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = next.name
        }
    }

    suspend fun updateAmbientNote(note: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AMBIENT_NOTE] = note
        }
    }
}
