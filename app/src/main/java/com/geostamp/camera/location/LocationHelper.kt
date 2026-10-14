package com.geostamp.camera.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double?,
    val isFresh: Boolean,
    val timestamp: Long,
    val provider: String?
)

sealed interface LocationState {
    object Idle : LocationState
    object Acquiring : LocationState
    object PermissionRequired : LocationState
    object LocationDisabled : LocationState
    data class Ready(val snapshot: LocationSnapshot) : LocationState
    data class Unavailable(val reason: String) : LocationState
}

class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _state = MutableStateFlow<LocationState>(LocationState.Idle)
    val state: StateFlow<LocationState> = _state.asStateFlow()

    private var fusedCallback: LocationCallback? = null
    private var frameworkListener: LocationListener? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            gps || network
        }
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (!hasLocationPermission()) {
            _state.value = LocationState.PermissionRequired
            return
        }

        if (!isLocationEnabled()) {
            _state.value = LocationState.LocationDisabled
            return
        }

        _state.value = LocationState.Acquiring

        // Check last known location first for immediate responsiveness
        try {
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = pickBestLocation(lastGps, lastNetwork)
            if (bestLast != null) {
                val ageMs = System.currentTimeMillis() - bestLast.time
                val isFresh = ageMs < 15_000
                _state.value = LocationState.Ready(bestLast.toSnapshot(isFresh))
            }
        } catch (_: Exception) {
            // Non-critical, monitoring continues
        }

        // Start continuous fused updates
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(1.0f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    val ageMs = System.currentTimeMillis() - loc.time
                    val isFresh = ageMs < 10_000
                    _state.value = LocationState.Ready(loc.toSnapshot(isFresh))
                }
            }
            fusedCallback = callback
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: Exception) {
            // Fallback to framework LocationManager
            startFrameworkMonitoring()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFrameworkMonitoring() {
        if (!hasLocationPermission()) return

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val ageMs = System.currentTimeMillis() - location.time
                val isFresh = ageMs < 10_000
                _state.value = LocationState.Ready(location.toSnapshot(isFresh))
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                if (!isLocationEnabled()) {
                    _state.value = LocationState.LocationDisabled
                }
            }
        }
        frameworkListener = listener
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            _state.value = LocationState.Unavailable(e.localizedMessage ?: "Location provider error")
        }
    }

    fun stopMonitoring() {
        fusedCallback?.let {
            try {
                fusedClient.removeLocationUpdates(it)
            } catch (_: Exception) {}
            fusedCallback = null
        }
        frameworkListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (_: Exception) {}
            frameworkListener = null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshFixForCapture(timeoutMillis: Long = 4000L): LocationSnapshot? =
        withContext(Dispatchers.IO) {
            if (!hasLocationPermission() || !isLocationEnabled()) {
                return@withContext null
            }

            // 1. Try to obtain fresh high-accuracy location via FusedLocationProviderClient
            try {
                val cancellationToken = CancellationTokenSource()
                val freshLoc = withTimeoutOrNull(timeoutMillis) {
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationToken.token
                    ).await()
                }

                if (freshLoc != null) {
                    val ageMs = System.currentTimeMillis() - freshLoc.time
                    val isFresh = ageMs < 15_000
                    return@withContext freshLoc.toSnapshot(isFresh)
                }
            } catch (_: Exception) {
                // Proceed to fallback
            }

            // 2. Fallback to last known location from Fused Client
            try {
                val lastLoc = fusedClient.lastLocation.await()
                if (lastLoc != null) {
                    val ageMs = System.currentTimeMillis() - lastLoc.time
                    val isFresh = ageMs < 20_000
                    return@withContext lastLoc.toSnapshot(isFresh)
                }
            } catch (_: Exception) {
                // Proceed to LocationManager fallback
            }

            // 3. Fallback to framework LocationManager
            try {
                val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val best = pickBestLocation(gps, net)
                if (best != null) {
                    val ageMs = System.currentTimeMillis() - best.time
                    val isFresh = ageMs < 20_000
                    return@withContext best.toSnapshot(isFresh)
                }
            } catch (_: Exception) {
                // Return null if all location queries fail
            }

            return@withContext null
        }

    private fun pickBestLocation(loc1: Location?, loc2: Location?): Location? {
        if (loc1 == null) return loc2
        if (loc2 == null) return loc1
        // Pick newer or more accurate
        val timeDiff = loc1.time - loc2.time
        return if (timeDiff > 30_000) {
            loc1
        } else if (timeDiff < -30_000) {
            loc2
        } else {
            if (loc1.accuracy <= loc2.accuracy) loc1 else loc2
        }
    }

    private fun Location.toSnapshot(isFresh: Boolean): LocationSnapshot {
        return LocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else 0f,
            altitudeMeters = if (hasAltitude()) altitude else null,
            isFresh = isFresh,
            timestamp = time,
            provider = provider
        )
    }
}
