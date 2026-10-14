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
        val styleStr = prefs.getString("stamp_style", StampStyle.FROSTED_GLASS.name)
        val coordFormatStr = prefs.getString("coord_format", CoordinateFormat.DECIMAL.name)

        val position = try {
            StampPosition.valueOf(positionStr ?: StampPosition.BOTTOM_LEFT.name)
        } catch (_: Exception) {
            StampPosition.BOTTOM_LEFT
        }

        val style = try {
            StampStyle.valueOf(styleStr ?: StampStyle.FROSTED_GLASS.name)
        } catch (_: Exception) {
            StampStyle.FROSTED_GLASS
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
            showCustomTitle = prefs.getBoolean("show_custom_title", true),
            customTitle = prefs.getString("custom_title", "GPS Map Camera") ?: "GPS Map Camera",
            coordinateFormat = coordFormat,
            dateTimePattern = prefs.getString("datetime_pattern", "yyyy-MM-dd HH:mm:ss") ?: "yyyy-MM-dd HH:mm:ss"
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
            putBoolean("show_custom_title", config.showCustomTitle)
            putString("custom_title", config.customTitle)
            putString("coord_format", config.coordinateFormat.name)
            putString("datetime_pattern", config.dateTimePattern)
            apply()
        }
        _configFlow.value = config
    }
}
