package com.geostamp.camera.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

object MapSnapshotHelper {

    /** Slippy-map zoom level used for the satellite thumbnail tiles. */
    private const val TILE_ZOOM = 17

    // In-memory cache for raw map tiles (before heading overlay)
    private val baseTileCache = mutableMapOf<String, Bitmap>()

    suspend fun getMapBitmap(
        latitude: Double,
        longitude: Double,
        headingDegrees: Int = 45,
        sizePx: Int = 320
    ): Bitmap = withContext(Dispatchers.IO) {
        val tileKey = String.format(Locale.US, "%.4f,%.4f,%d", latitude, longitude, sizePx)
        val cachedBase = synchronized(baseTileCache) {
            baseTileCache[tileKey]?.takeIf { !it.isRecycled }
        }

        val baseBitmap = if (cachedBase != null) {
            cachedBase
        } else {
            val downloaded = downloadStaticTile(latitude, longitude, sizePx)
            val rendered = downloaded ?: renderTacticalMap(latitude, longitude, sizePx)
            synchronized(baseTileCache) {
                if (baseTileCache.size > 20) baseTileCache.clear()
                baseTileCache[tileKey] = rendered
            }
            rendered
        }

        // Overlay pin, field-of-view cone (for current heading), and badge on a fresh copy.
        // The pin is placed at the exact pixel position of the GPS fix inside the tile,
        // so the marker always lands on the correct spot of the satellite imagery.
        overlayMapElements(baseBitmap, latitude, longitude, headingDegrees, sizePx)
    }

