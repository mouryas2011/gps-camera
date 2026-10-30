package com.geostamp.camera.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class StampPosition {
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_LEFT,
    TOP_RIGHT
}

enum class StampStyle {
    CLASSIC_MAP_BADGE, // Classic GeoStamp Camera reference layout with map on the right and info on the left
    MODERN_MINIMAL,    // Sleek modern card with rounded mini-map, clean typography & climate pill
    SURVEYOR_GRID,     // Technical inspection/engineering table grid with boxed metadata cells
    FROSTED_GLASS,     // Translucent dark glass badge with neon cyan accent border
    HIGH_CONTRAST_DARK,// Deep solid black card with high-vis golden yellow & white text
    CLEAN_OUTLINE      // Minimalist text with high-contrast shadow overlay directly on photo
}

enum class AspectRatioOption(val label: String, val ratioX: Int, val ratioY: Int) {
    RATIO_4_3("4:3", 4, 3),
    RATIO_16_9("16:9", 16, 9),
    RATIO_1_1("1:1", 1, 1)
}

enum class CoordinateFormat {
    DECIMAL,
    DMS
}

enum class StampLayout(val label: String) {
    HORIZONTAL("Horizontal"),
    VERTICAL("Vertical")
}

enum class StampSize(val label: String, val scaleFactor: Float) {
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f)
}

enum class StampFontSize(val label: String, val scaleFactor: Float) {
    SMALL("Small", 0.88f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.14f)
}

enum class StampFontColor(val label: String, val hex: String) {
    WHITE("White", "#FFFFFF"),
    CREAM("Warm White", "#FFF8E1"),
    GOLD("Gold", "#FFD54F"),
    CYAN("Cyan", "#80DEEA"),
    GREEN("Mint Green", "#B9F6CA")
}

data class StampConfig(
    val position: StampPosition = StampPosition.BOTTOM_RIGHT,
    val style: StampStyle = StampStyle.CLASSIC_MAP_BADGE,
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_4_3,
    val layout: StampLayout = StampLayout.HORIZONTAL,
    val stampSize: StampSize = StampSize.MEDIUM,
    val backgroundAlpha: Float = 0.78f,
    val fontSize: StampFontSize = StampFontSize.MEDIUM,
    val fontColor: StampFontColor = StampFontColor.WHITE,
    val showDateTime: Boolean = true,
    val showCoordinates: Boolean = true,
    val showAddress: Boolean = true,
    val showAccuracy: Boolean = false,
    val showAltitude: Boolean = false,
    val showMapSnapshot: Boolean = true,
    val showPlusCode: Boolean = false,
    val showWeather: Boolean = false,
    val showCompass: Boolean = false,
    val showSensors: Boolean = false,
    val showCustomTitle: Boolean = true,
    val showCapturedByNote: Boolean = true,
    val customTitle: String = "GeoStamp Camera",
    val personName: String = "-",
    val companyName: String = "",
    val notes: String = "",
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val dateTimePattern: String = "EEEE, dd/MM/yyyy hh:mm a 'GMT' XXX"
) {
    companion object {
        fun formatNoteLine(config: StampConfig): String {
            return noteLine(config) ?: ""
        }

        /**
         * Returns the optional "Note:" line for the watermark, or null when the user
         * disabled the captured-by note and no custom note was entered.
         */
        fun noteLine(config: StampConfig): String? {
            val customNote = config.notes.trim()
            if (customNote.isNotEmpty()) {
                return if (customNote.startsWith("Note:", ignoreCase = true)) {
                    customNote
                } else {
                    "Note: $customNote"
                }
            }
            if (!config.showCapturedByNote) return null
            return "Note: Captured by GeoStamp Camera"
        }

        fun formatDateTime(timestampMillis: Long, pattern: String): String {
            val effectivePattern = if (pattern.endsWith("'GMT' Z")) {
                pattern.removeSuffix("'GMT' Z") + "'GMT' XXX"
            } else {
                pattern
            }
            val sdf = try {
                SimpleDateFormat(effectivePattern, Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }
            } catch (_: Exception) {
                SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a 'GMT' XXX", Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }
            }
            return sdf.format(Date(timestampMillis))
        }

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
