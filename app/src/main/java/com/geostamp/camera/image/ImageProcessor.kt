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
import com.geostamp.camera.location.DetailedAddress
import com.geostamp.camera.location.LocationSnapshot
import com.geostamp.camera.sensor.SensorSnapshot
import com.geostamp.camera.weather.WeatherSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
        detailedAddress: DetailedAddress?,
        plusCode: String?,
        sensorSnapshot: SensorSnapshot,
        weatherSnapshot: WeatherSnapshot,
        mapBitmap: Bitmap?,
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
            SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a 'GMT' Z", Locale.getDefault())
        }
        val formattedTime = dateFormat.format(now)

        val formattedCoords = if (location != null && config.showCoordinates) {
            StampConfig.formatCoordinates(location.latitude, location.longitude, config.coordinateFormat)
        } else null

        // 6. Draw stamp on canvas
        if (config.style == StampStyle.CLASSIC_MAP_BADGE) {
            drawClassicMapBadge(
                canvas = canvas,
                width = mutableBitmap.width,
                height = mutableBitmap.height,
                config = config,
                location = location,
                detailedAddress = detailedAddress,
                plusCode = plusCode,
                sensorSnapshot = sensorSnapshot,
                weatherSnapshot = weatherSnapshot,
                mapBitmap = mapBitmap,
                timeStr = formattedTime,
                coordStr = formattedCoords
            )
        } else {
            drawSimpleStamp(
                canvas = canvas,
                width = mutableBitmap.width,
                height = mutableBitmap.height,
                config = config,
                title = if (config.showCustomTitle) config.customTitle else null,
                timeStr = if (config.showDateTime) formattedTime else null,
                coordStr = formattedCoords,
                accAltStr = null,
                addressStr = if (config.showAddress) detailedAddress?.fullStreetAddress else null
            )
        }

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
            address = detailedAddress?.fullStreetAddress
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
        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun drawClassicMapBadge(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: StampConfig,
        location: LocationSnapshot?,
        detailedAddress: DetailedAddress?,
        plusCode: String?,
        sensorSnapshot: SensorSnapshot,
        weatherSnapshot: WeatherSnapshot,
        mapBitmap: Bitmap?,
        timeStr: String,
        coordStr: String?
    ) {
        val minDim = min(width, height).toFloat()
        val scale = max(1.0f, minDim / 1080f)

        val padding = 20f * scale
        val cardInnerPadding = 14f * scale
        val cornerRadius = 12f * scale

        // Card width spans 95% of image width for modern look
        val cardWidth = width - (padding * 2)
        val mapBoxSize = 220f * scale
        val textSectionWidth = cardWidth - mapBoxSize - (cardInnerPadding * 3)

        // Typefaces and paints
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val addressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E6ED")
            textSize = 15f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF176")
            textSize = 15f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0BEC5")
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val sensorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80D8FF")
            textSize = 13.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Calculate height needed
        val lineSpacing = 6f * scale
        val headerText = detailedAddress?.shortHeader ?: "Korba, Chhattisgarh, India 🇮🇳"
        val streetText = detailedAddress?.fullStreetAddress ?: "223, Rampur, Korba, Chhattisgarh 495677, India"
        val wrappedAddress = wrapText(streetText, addressPaint, textSectionWidth)

        var textHeightAccumulator = 0f
        textHeightAccumulator += (headerPaint.fontMetrics.descent - headerPaint.fontMetrics.ascent) + lineSpacing
        for (w in wrappedAddress) {
            textHeightAccumulator += (addressPaint.fontMetrics.descent - addressPaint.fontMetrics.ascent) + lineSpacing
        }
        if (coordStr != null) {
            textHeightAccumulator += (coordPaint.fontMetrics.descent - coordPaint.fontMetrics.ascent) + lineSpacing
        }
        if (!plusCode.isNullOrBlank()) {
            textHeightAccumulator += (metaPaint.fontMetrics.descent - metaPaint.fontMetrics.ascent) + lineSpacing
        }
        textHeightAccumulator += (timePaint.fontMetrics.descent - timePaint.fontMetrics.ascent) + lineSpacing
        // Weather and Compass line
        textHeightAccumulator += (metaPaint.fontMetrics.descent - metaPaint.fontMetrics.ascent) + lineSpacing
        // Sensor line
        textHeightAccumulator += (sensorPaint.fontMetrics.descent - sensorPaint.fontMetrics.ascent) + lineSpacing

        val cardContentHeight = max(mapBoxSize, textHeightAccumulator)
        val cardHeight = cardContentHeight + (cardInnerPadding * 2)

        val cardLeft = padding
        val cardTop = height - padding - cardHeight

        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

        // 1. Draw card background (deep semi-translucent dark slate card)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 8, 12, 18)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
        }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

        // 2. Draw Map square on left
        val mapLeft = cardLeft + cardInnerPadding
        val mapTop = cardTop + (cardHeight - mapBoxSize) / 2f
        val mapRect = RectF(mapLeft, mapTop, mapLeft + mapBoxSize, mapTop + mapBoxSize)

        if (mapBitmap != null && !mapBitmap.isRecycled) {
            canvas.drawBitmap(mapBitmap, null, mapRect, null)
            val mapBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(80, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 1f * scale
            }
            canvas.drawRect(mapRect, mapBorder)
        }

        // 3. Draw text details on right side
        val textLeft = mapLeft + mapBoxSize + cardInnerPadding
        var currentY = cardTop + cardInnerPadding

        // Header
        val headerFm = headerPaint.fontMetrics
        canvas.drawText(headerText, textLeft, currentY - headerFm.ascent, headerPaint)
        currentY += (headerFm.descent - headerFm.ascent) + lineSpacing

        // Address lines
        for (line in wrappedAddress) {
            val addrFm = addressPaint.fontMetrics
            canvas.drawText(line, textLeft, currentY - addrFm.ascent, addressPaint)
            currentY += (addrFm.descent - addrFm.ascent) + lineSpacing
        }

        // Lat & Long
        if (coordStr != null) {
            val coordFm = coordPaint.fontMetrics
            canvas.drawText(coordStr, textLeft, currentY - coordFm.ascent, coordPaint)
            currentY += (coordFm.descent - coordFm.ascent) + lineSpacing
        }

        // Plus Code
        if (!plusCode.isNullOrBlank()) {
            val metaFm = metaPaint.fontMetrics
            canvas.drawText("Plus Code : $plusCode", textLeft, currentY - metaFm.ascent, metaPaint)
            currentY += (metaFm.descent - metaFm.ascent) + lineSpacing
        }

        // Date & Time on left, Weather Temperature on right
        val timeFm = timePaint.fontMetrics
        canvas.drawText(timeStr, textLeft, currentY - timeFm.ascent, timePaint)

        val weatherText = String.format(
            Locale.US,
            "%s %.2f° C",
            weatherSnapshot.weatherEmoji,
            weatherSnapshot.temperatureCelsius
        )
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD54F")
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val weatherWidth = tempPaint.measureText(weatherText)
        val rightEdge = cardLeft + cardWidth - cardInnerPadding
        canvas.drawText(weatherText, rightEdge - weatherWidth, currentY - timeFm.ascent, tempPaint)
        currentY += (timeFm.descent - timeFm.ascent) + lineSpacing

        // Person Name on left, Compass Heading on right
        val personNameText = "Person Name : ${if (config.personName.isNotBlank()) config.personName else "-"}"
        val metaFm = metaPaint.fontMetrics
        canvas.drawText(personNameText, textLeft, currentY - metaFm.ascent, metaPaint)

        val compassText = String.format(
            Locale.US,
            "🧭 %d° %s",
            sensorSnapshot.compassDegrees,
            sensorSnapshot.compassDirection
        )
        val compassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#81D4FA")
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val compassWidth = compassPaint.measureText(compassText)
        canvas.drawText(compassText, rightEdge - compassWidth, currentY - metaFm.ascent, compassPaint)
        currentY += (metaFm.descent - metaFm.ascent) + lineSpacing

        // Bottom Environmental Sensor Row (Wind, Humidity, Barometric Pressure, Magnetic Field)
        val sensorText = String.format(
            Locale.US,
            "🍃 %.2f km/h   💧 %d%%   🧭 %.0f hpa   🧲 %.2f µT",
            weatherSnapshot.windSpeedKmh,
            weatherSnapshot.humidityPercent,
            sensorSnapshot.pressureHpa,
            sensorSnapshot.magneticFieldMicroTesla
        )
        val sensorFm = sensorPaint.fontMetrics
        canvas.drawText(sensorText, textLeft, currentY - sensorFm.ascent, sensorPaint)

        // 4. Logo badge in top right: "GPS Map Camera"
        val logoBoxW = 125f * scale
        val logoBoxH = 26f * scale
        val logoLeft = cardLeft + cardWidth - logoBoxW - cardInnerPadding
        val logoTop = cardTop + 10f * scale
        val logoRect = RectF(logoLeft, logoTop, logoLeft + logoBoxW, logoTop + logoBoxH)

        val logoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 20, 30, 44)
            style = Paint.Style.FILL
        }
        val logoBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
        }
        canvas.drawRoundRect(logoRect, 4f * scale, 4f * scale, logoBgPaint)
        canvas.drawRoundRect(logoRect, 4f * scale, 4f * scale, logoBorder)

        val logoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("📷 GPS Map Camera", logoLeft + 6f * scale, logoTop + 17f * scale, logoTextPaint)
    }

    private fun drawSimpleStamp(
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

        val lines = mutableListOf<Pair<String, Paint>>()
        if (!title.isNullOrBlank()) lines.add(title.uppercase() to titlePaint)
        if (!timeStr.isNullOrBlank()) lines.add(timeStr to primaryPaint)
        if (!coordStr.isNullOrBlank()) lines.add(coordStr to primaryPaint)
        if (!accAltStr.isNullOrBlank()) lines.add(accAltStr to subPaint)
        if (!addressStr.isNullOrBlank()) {
            val maxAddressWidth = (width * 0.75f) - (cardInnerPadding * 2)
            val wrapped = wrapText(addressStr, subPaint, maxAddressWidth)
            for (line in wrapped) lines.add(line to subPaint)
        }

        if (lines.isEmpty()) return

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
            }
            StampStyle.HIGH_CONTRAST_DARK -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(240, 5, 8, 12)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(cardRect, cornerRadius * 0.5f, cornerRadius * 0.5f, bgPaint)
            }
            else -> {}
        }

        var currentY = cardTop + cardInnerPadding
        for ((text, paint) in lines) {
            val fontMetrics = paint.fontMetrics
            val baseline = currentY - fontMetrics.ascent
            val textX = cardLeft + cardInnerPadding
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

            val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            val formattedDate = exifDateFormat.format(date)
            exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formattedDate)

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
            return jpegBytes
        } finally {
            tempFile.delete()
        }
    }
}
