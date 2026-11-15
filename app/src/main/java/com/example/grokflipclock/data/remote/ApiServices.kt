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
import java.util.concurrent.TimeUnit

// --- xAI Grok API Models ---
data class GrokMessage(
    val role: String,
    val content: String
)

data class GrokChatRequest(
    val model: String,
    val messages: List<GrokMessage>,
    val temperature: Double = 0.7
)

data class GrokChoice(
    val index: Int,
    val message: GrokMessage
)

data class GrokChatResponse(
    val choices: List<GrokChoice>
)

interface GrokApiService {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: GrokChatRequest
    ): GrokChatResponse
}

// --- Open-Meteo Weather & Geocoding Models ---
data class OpenMeteoResponse(
    @SerializedName("current_weather") val currentWeather: CurrentWeatherDto?
)

data class CurrentWeatherDto(
    val temperature: Double,
    val weathercode: Int,
    val windspeed: Double
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
        @Query("current_weather") currentWeather: Boolean = true
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

    val grokApi: GrokApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.x.ai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GrokApiService::class.java)
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
