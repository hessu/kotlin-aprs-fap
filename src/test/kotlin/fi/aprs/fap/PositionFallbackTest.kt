package fi.aprs.fap

import kotlin.test.*

class PositionFallbackTest {

    @Test
    fun testFallbackUncompressed() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:hoponassualku!6028.51S/02505.68W#PHG7220/RELAY,WIDE, OH2AP Jarvenpaa"
        val p = parse(packet)
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(Format.UNCOMPRESSED, p.format)
        assertEquals(-60.4752, p.latitude!!, 0.0001)
        assertEquals(-25.0947, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)
        assertEquals("RELAY,WIDE, OH2AP Jarvenpaa", p.comment)
    }

    @Test
    fun testFallbackCompressed() {
        val packet = "OH2KKU>APRS,TCPIP*:hello!/I0-X;T_Wv&{-Aigate testing"
        val p = parse(packet)
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(Format.COMPRESSED, p.format)
        assertNotNull(p.latitude)
        assertNotNull(p.longitude)
    }

    @Test
    fun testFallbackExclAtOffset39() {
        val padding = "012345678901234567890123456789012345678" // 39 chars
        val packet = "OH2RDP-1>BEACON-15:${padding}!6028.51N/02505.68E#"
        val p = parse(packet)
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(60.4752, p.latitude!!, 0.0001)
    }

    @Test
    fun testFallbackExclAtOffset40() {
        val padding = "0123456789012345678901234567890123456789" // 40 chars
        val packet = "OH2RDP-1>BEACON-15:${padding}!6028.51N/02505.68E#"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.TYPE_NOT_SUPPORTED, ex.code)
    }

    @Test
    fun testFallbackNoExcl() {
        val packet = "OH2RDP-1>BEACON-15:Tno position here at all"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.TYPE_NOT_SUPPORTED, ex.code)
    }

    @Test
    fun testFallbackUncompressedTooShort() {
        val packet = "OH2RDP-1>BEACON-15:X!6028.51N/02505.6"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.TYPE_NOT_SUPPORTED, ex.code)
    }

    @Test
    fun testFallbackCompressedTooShort() {
        val packet = "OH2RDP-1>BEACON-15:X!/I0-X;T_Wv&"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.TYPE_NOT_SUPPORTED, ex.code)
    }

    @Test
    fun testFallbackBadCompressedTableChar() {
        val packet = "OH2RDP-1>BEACON-15:X!zI0-X;T_Wv&{-A"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.TYPE_NOT_SUPPORTED, ex.code)
    }
}
