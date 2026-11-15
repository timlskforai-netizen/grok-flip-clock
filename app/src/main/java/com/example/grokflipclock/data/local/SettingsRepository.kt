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

enum class AiProvider(val displayName: String, val shortName: String) {
    GROK("Grok (xAI)", "Grok"),
    GEMINI("Gemini (Google)", "Gemini")
}

data class UserSettings(
    val aiProvider: AiProvider = AiProvider.GROK,
    val grokApiKey: String = "",
    val geminiApiKey: String = "",
    val grokModelName: String = "grok-3",
    val geminiModelName: String = "gemini-2.5-flash",
    val systemPrompt: String = "你係一個貼心、簡潔有幽默感嘅桌面智能助手。請用自然廣東話（可夾雜常用英文）簡短回覆，每次盡量控制喺 80 字以內，方便擺喺桌面時鐘旁邊快速閱讀。",
    val themeMode: ClockThemeMode = ClockThemeMode.OLED_BLACK,
    val is24Hour: Boolean = true,
    val useDeviceLocation: Boolean = true,
    val manualCity: String = "Hong Kong",
    val ambientNote: String = "歡迎使用桌面翻頁鐘！請先喺右上角 ⚙️ 設定選擇 Grok 或 Gemini 並輸入 API Key。"
) {
    val activeApiKey: String
        get() = if (aiProvider == AiProvider.GROK) grokApiKey else geminiApiKey

    val activeModelName: String
        get() = if (aiProvider == AiProvider.GROK) grokModelName else geminiModelName
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val LEGACY_API_KEY = stringPreferencesKey("api_key")
        val GROK_API_KEY = stringPreferencesKey("grok_api_key")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val LEGACY_MODEL_NAME = stringPreferencesKey("model_name")
        val GROK_MODEL_NAME = stringPreferencesKey("grok_model_name")
        val GEMINI_MODEL_NAME = stringPreferencesKey("gemini_model_name")
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

        val providerStr = prefs[Keys.AI_PROVIDER] ?: AiProvider.GROK.name
        val provider = runCatching { AiProvider.valueOf(providerStr) }.getOrDefault(AiProvider.GROK)

        UserSettings(
            aiProvider = provider,
            grokApiKey = prefs[Keys.GROK_API_KEY] ?: prefs[Keys.LEGACY_API_KEY] ?: "",
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            grokModelName = prefs[Keys.GROK_MODEL_NAME] ?: prefs[Keys.LEGACY_MODEL_NAME] ?: "grok-3",
            geminiModelName = prefs[Keys.GEMINI_MODEL_NAME] ?: "gemini-2.5-flash",
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: UserSettings().systemPrompt,
            themeMode = theme,
            is24Hour = prefs[Keys.IS_24_HOUR] ?: true,
            useDeviceLocation = prefs[Keys.USE_DEVICE_LOCATION] ?: true,
            manualCity = prefs[Keys.MANUAL_CITY] ?: "Hong Kong",
            ambientNote = prefs[Keys.AMBIENT_NOTE] ?: UserSettings().ambientNote
        )
    }

    suspend fun saveSettings(
        aiProvider: AiProvider,
        grokApiKey: String,
        geminiApiKey: String,
        grokModelName: String,
        geminiModelName: String,
        systemPrompt: String,
        themeMode: ClockThemeMode,
        is24Hour: Boolean,
        useDeviceLocation: Boolean,
        manualCity: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_PROVIDER] = aiProvider.name
            prefs[Keys.GROK_API_KEY] = grokApiKey.trim()
            prefs[Keys.GEMINI_API_KEY] = geminiApiKey.trim()
            prefs[Keys.GROK_MODEL_NAME] = grokModelName.trim().ifEmpty { "grok-3" }
            prefs[Keys.GEMINI_MODEL_NAME] = geminiModelName.trim().ifEmpty { "gemini-2.5-flash" }
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
