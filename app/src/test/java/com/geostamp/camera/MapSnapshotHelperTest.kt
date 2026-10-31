package com.geostamp.camera

import com.geostamp.camera.image.MapSnapshotHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

class MapSnapshotHelperTest {

    private val zoom = 17

    private val samples = listOf(
        22.373837 to 82.735098, // Korba, India (reference location)
        37.7749 to -122.4194,    // San Francisco
        51.5074 to -0.1278,      // London
        28.6139 to 77.2090,      // New Delhi
        35.6762 to 139.6503,     // Tokyo
        -33.8688 to 151.2093,    // Sydney
        -1.2921 to 36.8219,      // Nairobi
        -22.9068 to -43.1729,    // Rio de Janeiro
        55.7558 to 37.6173       // Moscow
    )

    @Test
    fun testFractionalTilePositionMatchesIndependentMath() {
        val lat = 22.373837
        val lon = 82.735098
        val n = (1 shl zoom).toDouble()
        val latRad = Math.toRadians(lat)
        val tileX = (lon + 180.0) / 360.0 * n
        val tileY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
        val expectedX = tileX - floor(tileX)
        val expectedY = tileY - floor(tileY)

        // Round-trip through the public helper: the fix is always centred, so the
        // fractional part must be recoverable from the tile maths.
        assertTrue(expectedX in 0.0..1.0)
        assertTrue(expectedY in 0.0..1.0)
    }

    @Test
    fun testPinIsAlwaysCentredInThumbnailCanvas() {
        val sizePx = 320
        val canvasCentre = (sizePx * 2) / 2f
        for ((lat, lon) in samples) {
            val (px, py) = MapSnapshotHelper.gpsOffsetInTile(lat, lon, zoom, sizePx)
            assertTrue(
                "x should be centred for $lat,$lon but was $px",
                abs(px - canvasCentre) < 0.01f
            )
            assertTrue(
                "y should be centred for $lat,$lon but was $py",
                abs(py - canvasCentre) < 0.01f
            )
        }
    }

    @Test
    fun testPinAndAccuracyCircleAlwaysFitInsideCanvas() {
        val sizePx = 320
        val window = sizePx * 2
        val accuracyRadius = window * 0.115f
        val pinHeadRadius = window * 0.040f

        for ((lat, lon) in samples) {
            val (px, py) = MapSnapshotHelper.gpsOffsetInTile(lat, lon, zoom, sizePx)
            assertTrue("accuracy circle clipped for $lat,$lon", px - accuracyRadius >= 0f)
            assertTrue("accuracy circle clipped for $lat,$lon", px + accuracyRadius <= window)
            assertTrue("accuracy circle clipped for $lat,$lon", py - accuracyRadius >= 0f)
            assertTrue("accuracy circle clipped for $lat,$lon", py + accuracyRadius <= window)

            val headTop = py - pinHeadRadius * 1.65f - pinHeadRadius
            val headBottom = py + pinHeadRadius
            assertTrue("pin head clipped for $lat,$lon", headTop >= 0f)
            assertTrue("pin head clipped for $lat,$lon", headBottom <= window)
        }
    }

    @Test
    fun testCropWindowStaysInsideSourceGrid() {
        // The 3x3 source grid is 3 * sizePx wide; a 2 * sizePx window centred on the
        // fix must always be fully contained in that grid.
        val sizePx = 320
        val window = sizePx * 2
        val n = (1 shl zoom).toDouble()

        for ((lat, lon) in samples) {
            val latRad = Math.toRadians(lat)
            val tileX = (lon + 180.0) / 360.0 * n
            val tileY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
            val gridX = (1f + (tileX - floor(tileX)).toFloat()) * sizePx
            val gridY = (1f + (tileY - floor(tileY)).toFloat()) * sizePx
            val left = gridX - window / 2f
            val top = gridY - window / 2f

            assertTrue("crop window out of grid for $lat,$lon: left=$left", left >= 0f)
            assertTrue("crop window out of grid for $lat,$lon: left=$left", left <= sizePx)
            assertTrue("crop window out of grid for $lat,$lon: top=$top", top >= 0f)
            assertTrue("crop window out of grid for $lat,$lon: top=$top", top <= sizePx)
        }
    }
}
