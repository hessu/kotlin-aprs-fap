package fi.aprs.fap

import kotlin.test.*

// Tests ported from perl-aprs-fap/t/10badpacket.t

class BadPacketTest {

    @Test
    fun testBadPacketCorruptedPosition() {
        val packet = "OH2RDP-1>BEACON-15,OH2RDG*,WIDE:!60ff.51N/0250akh3r99hfae"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.LOC_INV, ex.code)
        assertNotNull(ex.packet)
        assertEquals(PacketType.LOCATION, ex.packet!!.type)
        assertEquals("OH2RDP-1", ex.packet!!.srcCallsign)
        assertEquals("BEACON-15", ex.packet!!.dstCallsign)
        assertNull(ex.packet!!.latitude)
        assertNull(ex.packet!!.longitude)
    }

    @Test
    fun testBadPacketBadSrcCall() {
        val packet = "K6IFR_S>APJS10,TCPIP*,qAC,K6IFR-BS:;K6IFR B *250300z3351.79ND11626.40WaRNG0040 440 Voice 447.140 -5.00 Mhz"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.SRCCALL_BADCHARS, ex.code)
        assertNotNull(ex.packet)
        assertNull(ex.packet!!.type)
    }

    @Test
    fun testBadPacketBadDigiCall() {
        val packet = "SV2BRF-6>APU25N,TCPXX*,qAX,SZ8L_GREE:=/:${'$'}U#T<:G- BVagelis, qrv:434.350, tsq:77 {UIV32N}"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.DIGICALL_BADCHARS, ex.code)
        assertNotNull(ex.packet)
        assertNull(ex.packet!!.type)
    }

    @Test
    fun testBadPacketBadSymbolTable() {
        val packet = "ASDF>DSALK,OH2RDG*,WIDE:!6028.51N,02505.68E#"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.SYM_INV_TABLE, ex.code)
    }

    @Test
    fun testBadPacketExperimental() {
        val packet = "ASDF>DSALK,OH2RDG*,WIDE:{{ unsupported experimental format"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.EXP_UNSUPP, ex.code)
    }
}