    /**
     * Builds the satellite thumbnail by stitching a 3x3 grid of tiles around the
     * GPS fix and then cropping a centred 2x2-tile window around the fix. Using
     * neighbouring tiles guarantees the pin and the blue accuracy circle always
     * have room to render, even when the fix sits close to a tile edge.
     */
    private fun downloadStaticTile(latitude: Double, longitude: Double, sizePx: Int): Bitmap? {
        val zoom = TILE_ZOOM
        val centerX = lonToTileX(longitude, zoom)
        val centerY = latToTileY(latitude, zoom)

        // 3x3 grid centred on the fix's own tile.
        val originX = centerX - 1
        val originY = centerY - 1

        val grid = arrayOfNulls<Bitmap>(9)
        var anyTile = false
        for (row in 0..2) {
            for (col in 0..2) {
                val tile = fetchTile(originX + col, originY + row, zoom, sizePx)
                if (tile != null) {
                    grid[row * 3 + col] = tile
                    anyTile = true
                }
            }
        }
        if (!anyTile) return null

        // Stitch the 3x3 grid into one bitmap.
        val full = Bitmap.createBitmap(sizePx * 3, sizePx * 3, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(full)
        for (row in 0..2) {
            for (col in 0..2) {
                val tile = grid[row * 3 + col] ?: continue
                fullCanvas.drawBitmap(
                    tile,
                    (col * sizePx).toFloat(),
                    (row * sizePx).toFloat(),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                tile.recycle()
            }
        }

        // Crop a 2x2-tile window centred on the GPS fix.
        // In the 3x3 grid the fix sits at (1 + frac) * sizePx from the grid origin.
        val (fracX, fracY) = fractionalTilePosition(latitude, longitude, zoom)
        val gridX = (1f + fracX) * sizePx
        val gridY = (1f + fracY) * sizePx
        val window = sizePx * 2
        val left = (gridX - window / 2f).coerceIn(0f, sizePx.toFloat())
        val top = (gridY - window / 2f).coerceIn(0f, sizePx.toFloat())

        val cropped = Bitmap.createBitmap(window, window, Bitmap.Config.ARGB_8888)
        val cropCanvas = Canvas(cropped)
        cropCanvas.drawBitmap(
            full,
            Rect(left.toInt(), top.toInt(), (left + window).toInt(), (top + window).toInt()),
            RectF(0f, 0f, window.toFloat(), window.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        full.recycle()
        return cropped
    }

    /** Downloads a single 256px slippy-map tile and scales it to [sizePx]. */
    private fun fetchTile(x: Int, y: Int, zoom: Int, sizePx: Int): Bitmap? {
        val urls = listOf(
            // 1. Google Satellite Hybrid (live satellite photography with street/area labels)
            "https://mt1.google.com/vt/lyrs=y&x=$x&y=$y&z=$zoom",
            "https://mt0.google.com/vt/lyrs=y&x=$x&y=$y&z=$zoom",
            // 2. Google Pure Satellite (crystal clear real satellite photo)
            "https://mt1.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom",
            "https://mt2.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom",
            // 3. Esri World Imagery (high-resolution global satellite)
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x",
            // 4. Carto / OSM tiles
            "https://tile.openstreetmap.org/$zoom/$x/$y.png"
        )

        for (urlString in urls) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val bmp = conn.inputStream.use { stream: InputStream ->
                        BitmapFactory.decodeStream(stream)
                    }
                    if (bmp != null) {
                        val scaled = Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
                        if (scaled != bmp) bmp.recycle()
                        return scaled
                    }
                }
            } catch (_: Exception) {
                // Try next url
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        return Math.floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clampedLat)
        return Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
    }

    private fun renderTacticalMap(latitude: Double, longitude: Double, sizePx: Int): Bitmap {
        // Same 2x size as the stitched satellite grid so the pin overlay lines up.
        val canvasSize = sizePx * 2
        val bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark terrain background simulating satellite earth
        val bgPaint = Paint().apply {
            color = Color.parseColor("#1B261D")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, canvasSize.toFloat(), canvasSize.toFloat(), bgPaint)

        // Roads / blocks simulation
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#37474F")
            strokeWidth = canvasSize * 0.0175f
            style = Paint.Style.STROKE
        }
        val center = canvasSize / 2f
        canvas.drawLine(0f, center * 0.7f, canvasSize.toFloat(), center * 0.7f, roadPaint)
        canvas.drawLine(center * 1.3f, 0f, center * 1.3f, canvasSize.toFloat(), roadPaint)

        // Building roofs / parcels
        val parcelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#263238")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(center * 0.2f, center * 0.1f, center * 0.9f, center * 0.6f), 4f, 4f, parcelPaint)
        canvas.drawRoundRect(RectF(center * 0.3f, center * 0.9f, center * 0.9f, center * 1.6f), 4f, 4f, parcelPaint)
        canvas.drawRoundRect(RectF(center * 1.5f, center * 0.3f, center * 1.9f, center * 0.9f), 4f, 4f, parcelPaint)

        // Concentric radar rings
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawCircle(center, center, center * 0.4f, ringPaint)
        canvas.drawCircle(center, center, center * 0.75f, ringPaint)

        return bitmap
    }

    /**
     * Returns the fractional position (0..1) of the given GPS fix inside its own
     * slippy-map tile at [zoom].
     */
    private fun fractionalTilePosition(
        latitude: Double,
        longitude: Double,
        zoom: Int
    ): Pair<Float, Float> {
        val n = (1 shl zoom).toDouble()
        val clampedLat = latitude.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clampedLat)
        val tileX = (longitude + 180.0) / 360.0 * n
        val tileY = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n
        val fracX = tileX - Math.floor(tileX)
        val fracY = tileY - Math.floor(tileY)
        return fracX.toFloat() to fracY.toFloat()
    }

    /**
     * Returns the pixel coordinates of the given GPS fix inside the finished
     * thumbnail canvas. The canvas is built from the tiles surrounding the fix's
     * own tile and cropped to a 2x2-tile window centred on the fix, so the marker
     * always sits at the exact centre and is never clipped by the canvas edge.
     */
    internal fun gpsOffsetInTile(
        latitude: Double,
        longitude: Double,
        zoom: Int,
        sizePx: Int
    ): Pair<Float, Float> {
        val canvasSize = (sizePx * 2).toFloat()
        val (fracX, fracY) = fractionalTilePosition(latitude, longitude, zoom)
        // The crop window is centred on the fix, so the fix lands at the canvas centre.
        return canvasSize / 2f to canvasSize / 2f
    }

