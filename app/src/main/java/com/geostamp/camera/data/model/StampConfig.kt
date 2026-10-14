package com.geostamp.camera.data.model

import java.util.Locale
import kotlin.math.abs

enum class StampPosition {
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_LEFT,
    TOP_RIGHT
}

enum class StampStyle {
    CLASSIC_MAP_BADGE, // Classic GPS Map Camera reference layout with Map on left and info on right
    FROSTED_GLASS,
    HIGH_CONTRAST_DARK,
    CLEAN_OUTLINE
}

enum class CoordinateFormat {
    DECIMAL,
    DMS
}

data class StampConfig(
    val position: StampPosition = StampPosition.BOTTOM_LEFT,
    val style: StampStyle = StampStyle.CLASSIC_MAP_BADGE,
    val showDateTime: Boolean = true,
    val showCoordinates: Boolean = true,
    val showAddress: Boolean = true,
    val showAccuracy: Boolean = true,
    val showAltitude: Boolean = true,
    val showMapSnapshot: Boolean = true,
    val showPlusCode: Boolean = true,
    val showWeather: Boolean = true,
    val showCompass: Boolean = true,
    val showSensors: Boolean = true,
    val showCustomTitle: Boolean = true,
    val customTitle: String = "GPS Map Camera",
    val personName: String = "-",
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val dateTimePattern: String = "EEEE, dd/MM/yyyy hh:mm a 'GMT' Z"
) {
    companion object {
        fun formatCoordinates(
            latitude: Double,
            longitude: Double,
            format: CoordinateFormat
        ): String {
            return when (format) {
                CoordinateFormat.DECIMAL -> {
                    String.format(
                        Locale.US,
                        "Lat %.6f° Long %.6f°",
                        latitude,
                        longitude
                    )
                }
                CoordinateFormat.DMS -> {
                    val latDms = toDms(latitude, isLatitude = true)
                    val lonDms = toDms(longitude, isLatitude = false)
                    "$latDms, $lonDms"
                }
            }
        }

        private fun toDms(value: Double, isLatitude: Boolean): String {
            val direction = if (isLatitude) {
                if (value >= 0) "N" else "S"
            } else {
                if (value >= 0) "E" else "W"
            }
            val absolute = abs(value)
            val degrees = absolute.toInt()
            val minutesDecimal = (absolute - degrees) * 60
            val minutes = minutesDecimal.toInt()
            val seconds = (minutesDecimal - minutes) * 60
            return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, direction)
        }
    }
}
