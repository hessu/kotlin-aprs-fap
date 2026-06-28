package fi.aprs.fap

import kotlin.test.*

// Tests for APRS "third party" format (body type identifier '}') decoding.
//
// A typical igate-to-RF third-party frame looks like:
//
//   IGATE-4>APRS,DIGI-15*,WIDE2*:}N0CALL-2>APRS,TCPIP,IGATE-4*:<payload>
//
// The outer header (IGATE-4>APRS,DIGI-15*,WIDE2*) is the relaying frame the
// igate transmitted on RF; the inner header (N0CALL-2>APRS,TCPIP,IGATE-4*) is
// the original packet, whose fields populate the main header and content fields.
//
// Third-party encapsulation is only valid on RF, so it must be explicitly
// enabled via ParseOptions(allowThirdParty = true). The AX.25/RF decode path
// (parseAx25) enables it automatically.

class ThirdPartyTest {
    private val tpOpts = ParseOptions(allowThirdParty = true)

    @Test
    fun testThirdPartyPosition() {
        val p =
            parse(
                "IGATE-4>APRS,DIGI-15*,WIDE2*:" +
                    "}N0CALL-2>APRS,TCPIP,IGATE-4*:!6028.51N/02505.68E#PHG7220",
                tpOpts,
            )

        assertTrue(p.thirdParty)

        // Inner (original) frame is in the main header fields.
        assertEquals("N0CALL-2", p.srcCallsign)
        assertEquals("APRS", p.dstCallsign)
        assertEquals(listOf("TCPIP", "IGATE-4"), p.digipeaters.map { it.call })
        assertFalse(p.digipeaters[0].wasDigied)
        assertTrue(p.digipeaters[1].wasDigied)

        // Outer (relaying) frame is in the outer fields.
        assertEquals("IGATE-4", p.outerSrcCallsign)
        assertEquals("APRS", p.outerDstCallsign)
        assertEquals(listOf("DIGI-15", "WIDE2"), p.outerDigipeaters.map { it.call })
        assertTrue(p.outerDigipeaters[0].wasDigied)
        assertTrue(p.outerDigipeaters[1].wasDigied)

        // Inner content parsed normally.
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(Format.UNCOMPRESSED, p.format)
        assertEquals(60.4752, p.latitude!!, 0.0001)
        assertEquals(25.0947, p.longitude!!, 0.0001)
    }

    @Test
    fun testThirdPartyMessage() {
        val p =
            parse(
                "IGATE-4>APRS,DIGI-15*,WIDE2*:}N0CALL-2>APRS,TCPIP,IGATE-4*::OH2RDP-1 :hello world{1",
                tpOpts,
            )

        assertTrue(p.thirdParty)
        assertEquals("N0CALL-2", p.srcCallsign)
        assertEquals("IGATE-4", p.outerSrcCallsign)
        assertEquals(PacketType.MESSAGE, p.type)
        assertNotNull(p.message)
        assertEquals("hello world", p.message!!.text)
    }

    @Test
    fun testNonThirdPartyHasFlagUnset() {
        val p = parse("N0CALL-2>APRS,TCPIP,IGATE-4*:!6028.51N/02505.68E#PHG7220", tpOpts)
        assertFalse(p.thirdParty)
        assertNull(p.outerSrcCallsign)
        assertNull(p.outerDstCallsign)
        assertTrue(p.outerDigipeaters.isEmpty())
    }

    @Test
    fun testThirdPartyFailsWhenDisabled() {
        // Without allowThirdParty, a '}' body must fail rather than fall back to
        // last-resort parsing, which would mis-attribute the inner position to
        // the outer source callsign.
        val ex =
            assertFailsWith<ParseException> {
                parse("IGATE-4>APRS,DIGI-15*,WIDE2*:}N0CALL-2>APRS,TCPIP,IGATE-4*:!6028.51N/02505.68E#")
            }
        assertEquals(ParseError.THIRDPARTY_NOT_ALLOWED, ex.code)
    }

    @Test
    fun testThirdPartyNoInnerBody() {
        val ex = assertFailsWith<ParseException> { parse("IGATE-4>APRS,DIGI-15*,WIDE2*:}N0CALL-2", tpOpts) }
        assertEquals(ParseError.THIRDPARTY_NO_BODY, ex.code)
    }

    @Test
    fun testNestedThirdPartyRejected() {
        val ex =
            assertFailsWith<ParseException> {
                parse(
                    "IGATE-4>APRS,DIGI-15*,WIDE2*:}N0CALL-2>APRS,TCPIP,IGATE-4*:" +
                        "}N0CALL-3>APRS:!6028.51N/02505.68E#",
                    tpOpts,
                )
            }
        assertEquals(ParseError.THIRDPARTY_NESTED, ex.code)
    }

    @Test
    fun testThirdPartyOverAx25Option() {
        // The inner frame uses TNC2/APRS-IS callsign rules even when the outer
        // frame is validated under strict AX.25 rules.
        val p =
            parse(
                "IGATE-4>APRS,DIGI-15*,WIDE2*:}N0CALL-2>APRS,TCPIP,IGATE-4*:!6028.51N/02505.68E#",
                ParseOptions(isAX25 = true, allowThirdParty = true),
            )
        assertTrue(p.thirdParty)
        assertEquals("N0CALL-2", p.srcCallsign)
        assertEquals("IGATE-4", p.outerSrcCallsign)
        assertEquals(PacketType.LOCATION, p.type)
    }

    @Test
    fun testThirdPartyEnabledOnRfDecode() {
        // parseAx25 is the RF path: third-party decoding is enabled automatically,
        // without the caller setting allowThirdParty.
        val inner = "}N0CALL-2>APRS,TCPIP,IGATE-4*:!6028.51N/02505.68E#".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("IGATE-4", "APRS", listOf("DIGI-15", "WIDE2"), inner)

        val result = parseAx25(frame)
        assertNull(result.parseError)
        val p = result.packet!!
        assertTrue(p.thirdParty)
        assertEquals("N0CALL-2", p.srcCallsign)
        assertEquals("IGATE-4", p.outerSrcCallsign)
        assertEquals(PacketType.LOCATION, p.type)
    }
}
