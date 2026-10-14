package com.geostamp.camera.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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

    // In-memory cache for map tiles
    private val mapCache = mutableMapOf<String, Bitmap>()

    suspend fun getMapBitmap(
        latitude: Double,
        longitude: Double,
        headingDegrees: Int = 45,
        sizePx: Int = 320
    ): Bitmap = withContext(Dispatchers.IO) {
        val cacheKey = String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
        synchronized(mapCache) {
            mapCache[cacheKey]?.let { return@withContext it }
        }

        // Try downloading static satellite / street tile
        val downloaded = downloadStaticTile(latitude, longitude, sizePx)
        val baseBitmap = downloaded ?: renderTacticalMap(latitude, longitude, sizePx)

        // Overlay pin, field-of-view cone, and Google badge
        val resultBitmap = overlayMapElements(baseBitmap, headingDegrees, sizePx)

        synchronized(mapCache) {
            if (mapCache.size > 20) mapCache.clear()
            mapCache[cacheKey] = resultBitmap
        }

        resultBitmap
    }

    private fun downloadStaticTile(latitude: Double, longitude: Double, sizePx: Int): Bitmap? {
        val zoom = 17
        val x = lonToTileX(longitude, zoom)
        val y = latToTileY(latitude, zoom)
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
            try {
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val stream: InputStream = conn.inputStream
                    val bmp = BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (bmp != null) {
                        return Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
                    }
                }
            } catch (_: Exception) {
                // Try next url
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
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark terrain background simulating satellite earth
        val bgPaint = Paint().apply {
            color = Color.parseColor("#1B261D")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), bgPaint)

        // Roads / blocks simulation
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#37474F")
            strokeWidth = sizePx * 0.035f
            style = Paint.Style.STROKE
        }
        val center = sizePx / 2f
        canvas.drawLine(0f, center * 0.7f, sizePx.toFloat(), center * 0.7f, roadPaint)
        canvas.drawLine(center * 1.3f, 0f, center * 1.3f, sizePx.toFloat(), roadPaint)

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

    private fun overlayMapElements(baseBitmap: Bitmap, headingDegrees: Int, sizePx: Int): Bitmap {
        val result = if (baseBitmap.isMutable) baseBitmap else baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val center = sizePx / 2f

        // 1. Draw blue vision field cone emanating from center pin
        val coneAngle = Math.toRadians((headingDegrees - 90).toDouble())
        val spread = Math.toRadians(35.0)
        val coneRadius = center * 0.75f

        val conePath = Path().apply {
            moveTo(center, center)
            lineTo(
                (center + coneRadius * cos(coneAngle - spread)).toFloat(),
                (center + coneRadius * sin(coneAngle - spread)).toFloat()
            )
            arcTo(
                RectF(center - coneRadius, center - coneRadius, center + coneRadius, center + coneRadius),
                (headingDegrees - 90 - 35).toFloat(),
                70f,
                false
            )
            close()
        }

        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 41, 121, 255)
            style = Paint.Style.FILL
        }
        canvas.drawPath(conePath, conePaint)

        // 2. Draw red location pin marker
        val pinRadius = sizePx * 0.055f
        val pinY = center

        // Pin shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, pinY + pinRadius * 0.4f, pinRadius * 0.6f, shadowPaint)

        // Pin head
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, pinY, pinRadius, pinPaint)

        // White dot inside pin
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, pinY, pinRadius * 0.45f, dotPaint)

        // 3. Draw "Google" watermark logo at bottom left
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * 0.09f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 1f, 1f, Color.argb(180, 0, 0, 0))
        }
        canvas.drawText("Google", sizePx * 0.07f, sizePx * 0.92f, logoPaint)

        return result
    }
}
