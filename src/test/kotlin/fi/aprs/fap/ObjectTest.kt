package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap object_test.go

class ObjectTest {
    @Test
    fun testObjectCompressed() {
        val packet = "N0CALL-1>APRS,TCPIP*,qAC,FIRST:;SRAL HQ  *100927zS0%E/Th4_a  AKaupinmaenpolku9,open M-Th12-17,F12-14 lcl"
        val p = parse(packet)

        assertEquals(PacketType.OBJECT, p.type)
        assertEquals("SRAL HQ  ", p.objectName)
        assertEquals(true, p.alive)
        assertEquals('S', p.symbolTable)
        assertEquals('a', p.symbolCode)
        assertEquals(60.2305, p.latitude!!, 0.0001)
        assertEquals(24.8790, p.longitude!!, 0.0001)
        assertEquals(0.291, p.posResolution!!, 0.001)
        assertEquals("", p.phg)
        assertEquals("Kaupinmaenpolku9,open M-Th12-17,F12-14 lcl", p.comment)
    }

    @Test
    fun testObjectUncompressed() {
        val packet = "N0CALL-1>APRS:;LEADER   *092345z4903.50N/07201.75W>088/036"
        val p = parse(packet)

        assertEquals(PacketType.OBJECT, p.type)
        assertEquals("LEADER   ", p.objectName)
        assertEquals(true, p.alive)
        assertEquals(49.0583, p.latitude!!, 0.0001)
        assertEquals(-72.0292, p.longitude!!, 0.0001)
        assertEquals(18.520, p.posResolution!!, 0.001)
        assertEquals("", p.phg)
        assertEquals("", p.comment)
    }

    @Test
    fun testObjectKilled() {
        val packet = "N0CALL-1>APRS:;LEADER   _092345z4903.50N/07201.75W>088/036"
        val p = parse(packet)

        assertEquals(PacketType.OBJECT, p.type)
        assertEquals("LEADER   ", p.objectName)
        assertEquals(false, p.alive)
    }

    @Test
    fun testObjectErrors() {
        data class TC(val name: String, val packet: String, val wantCode: String)

        val tests =
            listOf(
                TC("too short", "N0CALL-1>APRS:;SHORT", ParseError.OBJ_SHORT),
                TC("invalid alive/killed char", "N0CALL-1>APRS:;LEADER   X092345z4903.50N/07201.75W>", ParseError.OBJ_INV),
            )

        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse(tc.packet) }
            assertEquals(tc.wantCode, ex.code, tc.name)
        }
    }
}
