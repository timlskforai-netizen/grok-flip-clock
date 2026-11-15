package com.example.grokflipclock.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.grokflipclock.data.local.AppDatabase
import com.example.grokflipclock.data.local.ChatMessageEntity
import com.example.grokflipclock.data.local.ClockThemeMode
import com.example.grokflipclock.data.local.SettingsRepository
import com.example.grokflipclock.data.local.UserSettings
import com.example.grokflipclock.data.remote.GrokChatRequest
import com.example.grokflipclock.data.remote.GrokMessage
import com.example.grokflipclock.data.remote.NetworkModule
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeatherUiState(
    val cityName: String = "Hong Kong",
    val temperatureText: String = "--°C",
    val conditionText: String = "載入中",
    val weatherCode: Int = 1
)

class ClockViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val chatDao = AppDatabase.getInstance(application).chatDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    val settings: StateFlow<UserSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    val chatMessages: StateFlow<List<ChatMessageEntity>> = chatDao.getAllMessages().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _now = MutableStateFlow(LocalDateTime.now())
    val now: StateFlow<LocalDateTime> = _now.asStateFlow()

    private val _weather = MutableStateFlow(WeatherUiState())
    val weather: StateFlow<WeatherUiState> = _weather.asStateFlow()

    private val _isAmbientLoading = MutableStateFlow(false)
    val isAmbientLoading: StateFlow<Boolean> = _isAmbientLoading.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    init {
        startClockTicker()
        startPeriodicWeatherAndAmbient()
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            while (true) {
                val current = LocalDateTime.now()
                if (current.second != _now.value.second) {
                    _now.value = current
                }
                delay(200L)
            }
        }
    }

    private fun startPeriodicWeatherAndAmbient() {
        viewModelScope.launch {
            // 首次啟動延遲少許等 DataStore 讀取完成
            delay(500L)
            refreshWeather()
            if (settings.first().apiKey.isNotBlank()) {
                refreshAmbientNote()
            }

            var ticks = 0
            while (true) {
                delay(30 * 60 * 1000L) // 每 30 分鐘更新天氣
                ticks++
                refreshWeather()
                if (ticks % 4 == 0 && settings.value.apiKey.isNotBlank()) {
                    // 每 2 小時自動更新一次 Grok Ambient 簡報
                    refreshAmbientNote()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshWeather() {
        viewModelScope.launch {
            val currentSettings = settings.value
            try {
                var lat = 22.3193
                var lon = 114.1694
                var resolvedCity = currentSettings.manualCity

                if (currentSettings.useDeviceLocation) {
                    val loc = runCatching { fusedLocationClient.lastLocation.await() }.getOrNull()
                    if (loc != null) {
                        lat = loc.latitude
                        lon = loc.longitude
                        resolvedCity = reverseGeocodeCity(lat, lon) ?: "Local"
                    } else {
                        val geo = NetworkModule.geocodingApi.searchCity(currentSettings.manualCity).results?.firstOrNull()
                        if (geo != null) {
                            lat = geo.latitude
                            lon = geo.longitude
                            resolvedCity = geo.name
                        }
                    }
                } else {
                    val geo = NetworkModule.geocodingApi.searchCity(currentSettings.manualCity).results?.firstOrNull()
                    if (geo != null) {
                        lat = geo.latitude
                        lon = geo.longitude
                        resolvedCity = geo.name
                    }
                }

                val response = NetworkModule.weatherApi.getCurrentWeather(lat, lon)
                val cw = response.currentWeather
                if (cw != null) {
                    _weather.value = WeatherUiState(
                        cityName = resolvedCity,
                        temperatureText = "${cw.temperature.toInt()}°C",
                        conditionText = mapWeatherCodeToText(cw.weathercode),
                        weatherCode = cw.weathercode
                    )
                }
            } catch (_: Exception) {
                _weather.value = _weather.value.copy(conditionText = "離線")
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeCity(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(getApplication(), Locale.ENGLISH)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.locality ?: addresses?.firstOrNull()?.adminArea
        }.getOrNull()
    }

    fun refreshAmbientNote() {
        val currentSettings = settings.value
        if (currentSettings.apiKey.isBlank()) {
            viewModelScope.launch {
                settingsRepo.updateAmbientNote("請先點擊時鐘右上角 ⚙️ 輸入 xAI API Key，即可啟用 Grok 自動時間簡報。")
            }
            return
        }

        viewModelScope.launch {
            _isAmbientLoading.value = true
            try {
                val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)"))
                val weatherStr = "${_weather.value.cityName} ${_weather.value.temperatureText} ${_weather.value.conditionText}"
                val prompt = "現在時間係 $timeStr，當地天氣：$weatherStr。請根據呢個時段同天氣，寫一句 50 字以內嘅桌面小提示、問候或者冷知識（唔好加開頭標題）。"

                val req = GrokChatRequest(
                    model = currentSettings.modelName,
                    messages = listOf(
                        GrokMessage("system", currentSettings.systemPrompt),
                        GrokMessage("user", prompt)
                    ),
                    temperature = 0.8
                )
                val resp = NetworkModule.grokApi.createChatCompletion(
                    authHeader = "Bearer ${currentSettings.apiKey}",
                    request = req
                )
                val reply = resp.choices.firstOrNull()?.message?.content?.trim()
                if (!reply.isNullOrEmpty()) {
                    settingsRepo.updateAmbientNote(reply)
                }
            } catch (e: Exception) {
                settingsRepo.updateAmbientNote("Grok 連線失敗：${e.localizedMessage ?: "請檢查網絡或 API Key"}")
            } finally {
                _isAmbientLoading.value = false
            }
        }
    }

    fun sendChatMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            chatDao.insertMessage(ChatMessageEntity(role = "user", content = trimmed))

            val currentSettings = settings.value
            if (currentSettings.apiKey.isBlank()) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        role = "assistant",
                        content = "未設定 xAI API Key，請先按右上角 ⚙️ 輸入 Key。"
                    )
                )
                return@launch
            }

            _isChatLoading.value = true
            try {
                // 讀取最近 12 條對話記憶維持上下文
                val recent = chatDao.getRecentMessages(12).reversed()
                val apiMessages = buildList {
                    add(GrokMessage("system", currentSettings.systemPrompt))
                    recent.forEach { entity ->
                        add(GrokMessage(entity.role, entity.content))
                    }
                }

                val resp = NetworkModule.grokApi.createChatCompletion(
                    authHeader = "Bearer ${currentSettings.apiKey}",
                    request = GrokChatRequest(
                        model = currentSettings.modelName,
                        messages = apiMessages
                    )
                )
                val reply = resp.choices.firstOrNull()?.message?.content?.trim() ?: "(無回覆內容)"
                chatDao.insertMessage(ChatMessageEntity(role = "assistant", content = reply))
            } catch (e: Exception) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        role = "assistant",
                        content = "請求出錯：${e.localizedMessage ?: "請檢查 API Key 或模型名稱"}"
                    )
                )
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatDao.clearAll()
        }
    }

    fun toggleTheme() {
        viewModelScope.launch {
            settingsRepo.toggleTheme(settings.value.themeMode)
        }
    }

    fun saveSettings(
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        themeMode: ClockThemeMode,
        is24Hour: Boolean,
        useDeviceLocation: Boolean,
        manualCity: String
    ) {
        viewModelScope.launch {
            val previousKey = settings.value.apiKey
            settingsRepo.saveSettings(
                apiKey = apiKey,
                modelName = modelName,
                systemPrompt = systemPrompt,
                themeMode = themeMode,
                is24Hour = is24Hour,
                useDeviceLocation = useDeviceLocation,
                manualCity = manualCity
            )
            refreshWeather()
            if (apiKey.isNotBlank() && apiKey != previousKey) {
                refreshAmbientNote()
            }
        }
    }

    private fun mapWeatherCodeToText(code: Int): String {
        return when (code) {
            0 -> "晴朗"
            1, 2 -> "大致天晴"
            3 -> "多雲"
            45, 48 -> "有霧"
            51, 53, 55 -> "毛毛雨"
            61, 63, 65 -> "有雨"
            71, 73, 75, 77 -> "降雪"
            80, 81, 82 -> "驟雨"
            95, 96, 99 -> "雷暴"
            else -> "多雲"
        }
    }
}
