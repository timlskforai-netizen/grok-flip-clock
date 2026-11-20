package com.example.grokflipclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.grokflipclock.MainActivity
import com.example.grokflipclock.R
import com.example.grokflipclock.data.remote.HourlyWeatherDto
import com.example.grokflipclock.data.remote.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class WidgetManualConfig(
    val useManualLocation: Boolean = false,
    val manualQuery: String = "大埔",
    val is24Hour: Boolean = true,
    val showDate: Boolean = true,
    val showForecast: Boolean = true,
    val themeStyle: String = "dark",
    val bgAlphaPercent: Int = 90,
    val fontScalePercent: Int = 100
)

class FlipClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
        fetchWeatherForAllWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WEATHER) {
            fetchWeatherForAllWidgets(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (id in appWidgetIds) {
            editor.remove("cfg_${id}_use_manual")
                .remove("cfg_${id}_query")
                .remove("cfg_${id}_24h")
                .remove("cfg_${id}_date")
                .remove("cfg_${id}_forecast")
                .remove("cfg_${id}_theme")
                .remove("cfg_${id}_alpha")
                .remove("cfg_${id}_scale")
                .remove("data_${id}_city")
                .remove("data_${id}_temp")
                .remove("data_${id}_cond")
                .remove("data_${id}_summary")
        }
        editor.apply()
    }

    companion object {
        const val ACTION_REFRESH_WEATHER = "com.example.grokflipclock.ACTION_REFRESH_WEATHER"
        private const val PREFS_NAME = "flip_clock_widget_prefs"
        private const val KEY_CITY = "city"
        private const val KEY_TEMP = "temp"
        private const val KEY_CONDITION = "condition"
        private const val KEY_SUMMARY = "summary"
        private const val KEY_IS_24H = "is_24h"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"

        private val HKO_COORDS = listOf(
            Triple("香港天文台", 22.3019, 114.1742),
            Triple("京士柏", 22.3119, 114.1728),
            Triple("黃竹坑", 22.2478, 114.1736),
            Triple("打鼓嶺", 22.5286, 114.1567),
            Triple("流浮山", 22.4689, 113.9836),
            Triple("大埔", 22.4461, 114.1789),
            Triple("沙田", 22.4025, 114.2100),
            Triple("屯門", 22.3906, 113.9767),
            Triple("將軍澳", 22.3158, 114.2556),
            Triple("西貢", 22.3756, 114.2744),
            Triple("長洲", 22.2011, 114.0267),
            Triple("赤鱲角", 22.3094, 113.9219),
            Triple("青衣", 22.3481, 114.1100),
            Triple("石崗", 22.4361, 114.0847),
            Triple("荃灣可觀", 22.3836, 114.1078),
            Triple("荃灣城門谷", 22.3756, 114.1267),
            Triple("香港公園", 22.2783, 114.1622),
            Triple("筲箕灣", 22.2817, 114.2361),
            Triple("九龍城", 22.3350, 114.1847),
            Triple("跑馬地", 22.2706, 114.1836),
            Triple("黃大仙", 22.3394, 114.2053),
            Triple("赤柱", 22.2142, 114.2186),
            Triple("觀塘", 22.3186, 114.2247),
            Triple("深水埗", 22.3358, 114.1369),
            Triple("啟德跑道公園", 22.3097, 114.2144),
            Triple("元朗公園", 22.4408, 114.0183),
            Triple("大美督", 22.4753, 114.2375)
        )

        fun getWidgetConfig(context: Context, appWidgetId: Int): WidgetManualConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val global24h = prefs.getBoolean(KEY_IS_24H, true)
            return WidgetManualConfig(
                useManualLocation = prefs.getBoolean("cfg_${appWidgetId}_use_manual", false),
                manualQuery = prefs.getString("cfg_${appWidgetId}_query", "大埔") ?: "大埔",
                is24Hour = prefs.getBoolean("cfg_${appWidgetId}_24h", global24h),
                showDate = prefs.getBoolean("cfg_${appWidgetId}_date", true),
                showForecast = prefs.getBoolean("cfg_${appWidgetId}_forecast", true),
                themeStyle = prefs.getString("cfg_${appWidgetId}_theme", "dark") ?: "dark",
                bgAlphaPercent = prefs.getInt("cfg_${appWidgetId}_alpha", 90),
                fontScalePercent = prefs.getInt("cfg_${appWidgetId}_scale", 100)
            )
        }

        fun saveWidgetConfig(context: Context, appWidgetId: Int, config: WidgetManualConfig) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("cfg_${appWidgetId}_use_manual", config.useManualLocation)
                .putString("cfg_${appWidgetId}_query", config.manualQuery.trim())
                .putBoolean("cfg_${appWidgetId}_24h", config.is24Hour)
                .putBoolean("cfg_${appWidgetId}_date", config.showDate)
                .putBoolean("cfg_${appWidgetId}_forecast", config.showForecast)
                .putString("cfg_${appWidgetId}_theme", config.themeStyle)
                .putInt("cfg_${appWidgetId}_alpha", config.bgAlphaPercent.coerceIn(0, 100))
                .putInt("cfg_${appWidgetId}_scale", config.fontScalePercent.coerceIn(80, 130))
                .apply()

            updateSingleWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            fetchWeatherForWidget(context, appWidgetId)
        }

        fun syncFromApp(
            context: Context,
            city: String,
            temp: String,
            condition: String,
            hourlySummary: String,
            is24Hour: Boolean,
            lat: Double? = null,
            lon: Double? = null
        ) {
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CITY, city)
                .putString(KEY_TEMP, temp)
                .putString(KEY_CONDITION, condition)
                .putString(KEY_SUMMARY, hourlySummary)
                .putBoolean(KEY_IS_24H, is24Hour)

            if (lat != null && lon != null) {
                editor.putFloat(KEY_LAT, lat.toFloat()).putFloat(KEY_LON, lon.toFloat())
            }
            editor.apply()

            updateAllWidgets(context)
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FlipClockWidgetProvider::class.java))
            for (id in ids) {
                updateSingleWidget(context, manager, id)
            }
        }

        fun fetchWeatherForAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FlipClockWidgetProvider::class.java))
            for (id in ids) {
                fetchWeatherForWidget(context, id)
            }
        }

        fun fetchWeatherForWidget(context: Context, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val cfg = getWidgetConfig(context, appWidgetId)

                    var lat = prefs.getFloat(KEY_LAT, 22.3019f).toDouble()
                    var lon = prefs.getFloat(KEY_LON, 114.1742f).toDouble()
                    var resolvedCity = prefs.getString(KEY_CITY, "Hong Kong") ?: "Hong Kong"
                    var forcedHkoStation: String? = null

                    if (cfg.useManualLocation && cfg.manualQuery.isNotBlank()) {
                        val q = cfg.manualQuery.trim()
                        val matchedHko = HKO_COORDS.firstOrNull { (name, _, _) ->
                            name.contains(q, ignoreCase = true) || q.contains(name, ignoreCase = true)
                        }
                        if (matchedHko != null) {
                            forcedHkoStation = matchedHko.first
                            lat = matchedHko.second
                            lon = matchedHko.third
                            resolvedCity = matchedHko.first
                        } else {
                            val geo = NetworkModule.geocodingApi.searchCity(q).results?.firstOrNull()
                            if (geo != null) {
                                lat = geo.latitude
                                lon = geo.longitude
                                resolvedCity = geo.name
                            }
                        }
                    }

                    val omResp = runCatching {
                        NetworkModule.weatherApi.getCurrentWeather(lat, lon, models = "best_match")
                    }.getOrNull()

                    val summary = extractCompactSummary(omResp?.hourly)
                        .ifEmpty { prefs.getString("data_${appWidgetId}_summary", null) ?: prefs.getString(KEY_SUMMARY, "") ?: "" }

                    val inHongKong = lat in 22.14..22.58 && lon in 113.80..114.46
                    if (inHongKong) {
                        val hko = runCatching { NetworkModule.hkoWeatherApi.getCurrentRegionalWeather() }.getOrNull()
                        val readings = hko?.temperature?.data?.filter { !it.place.isNullOrBlank() && it.value != null }.orEmpty()
                        if (readings.isNotEmpty()) {
                            val map = readings.associate { it.place!! to it.value!! }
                            val chosenPlace = if (forcedHkoStation != null && map.containsKey(forcedHkoStation)) {
                                forcedHkoStation
                            } else {
                                val nearest = HKO_COORDS.filter { map.containsKey(it.first) }.minByOrNull { (_, sLat, sLon) ->
                                    val d = FloatArray(1)
                                    Location.distanceBetween(lat, lon, sLat, sLon, d)
                                    d[0]
                                }
                                nearest?.first ?: readings.first().place!!
                            }
                            val temp = map[chosenPlace] ?: readings.first().value!!
                            val cond = mapHkoIcon(hko?.icon?.firstOrNull() ?: 60)
                            val hum = hko?.humidity?.data?.firstOrNull { it.value != null }?.value?.roundToInt()
                            val condText = if (hum != null) "$cond · $hum%" else cond

                            prefs.edit()
                                .putString("data_${appWidgetId}_city", "$chosenPlace · HKO")
                                .putString("data_${appWidgetId}_temp", "${temp.roundToInt()}°C")
                                .putString("data_${appWidgetId}_cond", condText)
                                .putString("data_${appWidgetId}_summary", summary)
                                .apply()

                            updateSingleWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                            return@launch
                        }
                    }

                    val cw = omResp?.currentWeather
                    if (cw != null) {
                        prefs.edit()
                            .putString("data_${appWidgetId}_city", resolvedCity)
                            .putString("data_${appWidgetId}_temp", "${cw.temperature.roundToInt()}°C")
                            .putString("data_${appWidgetId}_cond", mapWmoCode(cw.weathercode))
                            .putString("data_${appWidgetId}_summary", summary)
                            .apply()

                        updateSingleWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                    }
                } catch (_: Exception) {
                }
            }
        }

        private fun extractCompactSummary(hourly: HourlyWeatherDto?): String {
            val times = hourly?.time ?: return ""
            val temps = hourly.temperature2m ?: return ""
            val currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
            val offsets = listOf(4L, 8L, 12L)
            val fmt = DateTimeFormatter.ofPattern("HH:mm")

            val slots = times.indices.mapNotNull { idx ->
                val dt = runCatching { LocalDateTime.parse(times[idx], DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
                val temp = temps.getOrNull(idx)
                if (dt != null && temp != null) dt to temp else null
            }

            return offsets.mapNotNull { off ->
                val target = currentHour.plusHours(off)
                slots.firstOrNull { it.first == target }?.let { (dt, temp) ->
                    "${dt.format(fmt)} ${temp.roundToInt()}°"
                }
            }.joinToString("  ·  ")
        }

        private fun updateSingleWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cfg = getWidgetConfig(context, appWidgetId)

            val options = manager.getAppWidgetOptions(appWidgetId)
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            // 直度螢幕時，實際寬度係 MIN_WIDTH，實際高度係 MAX_HEIGHT（Samsung One UI 1格約 115dp，2格約 240dp）
            val actualWidthDp = if (isLandscape) {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
            } else {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            }
            val actualHeightDp = if (isLandscape) {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
            } else {
                val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                if (maxH > 0) maxH else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
            }

            // 高度低於 165dp 屬於單行高度（包含 Samsung One UI 115dp 的 2x1 與 4x1）
            val isSingleRowHeight = actualHeightDp in 1..164
            val is2x1Compact = isSingleRowHeight && actualWidthDp in 1..225

            val layoutId = when {
                is2x1Compact -> R.layout.widget_flip_clock_2x1
                isSingleRowHeight -> R.layout.widget_flip_clock_row
                else -> R.layout.widget_flip_clock
            }

            val fallbackCity = prefs.getString(KEY_CITY, "Hong Kong") ?: "Hong Kong"
            val fallbackTemp = prefs.getString(KEY_TEMP, "--°C") ?: "--°C"
            val fallbackCond = prefs.getString(KEY_CONDITION, "輕觸更新") ?: "輕觸更新"
            val fallbackSummary = prefs.getString(KEY_SUMMARY, "") ?: ""

            val rawCity = if (cfg.useManualLocation) {
                prefs.getString("data_${appWidgetId}_city", cfg.manualQuery) ?: cfg.manualQuery
            } else {
                prefs.getString("data_${appWidgetId}_city", fallbackCity) ?: fallbackCity
            }
            val displayCity = if (is2x1Compact) rawCity.substringBefore(" · ") else rawCity
            val temp = prefs.getString("data_${appWidgetId}_temp", fallbackTemp) ?: fallbackTemp
            val condition = prefs.getString("data_${appWidgetId}_cond", fallbackCond) ?: fallbackCond
            val summary = prefs.getString("data_${appWidgetId}_summary", fallbackSummary) ?: fallbackSummary

            val views = RemoteViews(context.packageName, layoutId)

            val isLight = cfg.themeStyle == "light"
            val bgDrawable = when (cfg.themeStyle) {
                "oled" -> R.drawable.widget_bg_oled
                "light" -> R.drawable.widget_bg_light
                else -> R.drawable.widget_bg
            }
            val cardDrawable = if (isLight) R.drawable.widget_card_light else R.drawable.widget_flip_card
            val alpha255 = (cfg.bgAlphaPercent * 255 / 100).coerceIn(0, 255)
            val cardAlpha255 = if (cfg.bgAlphaPercent == 0) 85 else (cfg.bgAlphaPercent * 255 / 100).coerceIn(60, 255)

            views.setImageViewResource(R.id.widget_bg_image, bgDrawable)
            views.setInt(R.id.widget_bg_image, "setImageAlpha", alpha255)

            views.setImageViewResource(R.id.widget_hour_card_bg, cardDrawable)
            views.setInt(R.id.widget_hour_card_bg, "setImageAlpha", cardAlpha255)
            views.setImageViewResource(R.id.widget_min_card_bg, cardDrawable)
            views.setInt(R.id.widget_min_card_bg, "setImageAlpha", cardAlpha255)
            views.setImageViewResource(R.id.widget_weather_card_bg, cardDrawable)
            views.setInt(R.id.widget_weather_card_bg, "setImageAlpha", cardAlpha255)

            val primaryTextColor = if (isLight) Color.parseColor("#18181B") else Color.parseColor("#F4F4F5")
            val secondaryTextColor = if (isLight) Color.parseColor("#52525B") else Color.parseColor("#A1A1AA")
            val accentColor = if (isLight) Color.parseColor("#0284C7") else Color.parseColor("#38BDF8")

            views.setTextColor(R.id.widget_date, secondaryTextColor)
            views.setTextColor(R.id.widget_hour, primaryTextColor)
            views.setTextColor(R.id.widget_minute, primaryTextColor)
            views.setTextColor(R.id.widget_colon, secondaryTextColor)
            views.setTextColor(R.id.widget_city, secondaryTextColor)
            views.setTextColor(R.id.widget_temp, accentColor)
            views.setTextColor(R.id.widget_condition, primaryTextColor)
            views.setTextColor(R.id.widget_forecast_text, secondaryTextColor)

            val scale = cfg.fontScalePercent / 100f
            val baseDigitSp = when {
                is2x1Compact -> 24f
                isSingleRowHeight -> 28f
                else -> 42f
            }
            val baseColonSp = when {
                is2x1Compact -> 18f
                isSingleRowHeight -> 20f
                else -> 30f
            }
            views.setTextViewTextSize(R.id.widget_hour, TypedValue.COMPLEX_UNIT_SP, baseDigitSp * scale)
            views.setTextViewTextSize(R.id.widget_minute, TypedValue.COMPLEX_UNIT_SP, baseDigitSp * scale)
            views.setTextViewTextSize(R.id.widget_colon, TypedValue.COMPLEX_UNIT_SP, baseColonSp * scale)

            views.setViewVisibility(R.id.widget_config_btn, View.VISIBLE)

            val showDateNow = cfg.showDate && !is2x1Compact
            views.setViewVisibility(R.id.widget_date, if (showDateNow) View.VISIBLE else View.GONE)

            val hourFormat = if (cfg.is24Hour) "HH" else "hh"
            views.setCharSequence(R.id.widget_hour, "setFormat12Hour", hourFormat)
            views.setCharSequence(R.id.widget_hour, "setFormat24Hour", hourFormat)

            views.setTextViewText(R.id.widget_city, displayCity)
            views.setTextViewText(R.id.widget_temp, temp)
            views.setTextViewText(R.id.widget_condition, condition)
            views.setViewVisibility(R.id.widget_condition, View.VISIBLE)

            // 4x1 及 4x2+ 皆顯示未來每 4 小時預報，僅 2x1 細格隱藏
            val canShowForecast = cfg.showForecast && summary.isNotBlank() && !is2x1Compact
            if (canShowForecast) {
                views.setTextViewText(R.id.widget_forecast_text, summary)
                views.setViewVisibility(R.id.widget_forecast_text, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_forecast_text, View.GONE)
            }

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_clock_container, openAppPending)

            val refreshIntent = Intent(context, FlipClockWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WEATHER
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_weather_bar, refreshPending)

            val configIntent = Intent(context, FlipClockWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val configPending = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 3,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_config_btn, configPending)

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun mapHkoIcon(icon: Int): String = when (icon) {
            50 -> "陽光充沛"
            51 -> "間有陽光"
            52 -> "短暫陽光"
            53, 54 -> "驟雨"
            60, 61 -> "多雲"
            62 -> "微雨"
            63, 64 -> "有雨"
            65 -> "雷暴"
            70, 71, 72, 73, 74, 75, 77 -> "天色良好"
            76 -> "大致多雲"
            80 -> "大風"
            81 -> "乾燥"
            82 -> "潮濕"
            83, 84, 85 -> "有霧"
            90, 91 -> "炎熱"
            92, 93 -> "清涼"
            else -> "多雲"
        }

        private fun mapWmoCode(code: Int): String = when (code) {
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
