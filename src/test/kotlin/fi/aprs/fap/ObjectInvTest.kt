package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap object_inv_test.go

class ObjectInvTest {
    @Test
    fun testObjectInvalidBroken() {
        val ex =
            assertFailsWith<ParseException> {
                parse("OH2KKU-1>APRS,TCPIP*,qAC,FIRST:;SRAL HQ *110507zS0%E/Th4_a AKaupinmaenpolku9,open M-Th12-17,F12-14 lcl")
            }
        assertEquals(ParseError.OBJ_INV, ex.code)
        assertEquals(PacketType.OBJECT, ex.packet?.type)
    }
}
