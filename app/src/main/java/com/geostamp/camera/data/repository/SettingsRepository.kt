package com.geostamp.camera.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.geostamp.camera.data.model.AspectRatioOption
import com.geostamp.camera.data.model.CoordinateFormat
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampFontColor
import com.geostamp.camera.data.model.StampFontSize
import com.geostamp.camera.data.model.StampLayout
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampSize
import com.geostamp.camera.data.model.StampStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val configFlow: StateFlow<StampConfig> = getOrCreateFlow(prefs)

    private fun loadConfig(): StampConfig = loadConfigFromPrefs(prefs)

    fun updateConfig(config: StampConfig) {
        prefs.edit().apply {
            putString("stamp_position", config.position.name)
            putString("stamp_style", config.style.name)
            putString("aspect_ratio", config.aspectRatio.name)
            putString("stamp_layout", config.layout.name)
            putString("stamp_size", config.stampSize.name)
            putFloat("background_alpha", config.backgroundAlpha)
            putString("font_size", config.fontSize.name)
            putString("font_color", config.fontColor.name)
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
            putBoolean("show_captured_by_note", config.showCapturedByNote)
            putString("custom_title", config.customTitle)
            putString("person_name", config.personName)
            putString("company_name", config.companyName)
            putString("notes", config.notes)
            putString("coord_format", config.coordinateFormat.name)
            putString("datetime_pattern", config.dateTimePattern)
            apply()
        }
        sharedConfigFlow?.value = config
    }

    fun resetToDefaults(): StampConfig {
        val defaultConfig = StampConfig()
        updateConfig(defaultConfig)
        return defaultConfig
    }

    companion object {
        private const val PREFS_NAME = "gps_camera_prefs"

        @Volatile
        private var sharedConfigFlow: MutableStateFlow<StampConfig>? = null

        private fun getOrCreateFlow(prefs: SharedPreferences): StateFlow<StampConfig> {
            val existing = sharedConfigFlow
            if (existing != null) return existing.asStateFlow()
            return synchronized(this) {
                val current = sharedConfigFlow
                if (current != null) {
                    current.asStateFlow()
                } else {
                    val created = MutableStateFlow(loadConfigFromPrefs(prefs))
                    sharedConfigFlow = created
                    created.asStateFlow()
                }
            }
        }

        private fun loadConfigFromPrefs(prefs: SharedPreferences): StampConfig {
            val rebranded = prefs.getBoolean("rebrand_migrated", false)
            val positionStr = prefs.getString("stamp_position", StampPosition.BOTTOM_RIGHT.name)
            val styleStr = prefs.getString("stamp_style", StampStyle.CLASSIC_MAP_BADGE.name)
            val coordFormatStr = prefs.getString("coord_format", CoordinateFormat.DECIMAL.name)
            val aspectStr = prefs.getString("aspect_ratio", AspectRatioOption.RATIO_4_3.name)

            var position = try {
                StampPosition.valueOf(positionStr ?: StampPosition.BOTTOM_RIGHT.name)
            } catch (_: Exception) {
                StampPosition.BOTTOM_RIGHT
            }

            var customTitle = prefs.getString("custom_title", "GeoStamp Camera") ?: "GeoStamp Camera"

            if (!rebranded) {
                // One-time rebrand migration: old default stamp was bottom-left and titled "GeoStamp Camera"
                if (position == StampPosition.BOTTOM_LEFT) position = StampPosition.BOTTOM_RIGHT
                if (customTitle == "GeoStamp Camera") customTitle = "GeoStamp Camera"
                prefs.edit()
                    .putBoolean("rebrand_migrated", true)
                    .putString("stamp_position", position.name)
                    .putString("custom_title", customTitle)
                    .apply()
            }

            // One-time watermark v2 migration: the new premium reference layout shows only
            // the essential GPS fields by default. Optional extras remain available in Settings.
            val watermarkV2 = prefs.getBoolean("watermark_v2_migrated", false)
            if (!watermarkV2) {
                prefs.edit()
                    .putBoolean("watermark_v2_migrated", true)
                    .putString("stamp_layout", StampLayout.HORIZONTAL.name)
                    .putString("stamp_size", StampSize.MEDIUM.name)
                    .putFloat("background_alpha", 0.78f)
                    .putString("font_size", StampFontSize.MEDIUM.name)
                    .putString("font_color", StampFontColor.WHITE.name)
                    .putBoolean("show_accuracy", false)
                    .putBoolean("show_altitude", false)
                    .putBoolean("show_plus_code", false)
                    .putBoolean("show_weather", false)
                    .putBoolean("show_compass", false)
                    .putBoolean("show_sensors", false)
                    .putBoolean("show_captured_by_note", true)
                    .apply()
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

            val aspectRatio = try {
                AspectRatioOption.valueOf(aspectStr ?: AspectRatioOption.RATIO_4_3.name)
            } catch (_: Exception) {
                AspectRatioOption.RATIO_4_3
            }

            val layoutStr = prefs.getString("stamp_layout", StampLayout.HORIZONTAL.name)
            val layout = try {
                StampLayout.valueOf(layoutStr ?: StampLayout.HORIZONTAL.name)
            } catch (_: Exception) {
                StampLayout.HORIZONTAL
            }

            val sizeStr = prefs.getString("stamp_size", StampSize.MEDIUM.name)
            val stampSize = try {
                StampSize.valueOf(sizeStr ?: StampSize.MEDIUM.name)
            } catch (_: Exception) {
                StampSize.MEDIUM
            }

            val bgAlpha = prefs.getFloat("background_alpha", 0.78f).coerceIn(0.35f, 0.95f)

            val fontSizeStr = prefs.getString("font_size", StampFontSize.MEDIUM.name)
            val fontSize = try {
                StampFontSize.valueOf(fontSizeStr ?: StampFontSize.MEDIUM.name)
            } catch (_: Exception) {
                StampFontSize.MEDIUM
            }

            val fontColorStr = prefs.getString("font_color", StampFontColor.WHITE.name)
            val fontColor = try {
                StampFontColor.valueOf(fontColorStr ?: StampFontColor.WHITE.name)
            } catch (_: Exception) {
                StampFontColor.WHITE
            }

            return StampConfig(
                position = position,
                style = style,
                aspectRatio = aspectRatio,
                layout = layout,
                stampSize = stampSize,
                backgroundAlpha = bgAlpha,
                fontSize = fontSize,
                fontColor = fontColor,
                showDateTime = prefs.getBoolean("show_datetime", true),
                showCoordinates = prefs.getBoolean("show_coordinates", true),
                showAddress = prefs.getBoolean("show_address", true),
                showAccuracy = prefs.getBoolean("show_accuracy", false),
                showAltitude = prefs.getBoolean("show_altitude", false),
                showMapSnapshot = prefs.getBoolean("show_map_snapshot", true),
                showPlusCode = prefs.getBoolean("show_plus_code", false),
                showWeather = prefs.getBoolean("show_weather", false),
                showCompass = prefs.getBoolean("show_compass", false),
                showSensors = prefs.getBoolean("show_sensors", false),
                showCustomTitle = prefs.getBoolean("show_custom_title", true),
                showCapturedByNote = prefs.getBoolean("show_captured_by_note", true),
                customTitle = customTitle,
                personName = prefs.getString("person_name", "-") ?: "-",
                companyName = prefs.getString("company_name", "") ?: "",
                notes = prefs.getString("notes", "") ?: "",
                coordinateFormat = coordFormat,
                dateTimePattern = prefs.getString("datetime_pattern", "EEEE, dd/MM/yyyy hh:mm a 'GMT'XXX")
                    ?: "EEEE, dd/MM/yyyy hh:mm a 'GMT'XXX"
            )
        }
    }
}
