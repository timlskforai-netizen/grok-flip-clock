package com.example.grokflipclock.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.grokflipclock.data.local.AiProvider
import com.example.grokflipclock.data.local.AppDatabase
import com.example.grokflipclock.data.local.ChatMessageEntity
import com.example.grokflipclock.data.local.ClockThemeMode
import com.example.grokflipclock.data.local.SettingsRepository
import com.example.grokflipclock.data.local.UserSettings
import com.example.grokflipclock.data.remote.AiChatRequest
import com.example.grokflipclock.data.remote.AiMessage
import com.example.grokflipclock.data.remote.GeminiContent
import com.example.grokflipclock.data.remote.GeminiGenerateRequest
import com.example.grokflipclock.data.remote.GeminiGenerationConfig
import com.example.grokflipclock.data.remote.GeminiPart
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
import retrofit2.HttpException
import java.net.URLEncoder
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
            delay(500L)
            refreshWeather()
            if (settings.first().activeApiKey.isNotBlank()) {
                refreshAmbientNote()
            }

            var ticks = 0
            while (true) {
                delay(30 * 60 * 1000L)
                ticks++
                refreshWeather()
                if (ticks % 4 == 0 && settings.value.activeApiKey.isNotBlank()) {
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

    private suspend fun callAiCompletion(
        currentSettings: UserSettings,
        history: List<ChatMessageEntity> = emptyList(),
        singlePrompt: String? = null,
        temperature: Double = 0.7
    ): String {
        val apiKey = currentSettings.activeApiKey.trim()
        val rawModel = currentSettings.activeModelName.trim()

        return when (currentSettings.aiProvider) {
            AiProvider.GROK -> {
                val messages = buildList {
                    if (currentSettings.systemPrompt.isNotBlank()) {
                        add(AiMessage("system", currentSettings.systemPrompt.trim()))
                    }
                    if (singlePrompt != null) {
                        add(AiMessage("user", singlePrompt))
                    } else {
                        history.forEach { add(AiMessage(it.role, it.content)) }
                    }
                }
                val resp = NetworkModule.aiChatApi.createChatCompletion(
                    url = NetworkModule.GROK_CHAT_URL,
                    authHeader = "Bearer $apiKey",
                    request = AiChatRequest(
                        model = rawModel.ifEmpty { "grok-3" },
                        messages = messages,
                        temperature = temperature
                    )
                )
                resp.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            }

            AiProvider.GEMINI -> {
                val cleanModel = rawModel.removePrefix("models/").ifEmpty { "gemini-2.5-flash" }
                val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$encodedKey"

                val contents = if (singlePrompt != null) {
                    listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(singlePrompt))))
                } else {
                    history.map { entity ->
                        val geminiRole = if (entity.role == "assistant") "model" else "user"
                        GeminiContent(role = geminiRole, parts = listOf(GeminiPart(entity.content)))
                    }.dropWhile { it.role != "user" }
                }

                val sysInstruction = currentSettings.systemPrompt.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { GeminiContent
