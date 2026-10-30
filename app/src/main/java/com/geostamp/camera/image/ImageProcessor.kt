package com.geostamp.camera.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.data.model.AspectRatioOption
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampLayout
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
        detailedAddress: DetailedAddress?,
        plusCode: String?,
        sensorSnapshot: SensorSnapshot,
        weatherSnapshot: WeatherSnapshot,
        mapBitmap: Bitmap?,
        config: StampConfig,
        lensFacingFront: Boolean = false,
        captureTimestampMillis: Long = System.currentTimeMillis()
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

        // 4. Crop to Aspect Ratio if requested
        val croppedBitmap = applyAspectRatioCrop(uprightBitmap, config.aspectRatio)

        // 5. Prepare stamped canvas
        val mutableBitmap = if (croppedBitmap.isMutable) {
            croppedBitmap
        } else {
            val copy = croppedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (croppedBitmap != decoded && croppedBitmap != uprightBitmap) croppedBitmap.recycle()
            copy
        }
        if (decoded != mutableBitmap && decoded != uprightBitmap && decoded != croppedBitmap) {
            decoded.recycle()
        }
        if (uprightBitmap != mutableBitmap && uprightBitmap != croppedBitmap) {
            uprightBitmap.recycle()
        }

        val canvas = Canvas(mutableBitmap)

        // 6. Gather stamp lines
        val now = Date(captureTimestampMillis)
        val formattedTime = StampConfig.formatDateTime(captureTimestampMillis, config.dateTimePattern)
        val displayTime = if (config.showDateTime) formattedTime else null

        val formattedCoords = if (location != null && config.showCoordinates) {
            StampConfig.formatCoordinates(location.latitude, location.longitude, config.coordinateFormat)
        } else null

        val effectiveAddress = if (config.showAddress) detailedAddress else null
        val effectivePlusCode = if (config.showPlusCode) plusCode else null
        val effectiveMap = if (config.showMapSnapshot) mapBitmap else null

        // 7. Draw stamp according to selected template style
        when (config.style) {
            StampStyle.CLASSIC_MAP_BADGE -> {
                drawClassicMapBadge(
                    canvas = canvas,
                    width = mutableBitmap.width,
                    height = mutableBitmap.height,
                    config = config,
                    location = location,
                    detailedAddress = effectiveAddress,
                    plusCode = effectivePlusCode,
                    sensorSnapshot = sensorSnapshot,
                    weatherSnapshot = weatherSnapshot,
                    mapBitmap = effectiveMap,
                    timeStr = displayTime,
                    coordStr = formattedCoords
                )
            }
            StampStyle.MODERN_MINIMAL -> {
                drawModernMinimal(
                    canvas = canvas,
                    width = mutableBitmap.width,
                    height = mutableBitmap.height,
                    config = config,
                    location = location,
                    detailedAddress = effectiveAddress,
                    sensorSnapshot = sensorSnapshot,
                    weatherSnapshot = weatherSnapshot,
                    mapBitmap = effectiveMap,
                    timeStr = displayTime,
                    coordStr = formattedCoords
                )
            }
            StampStyle.SURVEYOR_GRID -> {
                drawSurveyorGrid(
                    canvas = canvas,
                    width = mutableBitmap.width,
                    height = mutableBitmap.height,
                    config = config,
                    location = location,
                    detailedAddress = effectiveAddress,
                    plusCode = effectivePlusCode,
                    sensorSnapshot = sensorSnapshot,
                    weatherSnapshot = weatherSnapshot,
                    timeStr = displayTime,
                    coordStr = formattedCoords
                )
            }
            StampStyle.FROSTED_GLASS -> {
                drawFrostedGlass(
                    canvas = canvas,
                    width = mutableBitmap.width,
                    height = mutableBitmap.height,
                    config = config,
                    location = location,
                    detailedAddress = effectiveAddress,
                    plusCode = effectivePlusCode,
                    weatherSnapshot = weatherSnapshot,
                    timeStr = displayTime,
                    coordStr = formattedCoords
                )
            }
            StampStyle.HIGH_CONTRAST_DARK -> {
                drawHighContrastDark(
                    canvas = canvas,
                    width = mutableBitmap.width,
                    height = mutableBitmap.height,
                    config = config,
                    location = location,
                    detailedAddress = effectiveAddress,
                    sensorSnapshot = sensorSnapshot,
                    timeStr = displayTime,
                    coordStr = formattedCoords
                )
            }
            StampStyle.CLEAN_OUTLINE -> {
                drawCleanOutline(
                    canvas = canvas,
                    width = mutableBitmap.width,
                    height = mutableBitmap.height,
                    config = config,
                    location = location,
                    detailedAddress = effectiveAddress,
                    sensorSnapshot = sensorSnapshot,
                    weatherSnapshot = weatherSnapshot,
                    timeStr = displayTime,
                    coordStr = formattedCoords
                )
            }
        }

        val finalWidth = mutableBitmap.width
        val finalHeight = mutableBitmap.height

        // 8. Compress stamped bitmap to JPEG
        val stream = ByteArrayOutputStream()
        mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)
        val stampedBytes = stream.toByteArray()
        mutableBitmap.recycle()

        // 9. Inject EXIF metadata
        val finalBytes = injectExifMetadata(
            jpegBytes = stampedBytes,
            location = location,
            address = detailedAddress?.fullStreetAddress,
            date = now
        )

        return ProcessedPhotoResult(
            jpegBytes = finalBytes,
            width = finalWidth,
            height = finalHeight,
            formattedTimestamp = formattedTime,
            formattedCoordinates = formattedCoords,
            address = detailedAddress?.fullStreetAddress
        )
    }

    private fun applyAspectRatioCrop(bitmap: Bitmap, option: AspectRatioOption): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val isPortrait = h > w

        val targetRatio = if (isPortrait) {
            option.ratioY.toFloat() / option.ratioX.toFloat()
        } else {
            option.ratioX.toFloat() / option.ratioY.toFloat()
        }

        val currentRatio = if (isPortrait) h.toFloat() / w.toFloat() else w.toFloat() / h.toFloat()

        if (abs(currentRatio - targetRatio) < 0.03f) {
            return bitmap
        }

        val targetWidth: Int
        val targetHeight: Int

        if (isPortrait) {
            // Target height / width = targetRatio
            val calcH = (w * targetRatio).toInt()
            if (calcH <= h) {
                targetWidth = w
                targetHeight = calcH
            } else {
                targetWidth = (h / targetRatio).toInt()
                targetHeight = h
            }
        } else {
            // Target width / height = targetRatio
            val calcW = (h * targetRatio).toInt()
            if (calcW <= w) {
                targetWidth = calcW
                targetHeight = h
            } else {
                targetWidth = w
                targetHeight = (w / targetRatio).toInt()
            }
        }

        val startX = max(0, (w - targetWidth) / 2)
        val startY = max(0, (h - targetHeight) / 2)

        return Bitmap.createBitmap(bitmap, startX, startY, targetWidth, targetHeight)
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
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> {}
        }
        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    // 1. CLASSIC MAP BADGE (premium reference layout: map left, info panel right)
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
        timeStr: String?,
        coordStr: String?
    ) {
        val minDim = min(width, height).toFloat()
        val baseScale = max(1.0f, minDim / 1080f)
        val sizeScale = config.stampSize.scaleFactor
        val fontScale = config.fontSize.scaleFactor

        val padding = 20f * baseScale
        val innerPad = 12f * baseScale
        val cornerRadius = 12f * baseScale
        val gap = 10f * baseScale

        val cardWidth = (stampCardWidth(width, height, padding) * sizeScale)
            .coerceAtMost(width - padding * 2)
        val hasMap = config.showMapSnapshot && mapBitmap != null && !mapBitmap.isRecycled
        val isVertical = config.layout == StampLayout.VERTICAL
        val mapSize = if (hasMap) 160f * baseScale * sizeScale else 0f

        val primaryColor = try {
            Color.parseColor(config.fontColor.hex)
        } catch (_: Exception) {
            Color.WHITE
        }
        val secondaryColor = Color.parseColor("#C7D2DC")
        val mutedColor = Color.parseColor("#9AA7B4")

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 21f * baseScale * fontScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val addressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = 14f * baseScale * fontScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 15f * baseScale * fontScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 13.5f * baseScale * fontScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0BEC5")
            textSize = 12.5f * baseScale * fontScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor
            textSize = 12f * baseScale * fontScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val lineSpacing = 5f * baseScale

        val textSectionWidth = when {
            isVertical -> cardWidth - innerPad * 2
            hasMap -> cardWidth - mapSize - innerPad * 3
            else -> cardWidth - innerPad * 2
        }

        val headerRaw = when {
            config.showAddress && !detailedAddress?.shortHeader.isNullOrBlank() -> detailedAddress!!.shortHeader
            config.showCustomTitle && config.customTitle.isNotBlank() -> config.customTitle
            coordStr != null -> coordStr
            else -> "GeoStamp Camera"
        }

        data class StampLine(val text: String, val paint: Paint)

        val lines = mutableListOf<StampLine>()
        lines.add(StampLine(ellipsizeText(headerRaw, headerPaint, textSectionWidth), headerPaint))

        if (config.showAddress && !detailedAddress?.fullStreetAddress.isNullOrBlank()) {
            val wrapped = wrapText(detailedAddress!!.fullStreetAddress, addressPaint, textSectionWidth)
            for (line in wrapped.take(2)) {
                lines.add(StampLine(line, addressPaint))
            }
        }

        if (coordStr != null) {
            lines.add(StampLine(ellipsizeText(coordStr, coordPaint, textSectionWidth), coordPaint))
        }

        if (timeStr != null) {
            lines.add(StampLine(ellipsizeText(timeStr, timePaint, textSectionWidth), timePaint))
        }

        val extraParts = mutableListOf<String>()
        if (config.showPlusCode && !plusCode.isNullOrBlank()) extraParts.add("Plus Code: $plusCode")
        if (config.showAccuracy && location != null) {
            extraParts.add(String.format(Locale.US, "±%.1fm", location.accuracyMeters))
        }
        if (config.showAltitude && location?.altitudeMeters != null) {
            extraParts.add(String.format(Locale.US, "Alt: %.1fm", location.altitudeMeters))
        }
        if (config.showWeather) {
            extraParts.add(String.format(Locale.US, "%s %.1f°C", weatherSnapshot.weatherEmoji, weatherSnapshot.temperatureCelsius))
        }
        if (config.showCompass || config.showSensors) {
            extraParts.add(String.format(Locale.US, "🧭 %d° %s", sensorSnapshot.compassDegrees, sensorSnapshot.compassDirection))
        }
        if (extraParts.isNotEmpty()) {
            lines.add(StampLine(ellipsizeText(extraParts.joinToString("  •  "), metaPaint, textSectionWidth), metaPaint))
        }

        val tagParts = mutableListOf<String>()
        if (config.showCustomTitle && config.customTitle.isNotBlank() &&
            config.customTitle != headerRaw && config.customTitle != "GeoStamp Camera"
        ) {
            tagParts.add(config.customTitle)
        }
        if (config.personName.isNotBlank() && config.personName != "-") {
            tagParts.add(config.personName)
        }
        if (config.companyName.isNotBlank()) {
            tagParts.add(config.companyName)
        }
        if (tagParts.isNotEmpty()) {
            lines.add(StampLine(ellipsizeText(tagParts.joinToString("  •  "), metaPaint, textSectionWidth), metaPaint))
        }

        StampConfig.noteLine(config)?.let { note ->
            lines.add(StampLine(ellipsizeText(note, notePaint, textSectionWidth), notePaint))
        }

        var textHeight = 0f
        for (line in lines) {
            textHeight += (line.paint.fontMetrics.descent - line.paint.fontMetrics.ascent) + lineSpacing
        }
        if (lines.isNotEmpty()) textHeight -= lineSpacing

        val contentHeight = if (isVertical) {
            (if (hasMap) mapSize + gap else 0f) + textHeight
        } else {
            max(mapSize, textHeight)
        }
        val cardHeight = contentHeight + innerPad * 2

        val cardLeft = if (config.position == StampPosition.TOP_RIGHT || config.position == StampPosition.BOTTOM_RIGHT) {
            width - padding - cardWidth
        } else {
            padding
        }
        val cardTop = if (config.position == StampPosition.TOP_LEFT || config.position == StampPosition.TOP_RIGHT) {
            padding
        } else {
            height - padding - cardHeight
        }
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                (config.backgroundAlpha.coerceIn(0.35f, 0.95f) * 255).toInt(),
                8,
                12,
                18
            )
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

        var textLeft = cardLeft + innerPad
        var textTop = cardTop + innerPad

        if (hasMap) {
            if (isVertical) {
                val mapRect = RectF(
                    cardLeft + innerPad,
                    cardTop + innerPad,
                    cardLeft + cardWidth - innerPad,
                    cardTop + innerPad + mapSize
                )
                drawRoundedBitmap(canvas, mapBitmap!!, mapRect, 8f * baseScale)
                textTop = mapRect.bottom + gap
            } else {
                val mapRect = RectF(
                    cardLeft + innerPad,
                    cardTop + (cardHeight - mapSize) / 2f,
                    cardLeft + innerPad + mapSize,
                    cardTop + (cardHeight + mapSize) / 2f
                )
                drawRoundedBitmap(canvas, mapBitmap!!, mapRect, 8f * baseScale)
                textLeft = mapRect.right + gap
            }
        }

        var currentY = textTop
        for (line in lines) {
            val fm = line.paint.fontMetrics
            canvas.drawText(line.text, textLeft, currentY - fm.ascent, line.paint)
            currentY += (fm.descent - fm.ascent) + lineSpacing
        }
    }

    private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, rect: RectF, radius: Float) {
        val path = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restoreToCount(save)
    }

    // 2. MODERN MINIMAL
    private fun drawModernMinimal(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: StampConfig,
        location: LocationSnapshot?,
        detailedAddress: DetailedAddress?,
        sensorSnapshot: SensorSnapshot,
        weatherSnapshot: WeatherSnapshot,
        mapBitmap: Bitmap?,
        timeStr: String?,
        coordStr: String?
    ) {
        val minDim = min(width, height).toFloat()
        val scale = max(1.0f, minDim / 1080f)

        val padding = 20f * scale
        val innerPad = 12f * scale
        val cardWidth = (stampCardWidth(width, height, padding) * config.stampSize.scaleFactor)
            .coerceAtMost(width - padding * 2)
        val hasMap = config.showMapSnapshot && mapBitmap != null && !mapBitmap.isRecycled
        val mapSize = if (hasMap) 95f * scale else 0f

        val cardHeight = max(mapSize, 108f * scale) + (innerPad * 2)
        val cardLeft = if (config.position == StampPosition.TOP_RIGHT || config.position == StampPosition.BOTTOM_RIGHT) {
            width - padding - cardWidth
        } else {
            padding
        }
        val cardTop = if (config.position == StampPosition.TOP_LEFT || config.position == StampPosition.TOP_RIGHT) {
            padding
        } else {
            height - padding - cardHeight
        }
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((config.backgroundAlpha.coerceIn(0.35f, 0.95f) * 255).toInt(), 10, 14, 20)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * scale
        }
        canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, bgPaint)
        canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, borderPaint)

        // Small map on the right if enabled
        if (hasMap) {
            val mapRight = cardLeft + cardWidth - innerPad
            val mapRect = RectF(mapRight - mapSize, cardTop + innerPad, mapRight, cardTop + innerPad + mapSize)
            canvas.drawBitmap(mapBitmap!!, null, mapRect, null)
            val circleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * scale
            }
            canvas.drawRect(mapRect, circleBorder)
        }

        val textLeft = cardLeft + innerPad
        val maxW = if (hasMap) cardWidth - mapSize - (innerPad * 3) else cardWidth - (innerPad * 2)
        var curY = cardTop + innerPad + (6f * scale)

        // Coords
        if (coordStr != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E5FF")
                textSize = 17f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(ellipsizeText(coordStr, p, maxW), textLeft, curY - p.fontMetrics.ascent, p)
            curY += (p.fontMetrics.descent - p.fontMetrics.ascent) + (4f * scale)
        }

        // Address
        if (config.showAddress && !detailedAddress?.fullStreetAddress.isNullOrBlank()) {
            val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 14f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val wrapped = wrapText(detailedAddress!!.fullStreetAddress, addrPaint, maxW)
            for (line in wrapped.take(2)) {
                canvas.drawText(line, textLeft, curY - addrPaint.fontMetrics.ascent, addrPaint)
                curY += (addrPaint.fontMetrics.descent - addrPaint.fontMetrics.ascent) + (4f * scale)
            }
        }

        // Time + Climate Pill
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0BEC5")
            textSize = 13f * scale
        }
        val metaParts = mutableListOf<String>()
        if (!timeStr.isNullOrBlank()) metaParts.add(timeStr)
        if (config.showWeather) metaParts.add("${weatherSnapshot.weatherEmoji} ${weatherSnapshot.temperatureCelsius.toInt()}°C")
        if (config.showCompass || config.showSensors) metaParts.add("🧭 ${sensorSnapshot.compassDegrees}° ${sensorSnapshot.compassDirection}")
        StampConfig.noteLine(config)?.let { metaParts.add(it) }
        val combinedMeta = metaParts.joinToString(" • ")
        if (combinedMeta.isNotBlank()) {
            canvas.drawText(ellipsizeText(combinedMeta, subPaint, maxW), textLeft, curY - subPaint.fontMetrics.ascent, subPaint)
        }
    }

    // 3. SURVEYOR GRID
    private fun drawSurveyorGrid(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: StampConfig,
        location: LocationSnapshot?,
        detailedAddress: DetailedAddress?,
        plusCode: String?,
        sensorSnapshot: SensorSnapshot,
        weatherSnapshot: WeatherSnapshot,
        timeStr: String?,
        coordStr: String?
    ) {
        val minDim = min(width, height).toFloat()
        val scale = max(1.0f, minDim / 1080f)

        val padding = 20f * scale
        val cardWidth = (stampCardWidth(width, height, padding) * config.stampSize.scaleFactor)
            .coerceAtMost(width - padding * 2)
        val rowHeight = 34f * scale
        val totalRows = 4
        val cardHeight = rowHeight * totalRows

        val cardLeft = if (config.position == StampPosition.TOP_RIGHT || config.position == StampPosition.BOTTOM_RIGHT) {
            width - padding - cardWidth
        } else {
            padding
        }
        val cardTop = if (config.position == StampPosition.TOP_LEFT || config.position == StampPosition.TOP_RIGHT) {
            padding
        } else {
            height - padding - cardHeight
        }

        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((config.backgroundAlpha.coerceIn(0.35f, 0.95f) * 255).toInt(), 6, 10, 16)
            style = Paint.Style.FILL
        }
        val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 204, 0) // surveyor golden yellow
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * scale
        }

        canvas.drawRect(cardRect, bgPaint)
        canvas.drawRect(cardRect, gridLinePaint)

        // Draw horizontal grid lines
        for (i in 1 until totalRows) {
            val y = cardTop + (i * rowHeight)
            canvas.drawLine(cardLeft, y, cardLeft + cardWidth, y, gridLinePaint)
        }

        // Draw vertical mid line for Row 1, Row 2, Row 4
        val midX = cardLeft + (cardWidth / 2f)
        canvas.drawLine(midX, cardTop, midX, cardTop + (rowHeight * 2), gridLinePaint)
        canvas.drawLine(midX, cardTop + (rowHeight * 3), midX, cardTop + cardHeight, gridLinePaint)

        val cellPad = 8f * scale
        val halfCellWidth = (cardWidth / 2f) - (cellPad * 2)
        val fullCellWidth = cardWidth - (cellPad * 2)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFCC00")
            textSize = 10.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val textBaselineOffset = 24f * scale

        // Row 1: Left = Project, Right = Inspector
        val projTitle = buildString {
            append(if (config.customTitle.isNotBlank()) config.customTitle else "GeoStamp Camera")
            if (config.companyName.isNotBlank()) append(" (${config.companyName})")
        }
        val projLabelW = labelPaint.measureText("PROJECT: ")
        canvas.drawText("PROJECT: ", cardLeft + cellPad, cardTop + textBaselineOffset, labelPaint)
        canvas.drawText(
            ellipsizeText(projTitle, valPaint, halfCellWidth - projLabelW),
            cardLeft + cellPad + projLabelW,
            cardTop + textBaselineOffset,
            valPaint
        )

        val person = if (config.personName.isNotBlank() && config.personName != "-") config.personName else "Surveyor"
        val inspLabelW = labelPaint.measureText("INSPECTOR: ")
        canvas.drawText("INSPECTOR: ", midX + cellPad, cardTop + textBaselineOffset, labelPaint)
        canvas.drawText(
            ellipsizeText(person, valPaint, halfCellWidth - inspLabelW),
            midX + cellPad + inspLabelW,
            cardTop + textBaselineOffset,
            valPaint
        )

        // Row 2: Left = GPS Coords, Right = Accuracy & Alt
        val r2Y = cardTop + rowHeight
        val gpsLabelW = labelPaint.measureText("GPS FIX: ")
        canvas.drawText("GPS FIX: ", cardLeft + cellPad, r2Y + textBaselineOffset, labelPaint)
        canvas.drawText(
            ellipsizeText(coordStr ?: "N/A", valPaint, halfCellWidth - gpsLabelW),
            cardLeft + cellPad + gpsLabelW,
            r2Y + textBaselineOffset,
            valPaint
        )

        val accAltParts = mutableListOf<String>()
        if (config.showAccuracy && location != null) {
            accAltParts.add(String.format(Locale.US, "±%.1fm", location.accuracyMeters))
        }
        if (config.showAltitude && location?.altitudeMeters != null) {
            accAltParts.add(String.format(Locale.US, "Alt: %.1fm", location.altitudeMeters))
        }
        if (config.showPlusCode && !plusCode.isNullOrBlank()) {
            accAltParts.add(plusCode)
        }
        val accAlt = accAltParts.joinToString(" • ").ifBlank { "Verified" }
        val precLabelW = labelPaint.measureText("PRECISION: ")
        canvas.drawText("PRECISION: ", midX + cellPad, r2Y + textBaselineOffset, labelPaint)
        canvas.drawText(
            ellipsizeText(accAlt, valPaint, halfCellWidth - precLabelW),
            midX + cellPad + precLabelW,
            r2Y + textBaselineOffset,
            valPaint
        )

        // Row 3: Full Address / Notes
        val r3Y = cardTop + (rowHeight * 2)
        val addrStr = buildString {
            if (config.showAddress && !detailedAddress?.fullStreetAddress.isNullOrBlank()) {
                append(detailedAddress!!.fullStreetAddress)
            }
            if (config.notes.isNotBlank()) {
                if (isNotEmpty()) append(" | Note: ")
                append(config.notes)
            }
        }.ifBlank { coordStr ?: "On-Device Stamped Location" }
        val locLabelW = labelPaint.measureText("LOCATION: ")
        canvas.drawText("LOCATION: ", cardLeft + cellPad, r3Y + textBaselineOffset, labelPaint)
        canvas.drawText(
            ellipsizeText(addrStr, valPaint, fullCellWidth - locLabelW),
            cardLeft + cellPad + locLabelW,
            r3Y + textBaselineOffset,
            valPaint
        )

        // Row 4: Left = Date & Time, Right = Environmental
        val r4Y = cardTop + (rowHeight * 3)
        val timeLabelW = labelPaint.measureText("TIMESTAMP: ")
        canvas.drawText("TIMESTAMP: ", cardLeft + cellPad, r4Y + textBaselineOffset, labelPaint)
        canvas.drawText(
            ellipsizeText(timeStr ?: "-", valPaint, halfCellWidth - timeLabelW),
            cardLeft + cellPad + timeLabelW,
            r4Y + textBaselineOffset,
            valPaint
        )

        val envParts = mutableListOf<String>()
        if (config.showWeather) {
            envParts.add("${weatherSnapshot.weatherEmoji} ${weatherSnapshot.temperatureCelsius.toInt()}°C")
            envParts.add("💧 ${weatherSnapshot.humidityPercent}%")
        }
        if (config.showCompass || config.showSensors) {
            envParts.add("🧭 ${sensorSnapshot.compassDegrees}° ${sensorSnapshot.compassDirection}")
        }
        val env = envParts.joinToString(" • ").ifBlank { "-" }
        val climLabelW = labelPaint.measureText("CLIMATE: ")
        canvas.drawText("CLIMATE: ", midX + cellPad, r4Y + textBaselineOffset, labelPaint)
        canvas.drawText(
            ellipsizeText(env, valPaint, halfCellWidth - climLabelW),
            midX + cellPad + climLabelW,
            r4Y + textBaselineOffset,
            valPaint
        )
    }

    // 4. FROSTED GLASS
    private fun drawFrostedGlass(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: StampConfig,
        location: LocationSnapshot?,
        detailedAddress: DetailedAddress?,
        plusCode: String?,
        weatherSnapshot: WeatherSnapshot,
        timeStr: String?,
        coordStr: String?
    ) {
        val minDim = min(width, height).toFloat()
        val scale = max(1.0f, minDim / 1080f)
        val padding = 20f * scale
        val innerPad = 13f * scale
        val cardWidth = (stampCardWidth(width, height, padding) * config.stampSize.scaleFactor)
            .coerceAtMost(width - padding * 2)
        val textMaxWidth = cardWidth - (innerPad * 2) - (16f * scale)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            textSize = 19f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CFD8DC")
            textSize = 14f * scale
        }

        val lines = mutableListOf<Pair<String, Paint>>()
        if (config.showCustomTitle && config.customTitle.isNotBlank()) {
            lines.add(ellipsizeText(config.customTitle.uppercase(), titlePaint, textMaxWidth) to titlePaint)
        }
        if (coordStr != null) {
            lines.add(ellipsizeText(coordStr, primaryPaint, textMaxWidth) to primaryPaint)
        }
        val metaParts = mutableListOf<String>()
        if (!timeStr.isNullOrBlank()) metaParts.add(timeStr)
        if (config.showWeather) metaParts.add("${weatherSnapshot.weatherEmoji} ${weatherSnapshot.temperatureCelsius.toInt()}°C")
        if (config.showAccuracy && location != null) metaParts.add(String.format(Locale.US, "±%.1fm", location.accuracyMeters))
        if (config.showAltitude && location?.altitudeMeters != null) metaParts.add(String.format(Locale.US, "Alt: %.1fm", location.altitudeMeters))
        if (config.showPlusCode && !plusCode.isNullOrBlank()) metaParts.add("Plus: $plusCode")
        if (metaParts.isNotEmpty()) {
            for (wrapped in wrapText(metaParts.joinToString(" • "), subPaint, textMaxWidth)) {
                lines.add(wrapped to subPaint)
            }
        }
        if (config.showAddress && !detailedAddress?.fullStreetAddress.isNullOrBlank()) {
            for (wrapped in wrapText(detailedAddress!!.fullStreetAddress, subPaint, textMaxWidth)) {
                lines.add(wrapped to subPaint)
            }
        }
        StampConfig.noteLine(config)?.let { note ->
            for (wrapped in wrapText(note, subPaint, textMaxWidth)) {
                lines.add(wrapped to subPaint)
            }
        }
        if (lines.isEmpty()) {
            lines.add("GEOSTAMP CAMERA" to titlePaint)
        }

        var totalH = 0f
        val lineSpacing = 4.5f * scale
        for ((_, p) in lines) {
            totalH += (p.fontMetrics.descent - p.fontMetrics.ascent) + lineSpacing
        }
        val cardHeight = totalH + (innerPad * 2)
        val cardLeft = if (config.position == StampPosition.TOP_RIGHT || config.position == StampPosition.BOTTOM_RIGHT) {
            width - padding - cardWidth
        } else {
            padding
        }
        val cardTop = if (config.position == StampPosition.TOP_LEFT || config.position == StampPosition.TOP_RIGHT) {
            padding
        } else {
            height - padding - cardHeight
        }

        val rect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((config.backgroundAlpha.coerceIn(0.35f, 0.95f) * 255).toInt(), 12, 17, 24)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * scale
        }
        canvas.drawRoundRect(rect, 14f * scale, 14f * scale, bgPaint)
        canvas.drawRoundRect(rect, 14f * scale, 14f * scale, borderPaint)

        // Cyan left accent vertical bar
        val barRect = RectF(cardLeft + (8f * scale), cardTop + (8f * scale), cardLeft + (12f * scale), cardTop + cardHeight - (8f * scale))
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(barRect, 2f * scale, 2f * scale, barPaint)

        var curY = cardTop + innerPad
        val textLeft = cardLeft + innerPad + (10f * scale)
        for ((text, paint) in lines) {
            canvas.drawText(text, textLeft, curY - paint.fontMetrics.ascent, paint)
            curY += (paint.fontMetrics.descent - paint.fontMetrics.ascent) + lineSpacing
        }
    }

    // 5. HIGH CONTRAST DARK
    private fun drawHighContrastDark(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: StampConfig,
        location: LocationSnapshot?,
        detailedAddress: DetailedAddress?,
        sensorSnapshot: SensorSnapshot,
        timeStr: String?,
        coordStr: String?
    ) {
        val minDim = min(width, height).toFloat()
        val scale = max(1.0f, minDim / 1080f)
        val padding = 16f * scale
        val innerPad = 12f * scale
        val cardWidth = (stampCardWidth(width, height, padding) * config.stampSize.scaleFactor)
            .coerceAtMost(width - padding * 2)
        val textMaxWidth = cardWidth - (innerPad * 2)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFCC00")
            textSize = 18f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 13.5f * scale
        }

        val lines = mutableListOf<Pair<String, Paint>>()
        if (config.showCustomTitle && config.customTitle.isNotBlank()) {
            lines.add(ellipsizeText(config.customTitle.uppercase(), headerPaint, textMaxWidth) to headerPaint)
        }
        if (coordStr != null) {
            lines.add(ellipsizeText(coordStr, textPaint, textMaxWidth) to textPaint)
        }
        val metaParts = mutableListOf<String>()
        if (!timeStr.isNullOrBlank()) metaParts.add(timeStr)
        if (config.showAccuracy && location != null) metaParts.add(String.format(Locale.US, "±%.1fm", location.accuracyMeters))
        if (config.showAltitude && location?.altitudeMeters != null) metaParts.add(String.format(Locale.US, "Alt: %.1fm", location.altitudeMeters))
        if (config.showCompass || config.showSensors) metaParts.add("🧭 ${sensorSnapshot.compassDegrees}° ${sensorSnapshot.compassDirection}")
        if (metaParts.isNotEmpty()) {
            for (wrapped in wrapText(metaParts.joinToString("  •  "), subPaint, textMaxWidth)) {
                lines.add(wrapped to subPaint)
            }
        }
        if (config.showAddress && !detailedAddress?.fullStreetAddress.isNullOrBlank()) {
            for (wrapped in wrapText(detailedAddress!!.fullStreetAddress, subPaint, textMaxWidth)) {
                lines.add(wrapped to subPaint)
            }
        }
        StampConfig.noteLine(config)?.let { note ->
            for (wrapped in wrapText(note, subPaint, textMaxWidth)) {
                lines.add(wrapped to subPaint)
            }
        }
        if (lines.isEmpty()) {
            lines.add("GEOSTAMP CAMERA" to headerPaint)
        }

        var totalH = 0f
        val lineSpacing = 4.5f * scale
        for ((_, p) in lines) {
            totalH += (p.fontMetrics.descent - p.fontMetrics.ascent) + lineSpacing
        }
        val cardHeight = totalH + (innerPad * 2)
        val cardLeft = if (config.position == StampPosition.TOP_RIGHT || config.position == StampPosition.BOTTOM_RIGHT) {
            width - padding - cardWidth
        } else {
            padding
        }
        val cardTop = if (config.position == StampPosition.TOP_LEFT || config.position == StampPosition.TOP_RIGHT) {
            padding
        } else {
            height - padding - cardHeight
        }

        val rect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((config.backgroundAlpha.coerceIn(0.35f, 0.95f) * 255).toInt(), 4, 6, 8)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 8f * scale, 8f * scale, bgPaint)

        var curY = cardTop + innerPad
        val textLeft = cardLeft + innerPad
        for ((text, paint) in lines) {
            canvas.drawText(text, textLeft, curY - paint.fontMetrics.ascent, paint)
            curY += (paint.fontMetrics.descent - paint.fontMetrics.ascent) + lineSpacing
        }
    }

    // 6. CLEAN OUTLINE
    private fun drawCleanOutline(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: StampConfig,
        location: LocationSnapshot?,
        detailedAddress: DetailedAddress?,
        sensorSnapshot: SensorSnapshot,
        weatherSnapshot: WeatherSnapshot,
        timeStr: String?,
        coordStr: String?
    ) {
        val minDim = min(width, height).toFloat()
        val scale = max(1.0f, minDim / 1080f)
        val padding = 22f * scale
        val maxTextWidth = width - (padding * 2)
        val isRightAligned = config.position == StampPosition.TOP_RIGHT || config.position == StampPosition.BOTTOM_RIGHT

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f * scale, 2f * scale, 2f * scale, Color.BLACK)
        }

        val rawLines = mutableListOf<String>()
        if (config.showCustomTitle && config.customTitle.isNotBlank()) rawLines.add(config.customTitle.uppercase())
        if (coordStr != null) rawLines.add(coordStr)
        val metaParts = mutableListOf<String>()
        if (!timeStr.isNullOrBlank()) metaParts.add(timeStr)
        if (config.showWeather) metaParts.add("${weatherSnapshot.weatherEmoji} ${weatherSnapshot.temperatureCelsius.toInt()}°C")
        if (config.showCompass || config.showSensors) metaParts.add("🧭 ${sensorSnapshot.compassDegrees}° ${sensorSnapshot.compassDirection}")
        if (metaParts.isNotEmpty()) rawLines.add(metaParts.joinToString(" • "))
        if (config.showAddress && !detailedAddress?.fullStreetAddress.isNullOrBlank()) {
            rawLines.add(detailedAddress!!.fullStreetAddress)
        }
        StampConfig.noteLine(config)?.let { rawLines.add(it) }

        val lines = rawLines.flatMap { wrapText(it, fillPaint, maxTextWidth) }
        if (lines.isEmpty()) return

        val lineHeight = fillPaint.fontMetrics.descent - fillPaint.fontMetrics.ascent
        val lineSpacing = 6f * scale
        val totalHeight = lines.size * (lineHeight + lineSpacing)

        var curY = if (config.position == StampPosition.TOP_LEFT || config.position == StampPosition.TOP_RIGHT) {
            padding - fillPaint.fontMetrics.ascent
        } else {
            height - padding - totalHeight - fillPaint.fontMetrics.ascent
        }

        for (line in lines) {
            val x = if (isRightAligned) {
                width - padding - fillPaint.measureText(line)
            } else {
                padding
            }
            canvas.drawText(line, x, curY, fillPaint)
            curY += lineHeight + lineSpacing
        }
    }

    private fun stampCardWidth(width: Int, height: Int, padding: Float): Float {
        return if (width > height) {
            width * 0.40f
        } else {
            width * 0.92f
        }
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end).trimEnd() + ellipsis else ""
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
        address: String?,
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

            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )

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

            if (!address.isNullOrBlank()) {
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, address)
            }

            exif.setAttribute(ExifInterface.TAG_MAKE, "GeoStamp Camera")
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
