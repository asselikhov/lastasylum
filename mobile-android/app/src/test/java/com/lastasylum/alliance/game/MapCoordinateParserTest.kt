package com.lastasylum.alliance.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapCoordinateParserTest {
    @Test
    fun parse_commandLabel() {
        val c = MapCoordinateParser.parse("Атака по городу X:482 Y:901")
        assertNotNull(c)
        assertEquals("Атака по городу", c!!.label)
        assertEquals(482, c.x)
        assertEquals(901, c.y)
    }

    @Test
    fun parse_targetNameWithSpaces() {
        val c = MapCoordinateParser.parse("База Игрок X:100 Y:200")
        assertNotNull(c)
        assertEquals("База Игрок", c!!.label)
        assertEquals(100, c.x)
        assertEquals(200, c.y)
    }

    @Test
    fun parse_excavationStyle() {
        val c = MapCoordinateParser.parse("Раскопки альянса X:10 Y:20")
        assertNotNull(c)
        assertEquals("Раскопки альянса", c!!.label)
        assertEquals(10, c.x)
        assertEquals(20, c.y)
    }

    @Test
    fun parse_coordsOnly() {
        val c = MapCoordinateParser.parse("X:1 Y:2")
        assertNotNull(c)
        assertNull(c!!.label)
        assertEquals(1, c.x)
        assertEquals(2, c.y)
    }

    @Test
    fun parse_loosePair() {
        val c = MapCoordinateParser.parse("482, 901")
        assertNotNull(c)
        assertEquals(482, c!!.x)
        assertEquals(901, c.y)
    }

    @Test
    fun parse_noCoordinates() {
        assertNull(MapCoordinateParser.parse("Привет союзники"))
        assertNull(MapCoordinateParser.parse(""))
    }

    @Test
    fun coordinateRangeIn_findsSuffix() {
        val text = "База X:5 Y:6"
        val range = MapCoordinateParser.coordinateRangeIn(text)
        assertNotNull(range)
        assertEquals("X:5 Y:6", text.substring(range!!.first, range.last + 1))
    }

    @Test
    fun parseSharedText_embedded() {
        val c = MapCoordinateParser.parseSharedText("Target: Fort Alpha X:300 Y:400 extra")
        assertNotNull(c)
        assertEquals(300, c!!.x)
        assertEquals(400, c.y)
    }

    @Test
    fun formatter_targetName() {
        assertEquals(
            "Fort X:1 Y:2",
            MapCoordinateFormatter.format(label = null, targetName = "Fort", x = 1, y = 2),
        )
    }

    @Test
    fun formatter_label() {
        assertEquals(
            "Атака X:1 Y:2",
            MapCoordinateFormatter.format(label = "Атака", targetName = null, x = 1, y = 2),
        )
    }
}
