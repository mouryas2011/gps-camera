package com.geostamp.camera

import com.geostamp.camera.data.model.CoordinateFormat
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampFontColor
import com.geostamp.camera.data.model.StampFontSize
import com.geostamp.camera.data.model.StampLayout
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampSize
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.location.PlusCodeHelper
import com.geostamp.camera.sensor.SensorHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun testCardinalDirectionCalculation() {
        assertEquals("N", SensorHelper.getCardinalDirection(0))
        assertEquals("NE", SensorHelper.getCardinalDirection(45))
        assertEquals("E", SensorHelper.getCardinalDirection(90))
        assertEquals("S", SensorHelper.getCardinalDirection(180))
        assertEquals("W", SensorHelper.getCardinalDirection(270))
        assertEquals("N", SensorHelper.getCardinalDirection(359))
    }

    @Test
    fun testDefaultConfigIntegrity() {
        val config = StampConfig()
        assertEquals(StampPosition.BOTTOM_RIGHT, config.position)
        assertEquals(StampStyle.CLASSIC_MAP_BADGE, config.style)
        assertEquals(StampLayout.HORIZONTAL, config.layout)
        assertEquals(StampSize.MEDIUM, config.stampSize)
        assertEquals(StampFontSize.MEDIUM, config.fontSize)
        assertEquals(StampFontColor.WHITE, config.fontColor)
        assertTrue(config.showDateTime)
        assertTrue(config.showCoordinates)
        assertTrue(config.showAddress)
        assertFalse(config.showAccuracy)
        assertFalse(config.showAltitude)
        assertTrue(config.showMapSnapshot)
        assertFalse(config.showWeather)
        assertTrue(config.showCapturedByNote)
        assertEquals("GeoStamp Camera", config.customTitle)
    }

    @Test
    fun testCapturedByNoteLine() {
        val config = StampConfig()
        val note = StampConfig.noteLine(config)
        assertNotNull(note)
        assertEquals("Note: Captured by GeoStamp Camera", note)

        assertNull(StampConfig.noteLine(config.copy(showCapturedByNote = false)))

        val custom = StampConfig.noteLine(config.copy(notes = "Site inspection"))
        assertEquals("Note: Site inspection", custom)
    }

    @Test
    fun testDateTimeFormattingUsesOffset() {
        val formatted = StampConfig.formatDateTime(1790000000000L, StampConfig().dateTimePattern)
        assertTrue("Should include GMT label", formatted.contains("GMT"))
        assertTrue("Should include a timezone offset", formatted.contains("+") || formatted.contains("-"))
    }
}
