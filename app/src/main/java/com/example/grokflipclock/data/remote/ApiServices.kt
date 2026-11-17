package com.example.grokflipclock.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// --- xAI Grok Chat Models (OpenAI-Compatible) ---
data class AiMessage(
    val role: String,
    val content: String
)

data class AiChatRequest(
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Double = 0.7
)

data class AiChoice(
    val index: Int,
    val message: AiMessage
)

data class AiChatResponse(
    val choices: List<AiChoice>
)

// --- Google Gemini Native Models ---
data class GeminiPart(
    val text: String
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.7
)

data class GeminiGenerateRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiCandidate(
    val content: GeminiContent?
)

data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>?
)

interface AiChatApiService {
    @POST
    suspend fun createChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: AiChatRequest
    ): AiChatResponse

    @POST
    suspend fun generateGeminiContent(
        @Url url: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse
}

// --- Hong Kong Observatory (HKO) Open Data Models ---
data class HkoCurrentWeatherResponse(
    val temperature: HkoTemperatureContainer?,
    val icon: List<Int>?,
    val humidity: HkoHumidityContainer?
)

data class HkoTemperatureContainer(
    val data: List<HkoPlaceValue>?,
    val recordTime: String?
)

data class HkoHumidityContainer(
    val data: List<HkoPlaceValue>?,
    val recordTime: String?
)

data class HkoPlaceValue(
    val place: String?,
    val value: Double?,
    val unit: String?
)

interface HkoWeatherApiService {
    @GET("weatherAPI/opendata/weather.php")
    suspend fun getCurrentRegionalWeather(
        @Query("dataType") dataType: String = "rhrread",
        @Query("lang") lang: String = "tc"
    ): HkoCurrentWeatherResponse
}

// --- Open-Meteo Weather & Geocoding Models ---
data class OpenMeteoResponse(
    @SerializedName("current_weather") val currentWeather: CurrentWeatherDto?,
    val hourly: HourlyWeatherDto?
)

data class CurrentWeatherDto(
    val temperature: Double,
    val weathercode: Int,
    val windspeed: Double
)

data class HourlyWeatherDto(
    val time: List<String>?,
    @SerializedName("temperature_2m") val temperature2m: List<Double>?,
    val weathercode: List<Int>?
)

data class GeocodingResponse(
    val results: List<GeocodingResult>?
)

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?
)

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("hourly") hourly: String = "temperature_2m,weathercode",
        @Query("models") models: String = "jma_seamless",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 2
    ): OpenMeteoResponse
}

interface GeocodingApiService {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") cityName: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

object NetworkModule {
    const val GROK_CHAT_URL = "https://api.x.ai/v1/chat/completions"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    val aiChatApi: AiChatApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.x.ai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiChatApiService::class.java)
    }

    val hkoWeatherApi: HkoWeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://data.weather.gov.hk/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HkoWeatherApiService::class.java)
    }

    val weatherApi: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    val geocodingApi: GeocodingApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingApiService::class.java)
    }
}
