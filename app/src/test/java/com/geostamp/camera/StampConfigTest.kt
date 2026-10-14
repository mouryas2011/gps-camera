package com.geostamp.camera

import com.geostamp.camera.data.model.CoordinateFormat
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.location.PlusCodeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StampConfigTest {

    @Test
    fun testDecimalCoordinateFormatting() {
        val lat = 22.374124
        val lon = 82.735036
        val formatted = StampConfig.formatCoordinates(lat, lon, CoordinateFormat.DECIMAL)

        assertTrue("Should contain Lat label", formatted.contains("Lat"))
        assertTrue("Should contain Long label", formatted.contains("Long"))
        assertTrue("Should contain latitude value", formatted.contains("22.374124"))
        assertTrue("Should contain longitude value", formatted.contains("82.735036"))
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
    fun testPlusCodeEncoding() {
        val code = PlusCodeHelper.encode(22.374124, 82.735036)
        assertTrue("Should contain plus sign", code.contains("+"))
        assertTrue("Should be at least 8 characters", code.length >= 8)
    }

    @Test
    fun testDefaultConfigIntegrity() {
        val config = StampConfig()
        assertEquals(StampPosition.BOTTOM_LEFT, config.position)
        assertEquals(StampStyle.CLASSIC_MAP_BADGE, config.style)
        assertTrue(config.showDateTime)
        assertTrue(config.showCoordinates)
        assertTrue(config.showAddress)
        assertTrue(config.showAccuracy)
        assertTrue(config.showAltitude)
        assertTrue(config.showMapSnapshot)
        assertTrue(config.showWeather)
        assertEquals("GPS Map Camera", config.customTitle)
    }
}
