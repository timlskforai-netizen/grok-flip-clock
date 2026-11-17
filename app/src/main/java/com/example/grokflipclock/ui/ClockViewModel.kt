package com.example.grokflipclock.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import android.location.Location
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
import com.example.grokflipclock.data.remote.HkoCurrentWeatherResponse
import com.example.grokflipclock.data.remote.HourlyWeatherDto
import com.example.grokflipclock.data.remote.NetworkModule
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

data class HourlyForecastItem(
    val timeLabel: String,
    val temperatureText: String,
    val conditionText: String,
    val weatherCode: Int
)

data class WeatherUiState(
    val cityName: String = "Hong Kong",
    val temperatureText: String = "--°C",
    val conditionText: String = "載入中",
    val weatherCode: Int = 1,
    val hourlyForecasts: List<HourlyForecastItem> = emptyList()
)

private data class HkoStation(
    val nameTc: String,
    val lat: Double,
    val lon: Double
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

    companion object {
        // 香港天文台主要自動氣象站座標表
        private val HKO_STATIONS = listOf(
            HkoStation("香港天文台", 22.3019, 114.1742),
            HkoStation("京士柏", 22.3119, 114.1728),
            HkoStation("黃竹坑", 22.2478, 114.1736),
            HkoStation("打鼓嶺", 22.5286, 114.1567),
            HkoStation("流浮山", 22.4689, 113.9836),
            HkoStation("大埔", 22.4461, 114.1789),
            HkoStation("沙田", 22.4025, 114.2100),
            HkoStation("屯門", 22.3906, 113.9767),
            HkoStation("將軍澳", 22.3158, 114.2556),
            HkoStation("西貢", 22.3756, 114.2744),
            HkoStation("長洲", 22.2011, 114.0267),
            HkoStation("赤鱲角", 22.3094, 113.9219),
            HkoStation("青衣", 22.3481, 114.1100),
            HkoStation("石崗", 22.4361, 114.0847),
            HkoStation("荃灣可觀", 22.3836, 114.1078),
            HkoStation("荃灣城門谷", 22.3756, 114.1267),
            HkoStation("香港公園", 22.2783, 114.1622),
            HkoStation("筲箕灣", 22.2817, 114.2361),
            HkoStation("九龍城", 22.3350, 114.1847),
            HkoStation("跑馬地", 22.2706, 114.1836),
            HkoStation("黃大仙", 22.3394, 114.2053),
            HkoStation("赤柱", 22.2142, 114.2186),
            HkoStation("觀塘", 22.3186, 114.2247),
            HkoStation("深水埗", 22.3358, 114.1369),
            HkoStation("啟德跑道公園", 22.3097, 114.2144),
            HkoStation("元朗公園", 22.4408, 114.0183),
            HkoStation("大美督", 22.4753, 114.2375)
        )
    }

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
                // 預設香港天文台總部座標
                var lat = 22.3019
                var lon = 114.1742
                var resolvedCity = currentSettings.manualCity

                if (currentSettings.useDeviceLocation) {
                    // 使用低功耗大概定位（Wi-Fi / 流動網絡），室內桌面亦可秒速獲取座標
                    val loc = runCatching {
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            null
                        ).await()
                    }.getOrNull() ?: runCatching {
                        fusedLocationClient.lastLocation.await()
                    }.getOrNull()

                    if (loc != null) {
                        lat = loc.latitude
                        lon = loc.longitude
                        resolvedCity = reverseGeocodeCity(lat, lon) ?: "Hong Kong"
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

                val inHongKong = isInsideHongKong(lat, lon)

                coroutineScope {
                    // 1. 並行獲取 JMA 每 4 小時預報（若超出東亞覆蓋範圍則自動後備切換 best_match）
                    val jmaForecastDeferred = async {
                        runCatching {
                            val jmaResp = NetworkModule.weatherApi.getCurrentWeather(
                                latitude = lat,
                                longitude = lon,
                                models = "jma_seamless"
                            )
                            if (extractFourHourlyForecasts(jmaResp.hourly).isNotEmpty()) {
                                jmaResp
                            } else {
                                NetworkModule.weatherApi.getCurrentWeather(
                                    latitude = lat,
                                    longitude = lon,
                                    models = "best_match"
                                )
                            }
                        }.getOrNull()
                    }

                    // 2. 若身處香港境內，並行獲取香港天文台分區實測數據
                    val hkoDeferred = if (inHongKong) {
                        async {
                            runCatching {
                                NetworkModule.hkoWeatherApi.getCurrentRegionalWeather()
                            }.getOrNull()
                        }
                    } else {
                        null
                    }

                    val openMeteoResp = jmaForecastDeferred.await()
                    val hkoResp = hkoDeferred?.await()
                    val fourHourly = extractFourHourlyForecasts(openMeteoResp?.hourly)

                    val hkoParsed = if (inHongKong && hkoResp != null) {
                        parseNearestHkoStation(lat, lon, hkoResp)
                    } else {
                        null
                    }

                    if (hkoParsed != null) {
                        // 香港境內：使用天文台最近分區站實測氣溫 + JMA 每 4 小時預報
                        _weather.value = WeatherUiState(
                            cityName = "${hkoParsed.stationName} · HKO",
                            temperatureText = "${hkoParsed.tempCelsius.roundToInt()}°C",
                            conditionText = hkoParsed.conditionWithHumidity,
                            weatherCode = hkoParsed.wmoEquivalentCode,
                            hourlyForecasts = fourHourly
                        )
                    } else if (openMeteoResp?.currentWeather != null) {
                        // 外地（如日本旅行）或天文台接口暫時無回應：自動無縫使用 JMA / Open-Meteo 實測推算
                        val cw = openMeteoResp.currentWeather
                        _weather.value = WeatherUiState(
                            cityName = resolvedCity,
                            temperatureText = "${cw.temperature.roundToInt()}°C",
                            conditionText = mapWeatherCodeToText(cw.weathercode),
                            weatherCode = cw.weathercode,
                            hourlyForecasts = fourHourly
                        )
                    } else {
                        _weather.value = _weather.value.copy(conditionText = "離線")
                    }
                }
            } catch (_: Exception) {
                _weather.value = _weather.value.copy(conditionText = "離線")
            }
        }
    }

    private fun isInsideHongKong(lat: Double, lon: Double): Boolean {
        return lat in 22.14..22.58 && lon in 113.80..114.46
    }

    private data class HkoParsedWeather(
        val stationName: String,
        val tempCelsius: Double,
        val conditionWithHumidity: String,
        val wmoEquivalentCode: Int
    )

    private fun parseNearestHkoStation(
        userLat: Double,
        userLon: Double,
        hko: HkoCurrentWeatherResponse
    ): HkoParsedWeather? {
        val tempReadings = hko.temperature?.data
            ?.filter { !it.place.isNullOrBlank() && it.value != null }
            ?: return null
        if (tempReadings.isEmpty()) return null

        val readingMap = tempReadings.associate { it.place!! to it.value!! }

        // 從當前有正常讀數嘅天文台自動氣象站之中，搵出距離最近嘅分區站
        val availableStations = HKO_STATIONS.filter { readingMap.containsKey(it.nameTc) }
        val nearestStation = availableStations.minByOrNull { station ->
            val results = FloatArray(1)
            Location.distanceBetween(userLat, userLon, station.lat, station.lon, results)
            results[0]
        }

        val chosenPlace = nearestStation?.nameTc ?: tempReadings.first().place!!
        val chosenTemp = readingMap[chosenPlace] ?: tempReadings.first().value!!

        val iconCode = hko.icon?.firstOrNull() ?: 60
        val (conditionText, wmoCode) = mapHkoIconToConditionAndWmo(iconCode)

        val humidityVal = hko.humidity?.data?.firstOrNull { it.value != null }?.value?.roundToInt()
        val fullCondition = if (humidityVal != null) {
            "$conditionText · $humidityVal%"
        } else {
            conditionText
        }

        return HkoParsedWeather(
            stationName = chosenPlace,
            tempCelsius = chosenTemp,
            conditionWithHumidity = fullCondition,
            wmoEquivalentCode = wmoCode
        )
    }

    private fun mapHkoIconToConditionAndWmo(hkoIcon: Int): Pair<String, Int> {
        return when (hkoIcon) {
            50 -> "陽光充沛" to 0
            51 -> "間有陽光" to 1
            52 -> "短暫陽光" to 2
            53 -> "陽光驟雨" to 80
            54 -> "短暫驟雨" to 80
            60 -> "多雲" to 3
            61 -> "密雲" to 3
            62 -> "微雨" to 51
            63 -> "有雨" to 61
            64 -> "大雨" to 65
            65 -> "雷暴" to 95
            70, 71, 72, 73, 74, 75 -> "天色良好" to 0
            76 -> "大致多雲" to 2
            77 -> "大致良好" to 1
            80 -> "大風" to 2
            81 -> "乾燥" to 0
            82 -> "潮濕" to 2
            83, 84 -> "有霧" to 45
            85 -> "煙霞" to 45
            90, 91 -> "炎熱" to 0
            92, 93 -> "清涼" to 1
            else -> "多雲" to 3
        }
    }

    private fun extractFourHourlyForecasts(hourly: HourlyWeatherDto?): List<HourlyForecastItem> {
        val times = hourly?.time ?: return emptyList()
        val temps = hourly.temperature2m ?: return emptyList()
        val codes = hourly.weathercode ?: return emptyList()

        val currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        val targetOffsets = listOf(4L, 8L, 12L, 16L, 20L)
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val parsedSlots = times.indices.mapNotNull { idx ->
            val dt = runCatching {
                LocalDateTime.parse(times[idx], DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }.getOrNull()
            val temp = temps.getOrNull(idx)
            val code = codes.getOrNull(idx)
            if (dt != null && temp != null && code != null) {
                Triple(dt, temp, code)
            } else {
                null
            }
        }

        return targetOffsets.mapNotNull { offset ->
            val targetTime = currentHour.plusHours(offset)
            val match = parsedSlots.firstOrNull { it.first == targetTime }
            match?.let { (dt, temp, code) ->
                HourlyForecastItem(
                    timeLabel = dt.format(timeFormatter),
                    temperatureText = "${temp.roundToInt()}°",
                    conditionText = mapWeatherCodeToText(code),
                    weatherCode = code
                )
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
                    ?.let { GeminiContent(parts = listOf(GeminiPart(it))) }

                val resp = NetworkModule.aiChatApi.generateGeminiContent(
                    url = url,
                    request = GeminiGenerateRequest(
                        systemInstruction = sysInstruction,
                        contents = contents,
                        generationConfig = GeminiGenerationConfig(temperature = temperature)
                    )
                )
                resp.candidates?.firstOrNull()?.content?.parts
                    ?.joinToString("") { it.text }
                    ?.trim()
                    .orEmpty()
            }
        }
    }

    private fun formatApiError(providerLabel: String, e: Exception): String {
        if (e is HttpException) {
            val rawBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            val msg = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(rawBody)?.groupValues?.getOrNull(1)
            val detail = msg ?: rawBody.take(120).ifBlank { e.message() }
            return "$providerLabel 錯誤 (${e.code()})：$detail"
        }
        return "$providerLabel 連線失敗：${e.localizedMessage ?: "請檢查網絡或 API Key"}"
    }

    fun refreshAmbientNote() {
        val currentSettings = settings.value
        val providerLabel = currentSettings.aiProvider.shortName
        val apiKey = currentSettings.activeApiKey

        if (apiKey.isBlank()) {
            viewModelScope.launch {
                settingsRepo.updateAmbientNote("請先點擊時鐘右上角 ⚙️ 輸入 $providerLabel API Key，即可啟用自動時間簡報。")
            }
            return
        }

        viewModelScope.launch {
            _isAmbientLoading.value = true
            try {
                val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)"))
                val weatherStr = "${_weather.value.cityName} ${_weather.value.temperatureText} ${_weather.value.conditionText}"
                val prompt = "現在時間係 $timeStr，當地天氣：$weatherStr。請根據呢個時段同天氣，寫一句 50 字以內嘅桌面小提示、問候或者冷知識（唔好加開頭標題）。"

                val reply = callAiCompletion(
                    currentSettings = currentSettings,
                    singlePrompt = prompt,
                    temperature = 0.8
                )
                if (reply.isNotEmpty()) {
                    settingsRepo.updateAmbientNote(reply)
                }
            } catch (e: Exception) {
                settingsRepo.updateAmbientNote(formatApiError(providerLabel, e))
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
            val providerLabel = currentSettings.aiProvider.shortName
            val apiKey = currentSettings.activeApiKey

            if (apiKey.isBlank()) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        role = "assistant",
                        content = "未設定 $providerLabel API Key，請先按右上角 ⚙️ 輸入 Key。"
                    )
                )
                return@launch
            }

            _isChatLoading.value = true
            try {
                val recent = chatDao.getRecentMessages(12).reversed()
                val reply = callAiCompletion(
                    currentSettings = currentSettings,
                    history = recent,
                    temperature = 0.7
                ).ifEmpty { "(無回覆內容)" }

                chatDao.insertMessage(ChatMessageEntity(role = "assistant", content = reply))
            } catch (e: Exception) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        role = "assistant",
                        content = formatApiError(providerLabel, e)
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
        viewModelScope.launch {
            val prevProvider = settings.value.aiProvider
            val prevKey = settings.value.activeApiKey

            settingsRepo.saveSettings(
                aiProvider = aiProvider,
                grokApiKey = grokApiKey,
                geminiApiKey = geminiApiKey,
                grokModelName = grokModelName,
                geminiModelName = geminiModelName,
                systemPrompt = systemPrompt,
                themeMode = themeMode,
                is24Hour = is24Hour,
                useDeviceLocation = useDeviceLocation,
                manualCity = manualCity
            )
            refreshWeather()

            val newKey = if (aiProvider == AiProvider.GROK) grokApiKey.trim() else geminiApiKey.trim()
            if (newKey.isNotBlank() && (aiProvider != prevProvider || newKey != prevKey)) {
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
