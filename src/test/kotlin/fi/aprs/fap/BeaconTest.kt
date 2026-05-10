package fi.aprs.fap

import kotlin.test.*

// Tests ported from perl-aprs-fap/t/52decode-beacon.t

class BeaconTest {
    @Test
    fun testBeaconNonAPRS() {
        val p = parse("OH2RDU>UIDIGI: UIDIGI 1.9")
        assertEquals(PacketType.BEACON, p.type)
        assertEquals("OH2RDU", p.srcCallsign)
        assertEquals("UIDIGI", p.dstCallsign)
        assertEquals(" UIDIGI 1.9", p.bodyAsString())
    }

    @Test
    fun testAllBeaconDestinations() {
        for (dst in listOf("ID", "BEACON", "UIDIGI", "CQ")) {
            val p = parse("OH2RDU>$dst:hello world")
            assertEquals(PacketType.BEACON, p.type, "dst=$dst")
        }
    }

    @Test
    fun testBeaconWithPositionFallback() {
        val p = parse("OH2RDU>BEACON:testing!6028.51N/02505.68E#PHG2360")
        assertEquals(PacketType.LOCATION, p.type)
        assertNotNull(p.latitude)
        assertEquals(60.475, p.latitude!!, 0.001)
    }
}
