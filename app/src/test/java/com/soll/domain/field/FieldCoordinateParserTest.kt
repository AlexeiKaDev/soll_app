package com.soll.domain.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FieldCoordinateParserTest {
    @Test
    fun `parses decimal coordinates from plain text`() {
        val coordinate = FieldCoordinateParser.parseFirst("Точка встречи: 47.010500, 28.863800")

        assertEquals(47.010500, coordinate?.latitude ?: 0.0, 0.000001)
        assertEquals(28.863800, coordinate?.longitude ?: 0.0, 0.000001)
    }

    @Test
    fun `parses coordinates from maps query`() {
        val coordinate = FieldCoordinateParser.parseFirst("https://maps.google.com/?q=47.02,28.84")

        assertEquals(47.02, coordinate?.latitude ?: 0.0, 0.000001)
        assertEquals(28.84, coordinate?.longitude ?: 0.0, 0.000001)
    }

    @Test
    fun `ignores invalid coordinate ranges`() {
        assertNull(FieldCoordinateParser.parseFirst("999.0, 222.0"))
    }

    @Test
    fun `manual parser accepts comma decimal separator`() {
        val coordinate = FieldCoordinateParser.parseManual("47,01", "28,86")

        assertEquals(47.01, coordinate.latitude, 0.000001)
        assertEquals(28.86, coordinate.longitude, 0.000001)
    }

    @Test
    fun `manual parser reports invalid input`() {
        assertThrows(IllegalArgumentException::class.java) {
            FieldCoordinateParser.parseManual("abc", "28.86")
        }
    }

    @Test
    fun `distance uses meters scale`() {
        val left = GeoCoordinate(47.0, 28.0)
        val right = GeoCoordinate(47.001, 28.0)

        val distance = FieldDistance.metersBetween(left, right)

        assertEquals(111.0, distance, 2.0)
    }
}
