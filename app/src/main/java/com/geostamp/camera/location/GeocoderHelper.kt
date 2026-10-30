package com.geostamp.camera.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class DetailedAddress(
    val shortHeader: String,
    val fullStreetAddress: String,
    val countryFlag: String = "📍"
)

object GeocoderHelper {

    private val detailedCache = mutableMapOf<String, DetailedAddress>()

    suspend fun getDetailedAddress(
        context: Context,
        latitude: Double,
        longitude: Double
    ): DetailedAddress? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null

        val cacheKey = String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
        synchronized(detailedCache) {
            detailedCache[cacheKey]?.let { return@withContext it }
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addressResult: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) {
                                    continuation.resume(addresses.firstOrNull())
                                }
                            }
                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(latitude, longitude, 1)
                results?.firstOrNull()
            }

            if (addressResult != null) {
                val detailed = formatDetailed(addressResult)
                synchronized(detailedCache) {
                    if (detailedCache.size > 50) detailedCache.clear()
                    detailedCache[cacheKey] = detailed
                }
                detailed
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getReadableAddress(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? {
        return getDetailedAddress(context, latitude, longitude)?.fullStreetAddress
    }

    private fun formatDetailed(address: Address): DetailedAddress {
        val flag = getCountryFlag(address.countryCode)

        val city = address.locality ?: address.subAdminArea ?: address.subLocality
        val state = address.adminArea
        val country = address.countryName

        val headerParts = listOfNotNull(
            city?.takeIf { it.isNotBlank() },
            state?.takeIf { it.isNotBlank() },
            country?.takeIf { it.isNotBlank() }
        ).distinct()

        val headerBase = if (headerParts.isNotEmpty()) {
            headerParts.joinToString(", ")
        } else {
            address.featureName?.takeIf { it.isNotBlank() } ?: "GPS Location"
        }
        val header = if (flag.isNotBlank()) "$headerBase $flag" else headerBase

        val parts = mutableListOf<String>()

        val street = when {
            !address.subThoroughfare.isNullOrBlank() && !address.thoroughfare.isNullOrBlank() ->
                "${address.subThoroughfare}, ${address.thoroughfare}"
            !address.thoroughfare.isNullOrBlank() -> address.thoroughfare
            !address.featureName.isNullOrBlank() && address.featureName != city -> address.featureName
            else -> null
        }
        if (!street.isNullOrBlank()) parts.add(street)

        val subLocality = address.subLocality
        if (!subLocality.isNullOrBlank() && !parts.contains(subLocality)) parts.add(subLocality)

        if (!city.isNullOrBlank() && !parts.contains(city)) parts.add(city)

        if (!state.isNullOrBlank()) {
            val stateAndPin = if (!address.postalCode.isNullOrBlank()) {
                "$state ${address.postalCode}"
            } else {
                state
            }
            if (!parts.contains(stateAndPin)) parts.add(stateAndPin)
        } else if (!address.postalCode.isNullOrBlank()) {
            parts.add(address.postalCode)
        }

        if (!country.isNullOrBlank() && !parts.contains(country)) parts.add(country)

        val fullAddress = if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            (0..address.maxAddressLineIndex)
                .mapNotNull { address.getAddressLine(it)?.takeIf { line -> line.isNotBlank() } }
                .joinToString(", ")
                .ifBlank { headerBase }
        }

        return DetailedAddress(
            shortHeader = header,
            fullStreetAddress = fullAddress,
            countryFlag = flag
        )
    }

    private fun getCountryFlag(countryCode: String?): String {
        if (countryCode.isNullOrBlank()) return "📍"
        return try {
            val upper = countryCode.trim().uppercase(Locale.US)
            if (upper.length == 2 && upper[0] in 'A'..'Z' && upper[1] in 'A'..'Z') {
                val firstChar = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
                val secondChar = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
                String(firstChar) + String(secondChar)
            } else "📍"
        } catch (_: Exception) {
            "📍"
        }
    }
}
