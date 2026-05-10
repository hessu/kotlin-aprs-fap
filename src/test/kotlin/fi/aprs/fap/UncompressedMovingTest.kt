package fi.aprs.fap

import kotlin.test.*

// Tests ported from perl-aprs-fap/t/21decode-uncomp-moving.t

class UncompressedMovingTest {

    @Test
    fun testUncompressedMoving() {
        val header = "OH7FDN>APZMDR,OH7AA-1*,WIDE2-1,qAR,OH7AA"
        val body = "!6253.52N/02739.47E>036/010/A=000465 |!!!!!!!!!!!!!!|"
        val packet = "$header:$body"

        val p = parse(packet)

        assertEquals("OH7FDN", p.srcCallsign)
        assertEquals("APZMDR", p.dstCallsign)
        assertEquals(header, p.header)
        assertEquals(body, p.bodyAsString())
        assertEquals(PacketType.LOCATION, p.type)

        assertEquals(4, p.digipeaters.size)
        val expectedDigis = listOf(
            "OH7AA-1" to true,
            "WIDE2-1" to false,
            "qAR" to false,
            "OH7AA" to false,
        )
        for ((i, want) in expectedDigis.withIndex()) {
            assertEquals(want.first, p.digipeaters[i].call, "digi[$i].call")
            assertEquals(want.second, p.digipeaters[i].wasDigied, "digi[$i].wasDigied")
        }

        assertEquals('/', p.symbolTable)
        assertEquals('>', p.symbolCode)
        assertEquals(0, p.posAmbiguity)
        assertEquals(false, p.messaging)

        assertEquals(62.8920, p.latitude!!, 0.0001)
        assertEquals(27.6578, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)

        // Speed: 10 knots * 1.852 = 18.52 km/h
        assertNotNull(p.speed)
        assertEquals(18.52, p.speed!!, 0.01)
        assertEquals(36, p.course)

        // Altitude: 465 feet * 0.3048 = 141.732 meters
        assertNotNull(p.altitude)
        assertEquals(141.732, p.altitude!!, 0.001)
    }
}
