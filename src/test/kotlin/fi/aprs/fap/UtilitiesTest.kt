package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap utilities_test.go

class UtilitiesTest {

    @Test
    fun testDistance() {
        val d = distance(60.17, 24.94, 61.50, 23.78)
        assertTrue(d in 155.0..165.0, "distance Helsinki-Tampere: got $d, want ~161")
    }

    @Test
    fun testDirectionNorth() {
        val d = direction(60.0, 25.0, 61.0, 25.0)
        assertTrue(d > 355 || d < 5, "direction north: got $d, want ~0")
    }

    @Test
    fun testDirectionEast() {
        val d = direction(60.0, 25.0, 60.0, 26.0)
        assertTrue(d in 85.0..95.0, "direction east: got $d, want ~90")
    }
}
