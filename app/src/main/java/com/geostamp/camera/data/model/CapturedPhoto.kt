package com.geostamp.camera.data.model

import android.net.Uri

data class CapturedPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val timestamp: Long,
    val size: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val address: String? = null
)
