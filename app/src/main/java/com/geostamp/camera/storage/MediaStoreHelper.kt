package com.geostamp.camera.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.data.model.CapturedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreHelper {

    private const val ALBUM_NAME = "GeoStamp Camera"
    private val memoryCache = mutableListOf<CapturedPhoto>()

    suspend fun savePhoto(
        context: Context,
        jpegBytes: ByteArray,
        timestamp: Long,
        latitude: Double?,
        longitude: Double?,
        address: String?,
        accuracy: Float?,
        altitude: Double?,
        folderName: String = "Default"
    ): CapturedPhoto = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "GPS_IMG_${dateFormat.format(Date(timestamp))}.jpg"

        val cleanFolder = folderName.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "")
        val relativeSubPath = if (cleanFolder.isBlank() || cleanFolder.equals("Default", ignoreCase = true)) {
            "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
        } else {
            "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME/$cleanFolder"
        }

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativeSubPath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collectionUri, contentValues)
            ?: throw IllegalStateException("Failed to insert MediaStore record")

        try {
            resolver.openOutputStream(itemUri)?.use { output ->
                output.write(jpegBytes)
                output.flush()
            } ?: throw IllegalStateException("Failed to open output stream for $itemUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }

        val id = ContentUris.parseId(itemUri)
        val photo = CapturedPhoto(
            id = id,
            uri = itemUri,
            displayName = fileName,
            timestamp = timestamp,
            size = jpegBytes.size.toLong(),
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            altitude = altitude,
            address = address
        )

        synchronized(memoryCache) {
            memoryCache.add(0, photo)
        }

        photo
    }

    suspend fun getCapturedPhotos(context: Context): List<CapturedPhoto> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<CapturedPhoto>()
        val resolver = context.contentResolver

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )

        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("GPS_IMG_%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val dateTaken = if (dateTakenCol != -1 && !cursor.isNull(dateTakenCol)) {
                        cursor.getLong(dateTakenCol)
                    } else {
                        cursor.getLong(dateAddedCol) * 1000
                    }
                    val size = cursor.getLong(sizeCol)
                    val uri = ContentUris.withAppendedId(collection, id)

                    // Check if cached metadata exists
                    val cached = synchronized(memoryCache) {
                        memoryCache.find { it.id == id || it.displayName == name }
                    }

                    val photo = if (cached != null) {
                        cached.copy(id = id, uri = uri, size = size)
                    } else {
                        var exifLat: Double? = null
                        var exifLon: Double? = null
                        var exifAlt: Double? = null
                        var exifAddr: String? = null
                        try {
                            resolver.openInputStream(uri)?.use { inputStream ->
                                val exif = ExifInterface(inputStream)
                                val latLong = exif.latLong
                                if (latLong != null && latLong.size == 2) {
                                    exifLat = latLong[0]
                                    exifLon = latLong[1]
                                }
                                val alt = exif.getAltitude(Double.NaN)
                                if (!alt.isNaN()) {
                                    exifAlt = alt
                                }
                                exifAddr = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
                                    ?.takeIf { it.isNotBlank() }
                            }
                        } catch (_: Exception) {
                            // Ignore EXIF read error
                        }

                        val loaded = CapturedPhoto(
                            id = id,
                            uri = uri,
                            displayName = name,
                            timestamp = dateTaken,
                            size = size,
                            latitude = exifLat,
                            longitude = exifLon,
                            accuracy = null,
                            altitude = exifAlt,
                            address = exifAddr
                        )
                        synchronized(memoryCache) {
                            if (memoryCache.none { it.id == id }) {
                                memoryCache.add(loaded)
                            }
                        }
                        loaded
                    }

                    photos.add(photo)
                }
            }
        } catch (_: Exception) {
            // Return cached photos on query error
            synchronized(memoryCache) {
                return@withContext ArrayList(memoryCache)
            }
        }

        if (photos.isEmpty()) {
            synchronized(memoryCache) {
                return@withContext ArrayList(memoryCache)
            }
        }

        photos
    }

    suspend fun deletePhoto(context: Context, photo: CapturedPhoto): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(photo.uri, null, null)
            synchronized(memoryCache) {
                memoryCache.removeAll { it.id == photo.id || it.uri == photo.uri }
            }
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    fun sharePhoto(context: Context, photo: CapturedPhoto) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, photo.uri)
            val textNote = buildString {
                append("Captured with GeoStamp Camera\n")
                if (photo.latitude != null && photo.longitude != null) {
                    append(String.format(Locale.US, "GPS: %.6f, %.6f\n", photo.latitude, photo.longitude))
                }
                if (!photo.address.isNullOrBlank()) {
                    append("Location: ${photo.address}\n")
                }
            }
            putExtra(Intent.EXTRA_TEXT, textNote)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Photo")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
