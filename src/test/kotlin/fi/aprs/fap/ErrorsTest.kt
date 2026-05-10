package fi.aprs.fap

import kotlin.test.*

class ErrorsTest {

    @Test
    fun testParseExceptionCode() {
        val ex = ParseException("test_code", "test message")
        assertEquals("test_code", ex.code)
        assertEquals("test message", ex.message)
        assertNull(ex.packet)
    }

    @Test
    fun testParseExceptionWithPacket() {
        val p = Packet(ByteArray(0))
        val ex = ParseException("obj_short", "object packet too short", p)
        assertEquals("obj_short", ex.code)
        assertSame(p, ex.packet)
    }

    @Test
    fun testParseExceptionCodeConstants() {
        assertEquals("obj_short", ParseError.OBJ_SHORT)
        assertEquals("obj_inv", ParseError.OBJ_INV)
        assertEquals("pos_short", ParseError.POS_SHORT)
        assertEquals("loc_amb_inv", ParseError.LOC_AMB_INV)
    }
}
