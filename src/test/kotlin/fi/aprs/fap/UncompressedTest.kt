package fi.aprs.fap

import kotlin.test.*

// Tests ported from perl-aprs-fap/t/20decode-uncompressed.t

class UncompressedTest {

    @Test
    fun testUncompressedNortheast() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!6028.51N/02505.68E#PHG7220/RELAY,WIDE, OH2AP Jarvenpaa"
        val p = parse(packet)
        assertEquals(Format.UNCOMPRESSED, p.format)
        assertEquals("OH2RDP-1", p.srcCallsign)
        assertEquals("BEACON-15", p.dstCallsign)
        assertEquals(60.4752, p.latitude!!, 0.0001)
        assertEquals(25.0947, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)
        assertEquals("7220", p.phg)
        assertEquals("RELAY,WIDE, OH2AP Jarvenpaa", p.comment)

        assertEquals(2, p.digipeaters.size)
        assertEquals("OH2RDG", p.digipeaters[0].call)
        assertTrue(p.digipeaters[0].wasDigied)
        assertEquals("WIDE", p.digipeaters[1].call)
        assertFalse(p.digipeaters[1].wasDigied)
    }

    @Test
    fun testUncompressedSouthwest() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!6028.51S/02505.68W#PHG7220RELAY,WIDE, OH2AP Jarvenpaa"
        val p = parse(packet)
        assertEquals(-60.4752, p.latitude!!, 0.0001)
        assertEquals(-25.0947, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)
    }

    @Test
    fun testUncompressedAmbiguity3() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!602 .  S/0250 .  W#PHG7220RELAY,WIDE, OH2AP Jarvenpaa"
        val p = parse(packet)
        assertEquals(-60.4167, p.latitude!!, 0.0001)
        assertEquals(-25.0833, p.longitude!!, 0.0001)
        assertEquals(3, p.posAmbiguity)
        assertEquals(18520.0, p.posResolution!!, 1.0)
    }

    @Test
    fun testUncompressedAmbiguity4() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!60  .  S/025  .  W#PHG7220RELAY,WIDE, OH2AP Jarvenpaa"
        val p = parse(packet)
        assertEquals(-60.5000, p.latitude!!, 0.0001)
        assertEquals(-25.5000, p.longitude!!, 0.0001)
        assertEquals(4, p.posAmbiguity)
        assertEquals(111120.0, p.posResolution!!, 1.0)
    }

    @Test
    fun testUncompressedAmbiguityUnbalanced() {
        val packet = "SRC>APRS,TCPIP*,qAC,T2POLAND:;SPBLTZ   *010020z5210.  N/021  .  E? Comment"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.LOC_AMB_INV, ex.code)
    }

    @Test
    fun testUncompressedAmbiguityLonMoreDigits() {
        // Latitude ambiguity 1 applies to longitude too
        val packet = "OH2RDP-1>BEACON-15:!5210.5 N/02101.73E#Comment here"
        val p = parse(packet)
        assertEquals(1, p.posAmbiguity)
        assertEquals(185.2, p.posResolution!!, 0.1)
        assertEquals(52.1758, p.latitude!!, 0.0001)
        assertEquals(21.0292, p.longitude!!, 0.0001)
    }

    @Test
    fun testUncompressedLastResort() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:hoponassualku!6028.51S/02505.68W#PHG7220RELAY,WIDE, OH2AP Jarvenpaa"
        val p = parse(packet)
        assertEquals(-60.4752, p.latitude!!, 0.0001)
        assertEquals(-25.0947, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)
        assertEquals("RELAY,WIDE, OH2AP Jarvenpaa", p.comment)
    }

    @Test
    fun testUncompressedWxSymbolComment() {
        val packet = "A0RID-1>KC0PID-7,WIDE1,qAR,NX0R-6:=3851.38N/09908.75W_Home of KA0RID"
        val p = parse(packet)
        assertEquals(38.8563, p.latitude!!, 0.0001)
        assertEquals(-99.1458, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)
        assertEquals("", p.comment)
    }

    @Test
    fun testUncompressedWhitespaceTrimming() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!6028.51N/02505.68E#PHG7220   RELAY,WIDE, OH2AP Jarvenpaa  \t "
        val p = parse(packet)
        assertEquals("7220", p.phg)
        assertEquals("RELAY,WIDE, OH2AP Jarvenpaa", p.comment)
    }

    @Test
    fun testPHGRA() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!6028.51N/02505.68E#PHG72205/RELAY,WIDE, OH2AP Jarvenpaa"
        val p = parse(packet)
        assertEquals("72205", p.phg)
        assertEquals("RELAY,WIDE, OH2AP Jarvenpaa", p.comment)
    }

    @Test
    fun testPHGRALetterSuffix() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!6028.51N/02505.68E#PHG7220Z/some comment"
        val p = parse(packet)
        assertEquals("7220Z", p.phg)
        assertEquals("some comment", p.comment)
    }

    @Test
    fun testPHGInvalidNotParsed() {
        // Second char 0x2f = '/' which is below 0x30 — invalid PHG
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!6028.51N/02505.68E#PHG7/20some comment"
        val p = parse(packet)
        assertEquals("", p.phg)
    }

    @Test
    fun testRNG() {
        val packet = "OH2RDP-1>BEACON-15:!6028.51N/02505.68E#RNG0050some comment"
        val p = parse(packet)
        assertNotNull(p.radioRange)
        assertEquals(80.4672, p.radioRange!!, 0.0001)
        assertEquals("some comment", p.comment)
    }

    @Test
    fun testUncompressedTimestampAltitude() {
        val packet = "YB1RUS-9>APOTC1,WIDE2-2,qAS,YC0GIN-1:/180000z0609.31S/10642.85E>058/010/A=000079 13.8V 15CYB1RUS-9 Mobile Tracker"
        val p = parse(packet)
        assertEquals(-6.15517, p.latitude!!, 0.00001)
        assertEquals(106.71417, p.longitude!!, 0.00001)
        assertNotNull(p.altitude)
        assertEquals(24.07920, p.altitude!!, 0.00001)
    }

    @Test
    fun testUncompressedNegativeAltitude() {
        val packet = "YB1RUS-9>APOTC1,WIDE2-2,qAS,YC0GIN-1:/180000z0609.31S/10642.85E>058/010/A=-00079 13.8V 15CYB1RUS-9 Mobile Tracker"
        val p = parse(packet)
        assertNotNull(p.altitude)
        assertEquals(-24.07920, p.altitude!!, 0.00001)
    }

    @Test
    fun testUncompressedBasicYC0SHR() {
        val packet = "YC0SHR>APU25N,TCPIP*,qAC,ALDIMORI:=0606.23S/10644.61E-GW SAHARA PENJARINGAN JAKARTA 147.880 MHz"
        val p = parse(packet)
        assertEquals(-6.10383, p.latitude!!, 0.00001)
        assertEquals(106.74350, p.longitude!!, 0.00001)
    }
}
