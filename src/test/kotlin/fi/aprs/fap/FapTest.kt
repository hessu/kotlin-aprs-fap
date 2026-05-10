package fi.aprs.fap

import kotlin.test.*

class FapTest {

    @Test
    fun testParseStatus() {
        val p = parse("OH7LZB>APRS:>Testing status")
        assertEquals(PacketType.STATUS, p.type)
        assertEquals("Testing status", p.status)
    }

    @Test
    fun testParseNoBody() {
        val ex = assertFailsWith<ParseException> { parse("OH7LZB>APRS") }
        assertEquals(ParseError.PACKET_NO_BODY, ex.code)
    }

    @Test
    fun testParseNoGT() {
        val ex = assertFailsWith<ParseException> { parse("OH7LZB:body") }
        assertEquals(ParseError.SRCCALL_NOGT, ex.code)
    }

    @Test
    fun testIsIPv6Hex() {
        // Valid: 32 uppercase hex chars
        assertTrue(isIPv6Hex("00000000000000000000000000000000"))
        assertTrue(isIPv6Hex("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"))
        assertTrue(isIPv6Hex("0123456789ABCDEF0123456789ABCDEF"))

        // Too short / too long / empty
        assertFalse(isIPv6Hex("0123456789ABCDEF0123456789ABCDE"))
        assertFalse(isIPv6Hex("0123456789ABCDEF0123456789ABCDEF0"))
        assertFalse(isIPv6Hex(""))

        // Lowercase not accepted
        assertFalse(isIPv6Hex("0123456789abcdef0123456789abcdef"))
        assertFalse(isIPv6Hex("0123456789ABCDEf0123456789ABCDEF"))

        // Non-hex characters
        assertFalse(isIPv6Hex("G0000000000000000000000000000000"))
        assertFalse(isIPv6Hex("0000000000000000000000000000000Z"))
        assertFalse(isIPv6Hex("00000000000000000000000000000 00"))
    }

    @Test
    fun testParseErrors() {
        data class TC(val name: String, val packet: String, val opts: ParseOptions = ParseOptions(), val wantCode: String)

        val tests = listOf(
            TC("no colon",               "OH7LZB>APRS",                          wantCode = ParseError.PACKET_NO_BODY),
            TC("empty body",             "OH7LZB>APRS:",                         wantCode = ParseError.PACKET_NO_BODY),
            TC("no > in header",         "OH7LZB:body",                          wantCode = ParseError.SRCCALL_NOGT),
            TC("empty source callsign",  ">APRS:body",                           wantCode = ParseError.SRCCALL_EMPTY),
            TC("source bad chars",       "OH7_LZB>APRS:body",                    wantCode = ParseError.SRCCALL_BADCHARS),
            TC("source not AX.25",       "TOOLONGCALL>APRS:body",   ParseOptions(isAX25 = true), ParseError.SRCCALL_NOAX25),
            TC("empty destination",      "OH7LZB>:body",                         wantCode = ParseError.DSTCALL_EMPTY),
            TC("destination not AX.25",  "OH7LZB>!!!:body",                      wantCode = ParseError.DSTCALL_NOAX25),
            TC("too many path AX.25",    "OH7LZB>APRS,A,B,C,D,E,F,G,H,I:body", ParseOptions(isAX25 = true), ParseError.DSTPATH_TOOMANY),
            TC("empty digi",             "OH7LZB>APRS,,WIDE:body",               wantCode = ParseError.DIGI_EMPTY),
            TC("empty digi after star",  "OH7LZB>APRS,*:body",                   wantCode = ParseError.DIGI_EMPTY),
            TC("digi bad chars",         "OH7LZB>APRS,WI_DE:body",               wantCode = ParseError.DIGICALL_BADCHARS),
            TC("digi not AX.25",         "OH7LZB>APRS,TOOLONGCALL:body",  ParseOptions(isAX25 = true), ParseError.DIGICALL_NOAX25),
        )

        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse(tc.packet, tc.opts) }
            assertEquals(tc.wantCode, ex.code, tc.name)
        }
    }

    @Test
    fun testExampleParse() {
        val packet = "OH7LZB-2>APRS,WIDE1-1,WIDE2-1,qAo,OH7LZB:!6128.23N/02353.52E-PHG2360/Testing"
        val p = parse(packet)
        assertEquals("OH7LZB-2", p.srcCallsign)
        assertEquals(PacketType.LOCATION, p.type)
        assertNotNull(p.latitude)
        assertNotNull(p.longitude)
        assertEquals(61.4705, p.latitude!!, 0.0001)
        assertEquals(23.8920, p.longitude!!, 0.0001)
    }
}
