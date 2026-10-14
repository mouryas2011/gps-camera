package com.geostamp.camera.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherSnapshot(
    val temperatureCelsius: Float = 29.97f,
    val humidityPercent: Int = 53,
    val windSpeedKmh: Float = 7.42f,
    val weatherEmoji: String = "☀️",
    val description: String = "Clear Sky"
)

object WeatherHelper {

    private var cachedWeather: WeatherSnapshot? = null
    private var lastFetchTime: Long = 0
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    suspend fun getWeather(latitude: Double, longitude: Double): WeatherSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedWeather != null && (now - lastFetchTime < 10 * 60 * 1000) &&
            Math.abs(latitude - lastLat) < 0.05 && Math.abs(longitude - lastLon) < 0.05
        ) {
            return@withContext cachedWeather!!
        }

        try {
            val urlString = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code",
                latitude,
                longitude
            )
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m").toFloat()
                val humidity = current.getInt("relative_humidity_2m")
                val wind = current.getDouble("wind_speed_10m").toFloat()
                val code = current.optInt("weather_code", 0)

                val (emoji, desc) = getWeatherEmojiAndDesc(code)

                val result = WeatherSnapshot(
                    temperatureCelsius = temp,
                    humidityPercent = humidity,
                    windSpeedKmh = wind,
                    weatherEmoji = emoji,
                    description = desc
                )

                cachedWeather = result
                lastFetchTime = now
                lastLat = latitude
                lastLon = longitude

                return@withContext result
            }
        } catch (_: Exception) {
            // Graceful fallback on network failure / offline
        }

        return@withContext cachedWeather ?: WeatherSnapshot()
    }

    private fun getWeatherEmojiAndDesc(code: Int): Pair<String, String> {
        return when (code) {
            0 -> "☀️" to "Sunny"
            1, 2 -> "🌤️" to "Partly Cloudy"
            3 -> "☁️" to "Overcast"
            45, 48 -> "🌫️" to "Foggy"
            51, 53, 55, 61, 63, 65 -> "🌧️" to "Rain"
            71, 73, 75 -> "❄️" to "Snow"
            95, 96, 99 -> "⛈️" to "Thunderstorm"
            else -> "☀️" to "Clear"
        }
    }
}
