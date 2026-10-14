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
    val style: StampStyle = StampStyle.FROSTED_GLASS,
    val showDateTime: Boolean = true,
    val showCoordinates: Boolean = true,
    val showAddress: Boolean = true,
    val showAccuracy: Boolean = true,
    val showAltitude: Boolean = true,
    val showCustomTitle: Boolean = true,
    val customTitle: String = "GPS Map Camera",
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val dateTimePattern: String = "yyyy-MM-dd HH:mm:ss"
) {
    companion object {
        fun formatCoordinates(
            latitude: Double,
            longitude: Double,
            format: CoordinateFormat
        ): String {
            return when (format) {
                CoordinateFormat.DECIMAL -> {
                    val latDir = if (latitude >= 0) "N" else "S"
                    val lonDir = if (longitude >= 0) "E" else "W"
                    String.format(
                        Locale.US,
                        "%.6f° %s, %.6f° %s",
                        abs(latitude),
                        latDir,
                        abs(longitude),
                        lonDir
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