    private fun overlayMapElements(
        baseBitmap: Bitmap,
        latitude: Double,
        longitude: Double,
        headingDegrees: Int,
        sizePx: Int
    ): Bitmap {
        val result = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val canvasSize = result.width.toFloat()

        // Exact screen position of the GPS fix inside the stitched grid.
        val (pinX, pinY) = gpsOffsetInTile(latitude, longitude, TILE_ZOOM, sizePx)

        // 1. Draw Google Maps-style blue location accuracy circle around the fix
        val accuracyRadius = canvasSize * 0.115f
        val accuracyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 66, 133, 244) // #4285F4 translucent
            style = Paint.Style.FILL
        }
        val accuracyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 66, 133, 244)
            style = Paint.Style.STROKE
            strokeWidth = (canvasSize * 0.005f).coerceAtLeast(1.8f)
        }
        canvas.drawCircle(pinX, pinY, accuracyRadius, accuracyFillPaint)
        canvas.drawCircle(pinX, pinY, accuracyRadius, accuracyBorderPaint)

        // Subtle directional heading cone inside accuracy circle
        val coneAngle = Math.toRadians((headingDegrees - 90).toDouble())
        val spread = Math.toRadians(30.0)
        val coneRadius = accuracyRadius * 0.92f
        val conePath = Path().apply {
            moveTo(pinX, pinY)
            lineTo(
                (pinX + coneRadius * cos(coneAngle - spread)).toFloat(),
                (pinY + coneRadius * sin(coneAngle - spread)).toFloat()
            )
            arcTo(
                RectF(pinX - coneRadius, pinY - coneRadius, pinX + coneRadius, pinY + coneRadius),
                (headingDegrees - 90 - 30).toFloat(),
                60f,
                false
            )
            close()
        }
        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(75, 66, 133, 244)
            style = Paint.Style.FILL
        }
        canvas.drawPath(conePath, conePaint)

        // 2. Draw Google Maps-style red teardrop location pin pointing at the fix
        val pinHeadRadius = canvasSize * 0.040f
        val pinTipY = pinY
        val pinHeadCenterY = pinTipY - (pinHeadRadius * 1.65f)

        // Ground shadow at pin tip
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            RectF(
                pinX - pinHeadRadius * 0.55f,
                pinTipY - pinHeadRadius * 0.18f,
                pinX + pinHeadRadius * 0.55f,
                pinTipY + pinHeadRadius * 0.22f
            ),
            shadowPaint
        )

        // Teardrop pin path
        val pinPath = Path().apply {
            moveTo(pinX, pinTipY)
            cubicTo(
                pinX - pinHeadRadius * 0.45f,
                pinTipY - pinHeadRadius * 0.65f,
                pinX - pinHeadRadius,
                pinHeadCenterY + pinHeadRadius * 0.45f,
                pinX - pinHeadRadius,
                pinHeadCenterY
            )
            arcTo(
                RectF(
                    pinX - pinHeadRadius,
                    pinHeadCenterY - pinHeadRadius,
                    pinX + pinHeadRadius,
                    pinHeadCenterY + pinHeadRadius
                ),
                180f,
                180f,
                false
            )
            cubicTo(
                pinX + pinHeadRadius,
                pinHeadCenterY + pinHeadRadius * 0.45f,
                pinX + pinHeadRadius * 0.45f,
                pinTipY - pinHeadRadius * 0.65f,
                pinX,
                pinTipY
            )
            close()
        }

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA4335")
            style = Paint.Style.FILL
        }
        val pinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (canvasSize * 0.004f).coerceAtLeast(1.4f)
        }
        canvas.drawPath(pinPath, pinPaint)
        canvas.drawPath(pinPath, pinStrokePaint)

        // Dark crimson inner circle inside pin head
        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E1B13")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pinX, pinHeadCenterY, pinHeadRadius * 0.42f, innerDotPaint)

        // 3. Draw "Google" watermark logo at bottom left
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = canvasSize * 0.045f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 1f, 1f, Color.argb(180, 0, 0, 0))
        }
        canvas.drawText("Google", canvasSize * 0.035f, canvasSize * 0.96f, logoPaint)

        return result
    }
}
