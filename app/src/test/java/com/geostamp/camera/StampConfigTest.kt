package com.geostamp.camera

import com.geostamp.camera.data.model.CoordinateFormat
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StampConfigTest {

    @Test
    fun testDecimalCoordinateFormatting() {
        val lat = 37.774929
        val lon = -122.419416
        val formatted = StampConfig.formatCoordinates(lat, lon, CoordinateFormat.DECIMAL)

        assertTrue("Should contain North indicator", formatted.contains("N"))
        assertTrue("Should contain West indicator", formatted.contains("W"))
        assertTrue("Should contain latitude value", formatted.contains("37.774929"))
        assertTrue("Should contain longitude value", formatted.contains("122.419416"))
    }

    @Test
    fun testSouthernEasternHemisphereFormatting() {
        val lat = -33.868820
        val lon = 151.209296
        val formatted = StampConfig.formatCoordinates(lat, lon, CoordinateFormat.DECIMAL)

        assertTrue("Should contain South indicator", formatted.contains("S"))
        assertTrue("Should contain East indicator", formatted.contains("E"))
        assertTrue("Should contain formatted latitude", formatted.contains("33.868820"))
    }

    @Test
    fun testDmsCoordinateFormatting() {
        val lat = 40.712776
        val lon = -74.005974
        val formatted = StampConfig.formatCoordinates(lat, lon, CoordinateFormat.DMS)

        assertTrue("Should format degrees", formatted.contains("40°"))
        assertTrue("Should contain North indicator", formatted.contains("N"))
        assertTrue("Should contain West indicator", formatted.contains("W"))
    }

    @Test
    fun testDefaultConfigIntegrity() {
        val config = StampConfig()
        assertEquals(StampPosition.BOTTOM_LEFT, config.position)
        assertEquals(StampStyle.FROSTED_GLASS, config.style)
        assertTrue(config.showDateTime)
        assertTrue(config.showCoordinates)
        assertTrue(config.showAddress)
        assertTrue(config.showAccuracy)
        assertTrue(config.showAltitude)
        assertEquals("GPS Map Camera", config.customTitle)
    }
}
