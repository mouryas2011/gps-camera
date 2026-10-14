package com.geostamp.camera.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.location.LocationSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {

    data class ProcessedPhotoResult(
        val jpegBytes: ByteArray,
        val width: Int,
        val height: Int,
        val formattedTimestamp: String,
        val formattedCoordinates: String?,
        val address: String?
    )

    fun stampAndProcessImage(
        rawJpegBytes: ByteArray,
        location: LocationSnapshot?,
        address: String?,
        config: StampConfig,
        lensFacingFront: Boolean = false
    ): ProcessedPhotoResult {
        // 1. Inspect original orientation from EXIF
        val originalExif = try {
            ExifInterface(ByteArrayInputStream(rawJpegBytes))
        } catch (_: Exception) {
            null
        }

        val orientation = originalExif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

        // 2. Decode Bitmap
        val decoded = BitmapFactory.decodeByteArray(rawJpegBytes, 0, rawJpegBytes.size)
            ?: throw IllegalStateException("Unable to decode captured JPEG image")

        // 3. Normalize orientation so the bitmap is upright
        val uprightBitmap = normalizeOrientation(decoded, orientation, lensFacingFront)

        // 4. Prepare stamped canvas
        val mutableBitmap = if (uprightBitmap.isMutable) {
            uprightBitmap
        } else {
            val copy = uprightBitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (uprightBitmap != decoded) uprightBitmap.recycle()
            copy
        }
        if (decoded != mutableBitmap && decoded != uprightBitmap) {
            decoded.recycle()
        }

        val canvas = Canvas(mutableBitmap)

        // 5. Gather stamp lines
        val now = Date()
        val dateFormat = try {
            SimpleDateFormat(config.dateTimePattern, Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        }
        val formattedTime = dateFormat.format(now)

        val formattedCoords = if (location != null && config.showCoordinates) {
            StampConfig.formatCoordinates(location.latitude, location.longitude, config.coordinateFormat)
        } else null

        val accuracyAltitude = buildString {
            if (location != null && config.showAccuracy && location.accuracyMeters > 0) {
                append(String.format(Locale.US, "Accuracy: ±%.1fm", location.accuracyMeters))
            }
            if (location?.altitudeMeters != null && config.showAltitude) {
                if (isNotEmpty()) append("  •  ")
                append(String.format(Locale.US, "Alt: %.1fm", location.altitudeMeters))
            }
        }.takeIf { it.isNotBlank() }

        // 6. Draw stamp on canvas
        drawStamp(
            canvas = canvas,
            width = mutableBitmap.width,
            height = mutableBitmap.height,
            config = config,
            title = if (config.showCustomTitle) config.customTitle else null,
            timeStr = if (config.showDateTime) formattedTime else null,
            coordStr = formattedCoords,
            accAltStr = accuracyAltitude,
            addressStr = if (config.showAddress) address else null
        )

        // 7. Compress stamped bitmap to JPEG
        val stream = ByteArrayOutputStream()
        mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)
        val stampedBytes = stream.toByteArray()
        mutableBitmap.recycle()

        // 8. Inject EXIF metadata
        val finalBytes = injectExifMetadata(
            jpegBytes = stampedBytes,
            location = location,
            date = now
        )

        return ProcessedPhotoResult(
            jpegBytes = finalBytes,
            width = canvas.width,
            height = canvas.height,
            formattedTimestamp = formattedTime,
            formattedCoordinates = formattedCoords,
            address = address
        )
    }

    private fun normalizeOrientation(
        bitmap: Bitmap,
        orientation: Int,
        lensFacingFront: Boolean
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> {}
        }
        if (lensFacingFront && orientation == ExifInterface.ORIENTATION_NORMAL) {
            // Some front cameras require horizontal mirroring
            // Keep natural preview unless explicitly requested
        }

        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun drawStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: StampConfig,
        title: String?,
        timeStr: String?,
        coordStr: String?,
        accAltStr: String?,
        addressStr: String?
    ) {
        val minDim = min(width, height).toFloat()
        val scale = max(1.0f, minDim / 1080f)

        val padding = 28f * scale
        val cardInnerPadding = 24f * scale
        val cornerRadius = 18f * scale

        val titleSize = 22f * scale
        val primaryTextSize = 20f * scale
        val subTextSize = 17f * scale
        val lineSpacing = 8f * scale

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            textSize = titleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = primaryTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E6ED")
            textSize = subTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Collect lines to render
        val lines = mutableListOf<Pair<String, Paint>>()
        if (!title.isNullOrBlank()) {
            lines.add(title.uppercase() to titlePaint)
        }
        if (!timeStr.isNullOrBlank()) {
            lines.add(timeStr to primaryPaint)
        }
        if (!coordStr.isNullOrBlank()) {
            lines.add(coordStr to primaryPaint)
        }
        if (!accAltStr.isNullOrBlank()) {
            lines.add(accAltStr to subPaint)
        }
        if (!addressStr.isNullOrBlank()) {
            // Wrap address if it exceeds max width
            val maxAddressWidth = (width * 0.75f) - (cardInnerPadding * 2)
            val wrapped = wrapText(addressStr, subPaint, maxAddressWidth)
            for (line in wrapped) {
                lines.add(line to subPaint)
            }
        }

        if (lines.isEmpty()) return

        // Calculate card dimensions
        var maxLineWidth = 0f
        var totalTextHeight = 0f
        for ((text, paint) in lines) {
            val textW = paint.measureText(text)
            if (textW > maxLineWidth) maxLineWidth = textW
            val fontMetrics = paint.fontMetrics
            totalTextHeight += (fontMetrics.descent - fontMetrics.ascent) + lineSpacing
        }

        val cardWidth = maxLineWidth + (cardInnerPadding * 2)
        val cardHeight = totalTextHeight + (cardInnerPadding * 2) - lineSpacing

        // Calculate card coordinates based on StampPosition
        val cardLeft: Float
        val cardTop: Float

        when (config.position) {
            StampPosition.BOTTOM_LEFT -> {
                cardLeft = padding
                cardTop = height - padding - cardHeight
            }
            StampPosition.BOTTOM_RIGHT -> {
                cardLeft = width - padding - cardWidth
                cardTop = height - padding - cardHeight
            }
            StampPosition.TOP_LEFT -> {
                cardLeft = padding
                cardTop = padding
            }
            StampPosition.TOP_RIGHT -> {
                cardLeft = width - padding - cardWidth
                cardTop = padding
            }
        }

        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

        // Draw card background based on StampStyle
        when (config.style) {
            StampStyle.FROSTED_GLASS -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(205, 12, 17, 24)
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(90, 0, 229, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * scale
                }
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

                // Accent side indicator bar
                val accentBar = RectF(
                    cardLeft + 2f * scale,
                    cardTop + cornerRadius,
                    cardLeft + 6f * scale,
                    cardTop + cardHeight - cornerRadius
                )
                val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00E5FF")
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(accentBar, 2f * scale, 2f * scale, accentPaint)
            }
            StampStyle.HIGH_CONTRAST_DARK -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(240, 5, 8, 12)
                    style = Paint.Style.FILL
                }
                val topAccentBar = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + 4f * scale)
                val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00E5FF")
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(cardRect, cornerRadius * 0.5f, cornerRadius * 0.5f, bgPaint)
                canvas.drawRoundRect(topAccentBar, 2f * scale, 2f * scale, accentPaint)
            }
            StampStyle.CLEAN_OUTLINE -> {
                // Outline style: shadow/stroke with no solid background card
            }
        }

        // Draw text lines
        var currentY = cardTop + cardInnerPadding
        for ((text, paint) in lines) {
            val fontMetrics = paint.fontMetrics
            val baseline = currentY - fontMetrics.ascent
            val textX = cardLeft + cardInnerPadding

            if (config.style == StampStyle.CLEAN_OUTLINE) {
                // Shadow outline stroke behind text
                val strokePaint = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f * scale
                    color = Color.argb(220, 0, 0, 0)
                }
                canvas.drawText(text, textX, baseline, strokePaint)
            }

            canvas.drawText(text, textX, baseline, paint)
            currentY += (fontMetrics.descent - fontMetrics.ascent) + lineSpacing
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.setLength(0)
                currentLine.append(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine.setLength(0)
                }
                currentLine.append(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    private fun injectExifMetadata(
        jpegBytes: ByteArray,
        location: LocationSnapshot?,
        date: Date
    ): ByteArray {
        val tempFile = try {
            File.createTempFile("exif_temp_", ".jpg").apply {
                FileOutputStream(this).use { it.write(jpegBytes) }
            }
        } catch (_: Exception) {
            return jpegBytes
        }

        try {
            val exif = ExifInterface(tempFile.absolutePath)

            // Date / Time
            val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            val formattedDate = exifDateFormat.format(date)
            exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formattedDate)

            // Location
            if (location != null) {
                exif.setLatLong(location.latitude, location.longitude)
                if (location.altitudeMeters != null) {
                    exif.setAltitude(location.altitudeMeters)
                }
            }

            exif.setAttribute(ExifInterface.TAG_MAKE, "GPS Map Camera")
            exif.setAttribute(ExifInterface.TAG_MODEL, "GeoStamp Android")

            exif.saveAttributes()

            return tempFile.readBytes()
        } catch (_: Exception) {
            // Fail-safe: if EXIF writing fails, return the original stamped bytes
            return jpegBytes
        } finally {
            tempFile.delete()
        }
    }
}
