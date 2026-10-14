package com.geostamp.camera.location

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * Open Location Code (Plus Code) implementation in pure Kotlin.
 * Converts latitude and longitude into standard Plus Codes like "7MJ49PFP+J2".
 */
object PlusCodeHelper {

    private const val CODE_ALPHABET = "23456789CFGHJMPQRVWX"
    private const val ENCODING_BASE = 20
    private const val LATITUDE_MAX = 90.0
    private const val LONGITUDE_MAX = 180.0
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POSITION = 8
    private const val PAIR_CODE_LENGTH = 10

    private val PAIR_RESOLUTIONS = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)

    fun encode(latitude: Double, longitude: Double, codeLength: Int = 10): String {
        val lat = latitude.coerceIn(-LATITUDE_MAX, LATITUDE_MAX)
        val lon = normalizeLongitude(longitude)

        // Adjust latitude if at 90
        val adjustedLat = if (lat == LATITUDE_MAX) lat - 0.0000001 else lat

        var latVal = adjustedLat + LATITUDE_MAX
        var lonVal = lon + LONGITUDE_MAX

        val code = StringBuilder()

        for (i in 0 until min(codeLength, PAIR_CODE_LENGTH) step 2) {
            val res = PAIR_RESOLUTIONS[i / 2]
            val latDigit = floor(latVal / res).toInt().coerceIn(0, ENCODING_BASE - 1)
            val lonDigit = floor(lonVal / res).toInt().coerceIn(0, ENCODING_BASE - 1)

            latVal -= latDigit * res
            lonVal -= lonDigit * res

            code.append(CODE_ALPHABET[latDigit])
            code.append(CODE_ALPHABET[lonDigit])

            if (code.length == SEPARATOR_POSITION) {
                code.append(SEPARATOR)
            }
        }

        if (code.length <= SEPARATOR_POSITION) {
            while (code.length < SEPARATOR_POSITION) {
                code.append('0')
            }
            code.append(SEPARATOR)
        }

        return code.toString()
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var lon = longitude
        while (lon < -LONGITUDE_MAX) lon += 360.0
        while (lon >= LONGITUDE_MAX) lon -= 360.0
        return lon
    }
}
