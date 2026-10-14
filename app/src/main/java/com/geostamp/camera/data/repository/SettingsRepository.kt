package com.geostamp.camera.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.geostamp.camera.data.model.CoordinateFormat
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gps_camera_prefs", Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<StampConfig> = _configFlow.asStateFlow()

    private fun loadConfig(): StampConfig {
        val positionStr = prefs.getString("stamp_position", StampPosition.BOTTOM_LEFT.name)
        val styleStr = prefs.getString("stamp_style", StampStyle.CLASSIC_MAP_BADGE.name)
        val coordFormatStr = prefs.getString("coord_format", CoordinateFormat.DECIMAL.name)

        val position = try {
            StampPosition.valueOf(positionStr ?: StampPosition.BOTTOM_LEFT.name)
        } catch (_: Exception) {
            StampPosition.BOTTOM_LEFT
        }

        val style = try {
            StampStyle.valueOf(styleStr ?: StampStyle.CLASSIC_MAP_BADGE.name)
        } catch (_: Exception) {
            StampStyle.CLASSIC_MAP_BADGE
        }

        val coordFormat = try {
            CoordinateFormat.valueOf(coordFormatStr ?: CoordinateFormat.DECIMAL.name)
        } catch (_: Exception) {
            CoordinateFormat.DECIMAL
        }

        return StampConfig(
            position = position,
            style = style,
            showDateTime = prefs.getBoolean("show_datetime", true),
            showCoordinates = prefs.getBoolean("show_coordinates", true),
            showAddress = prefs.getBoolean("show_address", true),
            showAccuracy = prefs.getBoolean("show_accuracy", true),
            showAltitude = prefs.getBoolean("show_altitude", true),
            showMapSnapshot = prefs.getBoolean("show_map_snapshot", true),
            showPlusCode = prefs.getBoolean("show_plus_code", true),
            showWeather = prefs.getBoolean("show_weather", true),
            showCompass = prefs.getBoolean("show_compass", true),
            showSensors = prefs.getBoolean("show_sensors", true),
            showCustomTitle = prefs.getBoolean("show_custom_title", true),
            customTitle = prefs.getString("custom_title", "GPS Map Camera") ?: "GPS Map Camera",
            personName = prefs.getString("person_name", "-") ?: "-",
            coordinateFormat = coordFormat,
            dateTimePattern = prefs.getString("datetime_pattern", "EEEE, dd/MM/yyyy hh:mm a 'GMT' Z") ?: "EEEE, dd/MM/yyyy hh:mm a 'GMT' Z"
        )
    }

    fun updateConfig(config: StampConfig) {
        prefs.edit().apply {
            putString("stamp_position", config.position.name)
            putString("stamp_style", config.style.name)
            putBoolean("show_datetime", config.showDateTime)
            putBoolean("show_coordinates", config.showCoordinates)
            putBoolean("show_address", config.showAddress)
            putBoolean("show_accuracy", config.showAccuracy)
            putBoolean("show_altitude", config.showAltitude)
            putBoolean("show_map_snapshot", config.showMapSnapshot)
            putBoolean("show_plus_code", config.showPlusCode)
            putBoolean("show_weather", config.showWeather)
            putBoolean("show_compass", config.showCompass)
            putBoolean("show_sensors", config.showSensors)
            putBoolean("show_custom_title", config.showCustomTitle)
            putString("custom_title", config.customTitle)
            putString("person_name", config.personName)
            putString("coord_format", config.coordinateFormat.name)
            putString("datetime_pattern", config.dateTimePattern)
            apply()
        }
        _configFlow.value = config
    }
}
