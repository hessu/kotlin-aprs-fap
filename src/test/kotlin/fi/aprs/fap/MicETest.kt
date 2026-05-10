package fi.aprs.fap

import kotlin.test.*

// Tests ported from perl-aprs-fap/t/23decode-mice.t

class MicETest {

    @Test
    fun testMicENonMoving() {
        val packet = "OH7LZB-13>SX15S6,TCPIP*,qAC,FOURTH:'I',l >/]"
        val p = parse(packet)

        assertEquals("OH7LZB-13", p.srcCallsign)
        assertEquals("SX15S6", p.dstCallsign)
        assertEquals("OH7LZB-13>SX15S6,TCPIP*,qAC,FOURTH", p.header)
        assertEquals("'I',l >/]", p.bodyAsString())
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(Format.MICE, p.format)
        assertEquals("]", p.comment)

        assertEquals(3, p.digipeaters.size)
        assertEquals("TCPIP", p.digipeaters[0].call)
        assertTrue(p.digipeaters[0].wasDigied)
        assertEquals("qAC", p.digipeaters[1].call)
        assertFalse(p.digipeaters[1].wasDigied)
        assertEquals("FOURTH", p.digipeaters[2].call)
        assertFalse(p.digipeaters[2].wasDigied)

        assertEquals('/', p.symbolTable)
        assertEquals('>', p.symbolCode)
        assertEquals(0, p.posAmbiguity)
        assertNull(p.messaging)

        assertEquals(-38.2560, p.latitude!!, 0.0001)
        assertEquals(145.1860, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)

        assertEquals(0.0, p.speed!!, 0.001)
        assertEquals(0, p.course)
        assertNull(p.altitude)
    }

    @Test
    fun testMicEMoving() {
        val packet = "OH7LZB-2>TQ4W2V,WIDE2-1,qAo,OH7LZB:`c51!f?>/]\"3x}="
        val p = parse(packet)

        assertEquals("OH7LZB-2", p.srcCallsign)
        assertEquals("TQ4W2V", p.dstCallsign)
        assertEquals("OH7LZB-2>TQ4W2V,WIDE2-1,qAo,OH7LZB", p.header)
        assertEquals("`c51!f?>/]\"3x}=", p.bodyAsString())
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals("]=", p.comment)

        assertEquals(3, p.digipeaters.size)
        assertEquals("WIDE2-1", p.digipeaters[0].call)
        assertFalse(p.digipeaters[0].wasDigied)
        assertEquals("qAo", p.digipeaters[1].call)
        assertFalse(p.digipeaters[1].wasDigied)
        assertEquals("OH7LZB", p.digipeaters[2].call)
        assertFalse(p.digipeaters[2].wasDigied)

        assertEquals('/', p.symbolTable)
        assertEquals('>', p.symbolCode)
        assertEquals(0, p.posAmbiguity)
        assertNull(p.messaging)
        assertEquals("110", p.mBits)

        assertEquals(41.7877, p.latitude!!, 0.0001)
        assertEquals(-71.4202, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)

        assertEquals(105.56, p.speed!!, 0.01)
        assertEquals(35, p.course)
        assertEquals(6.0, p.altitude!!, 0.001)
    }

    @Test
    fun testMicEHexTelemetry5Ch() {
        val packet = "OZ2BRN-4>5U2V08,WIDE2-1,qAo,OH7LZB:`c51!f?>/'102030FFff commeeeent"
        val p = parse(packet)
        assertEquals("commeeeent", p.comment)
        assertNotNull(p.telemetryData)
        val vals = p.telemetryData!!.vals
        assertEquals(5, vals.size)
        val expected = listOf(16.0, 32.0, 48.0, 255.0, 255.0)
        for ((i, want) in expected.withIndex()) {
            assertEquals(want, vals[i], "${i}: ")
        }
    }

    @Test
    fun testMicEHexTelemetry2Ch() {
        val packet = "OZ2BRN-4>5U2V08,WIDE2-1,qAo,OH7LZB:`c51!f?>/'1020 commeeeent"
        val p = parse(packet)
        assertEquals("commeeeent", p.comment)
        assertNotNull(p.telemetryData)
        val vals = p.telemetryData!!.vals
        assertEquals(3, vals.size)
        val expected = listOf(16.0, 0.0, 32.0)
        for ((i, want) in expected.withIndex()) {
            assertEquals(want, vals[i], "${i}: ")
        }
    }

    @Test
    fun testMicEMangled() {
        val comment = "]Greetings via ISS="
        val packet = "KD0KZE>TUPX9R,RS0ISS*,qAR,K0GDI-6:'yaIl -/" + comment
        val p = parse(packet, ParseOptions(acceptBrokenMicE = true))

        assertEquals(45.1487, p.latitude!!, 0.0001)
        assertEquals(-93.1575, p.longitude!!, 0.0001)
        assertEquals('/', p.symbolTable)
        assertEquals('-', p.symbolCode)
        assertEquals(comment, p.comment)
        assertNull(p.course)
        assertNull(p.speed)
        assertTrue(p.miceMangled)
    }

    @Test
    fun testMicEErrors() {
        data class TC(val name: String, val packet: String, val wantCode: String)

        val tests = listOf(
            TC("destination too short",     "N0CALL>AB:`c51!f?>/",                      ParseError.MICE_INV_DSTCALL),
            TC("info field too short",      "N0CALL>SX15S6:`short",                     ParseError.MICE_SHORT),
            TC("invalid destination char",  "N0CALL>SX1MS6:`c51!f?>/",                  ParseError.MICE_INV_DSTCALL),
            TC("invalid symbol table",      "OZ2BRN-4>5U2V08,OZ3RIN-3,OZ4DIA-2*,WIDE2-1,qAR,DB0KUE:`'O<l!{,,\"4R}", ParseError.SYM_INV_TABLE),
        )

        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse(tc.packet) }
            assertEquals(tc.wantCode, ex.code, tc.name)
        }
    }

    @Test
    fun testMicEMangledErrors() {
        data class TC(val name: String, val packet: String, val wantCode: String)

        val tests = listOf(
            TC("destination too short", "N0CALL>AB:`c51!f?>/",                   ParseError.MICE_SHORT),
            TC("invalid info field",    "N0CALL>SX15S6:`c51!f?>" + "",     ParseError.MICE_INV_INFOFIELD),
            TC("invalid dest char",     "N0CALL>SX1MS6:`@@@@ >/",                ParseError.MICE_INV_DSTCALL),
        )

        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) {
                parse(tc.packet, ParseOptions(acceptBrokenMicE = true))
            }
            assertEquals(tc.wantCode, ex.code, tc.name)
        }
    }

    @Test
    fun testMicEMBitsToMessage() {
        assertEquals("Off Duty",  micEMBitsToMessage("111"))
        assertEquals("En Route",  micEMBitsToMessage("110"))
        assertEquals("Emergency", micEMBitsToMessage("000"))
    }
}
