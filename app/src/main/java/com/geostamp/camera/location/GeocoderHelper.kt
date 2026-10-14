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

object GeocoderHelper {

    // Simple cache keyed by rounded lat,lon (approx 100m)
    private val addressCache = mutableMapOf<String, String>()

    suspend fun getReadableAddress(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            return@withContext null
        }

        val cacheKey = String.format(Locale.US, "%.3f,%.3f", latitude, longitude)
        synchronized(addressCache) {
            addressCache[cacheKey]?.let { return@withContext it }
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
                                continuation.resume(addresses.firstOrNull())
                            }
                            override fun onError(errorMessage: String?) {
                                continuation.resume(null)
                            }
                        }
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(latitude, longitude, 1)
                results?.firstOrNull()
            }

            val formatted = addressResult?.let { formatAddress(it) }
            if (formatted != null) {
                synchronized(addressCache) {
                    if (addressCache.size > 50) addressCache.clear()
                    addressCache[cacheKey] = formatted
                }
            }
            formatted
        } catch (_: Exception) {
            // Geocoding can fail when offline or rate-limited; gracefully fallback to coordinates
            null
        }
    }

    private fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()
        
        // Thoroughfare / Street
        val street = when {
            address.subThoroughfare != null && address.thoroughfare != null ->
                "${address.subThoroughfare} ${address.thoroughfare}"
            address.thoroughfare != null -> address.thoroughfare
            address.featureName != null && address.featureName != address.locality -> address.featureName
            else -> null
        }
        if (!street.isNullOrBlank()) parts.add(street)

        // Locality / Sub-locality
        val locality = address.subLocality ?: address.locality
        if (!locality.isNullOrBlank() && !parts.contains(locality)) parts.add(locality)

        // City / Admin area
        val city = address.locality ?: address.subAdminArea ?: address.adminArea
        if (!city.isNullOrBlank() && !parts.contains(city)) parts.add(city)

        // Country
        val country = address.countryName
        if (!country.isNullOrBlank() && !parts.contains(country)) parts.add(country)

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            // Fallback to address lines
            (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it) }.joinToString(", ")
        }
    }
}
