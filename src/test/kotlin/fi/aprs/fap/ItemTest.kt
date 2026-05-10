package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap item_test.go

class ItemTest {
    @Test
    fun testItemAlive() {
        val packet = "N0CALL-1>APRS:)AID #2!4903.50N/07201.75WA"
        val p = parse(packet)

        assertEquals(PacketType.ITEM, p.type)
        assertEquals("AID #2", p.itemName)
        assertEquals(true, p.alive)

        assertEquals(49.0583, p.latitude!!, 0.0001)
        assertEquals(-72.0292, p.longitude!!, 0.0001)
        assertEquals('/', p.symbolTable)
        assertEquals('A', p.symbolCode)
    }

    @Test
    fun testItemKilled() {
        val packet = "N0CALL-1>APRS:)AID #2_4903.50N/07201.75WA"
        val p = parse(packet)

        assertEquals(PacketType.ITEM, p.type)
        assertEquals("AID #2", p.itemName)
        assertEquals(false, p.alive)
    }

    @Test
    fun testItemShortName() {
        val packet = "N0CALL-1>APRS:)X1Y!4903.50N/07201.75WA"
        val p = parse(packet)

        assertEquals("X1Y", p.itemName)
        assertEquals(true, p.alive)
    }

    @Test
    fun testItemMaxLengthName() {
        val packet = "N0CALL-15>APRS,TCPIP*,qAC,T2TEST:)MyRadio99!4327.00N/00119.00WlMyRadio99"
        val p = parse(packet)

        assertEquals("MyRadio99", p.itemName)
        assertEquals(true, p.alive)
        assertEquals(43.4500, p.latitude!!, 0.0001)
    }

    @Test
    fun testItemCompressedPosition() {
        val packet = "N0CALL-1>APRS:)TEST!/0%E/Th4_/  A"
        val p = parse(packet)

        assertEquals(PacketType.ITEM, p.type)
        assertEquals("TEST", p.itemName)
        assertEquals(true, p.alive)
        assertEquals(60.2305, p.latitude!!, 0.0001)
        assertEquals(24.8790, p.longitude!!, 0.0001)
        assertEquals('/', p.symbolTable)
        assertEquals('/', p.symbolCode)
    }

    @Test
    fun testItemWithCourseSpeed() {
        val packet = "N0CALL-1>APRS:)MOBILE!4903.50N/07201.75W>088/036"
        val p = parse(packet)

        assertEquals("MOBILE", p.itemName)
        assertEquals(88, p.course)
        assertEquals(66.672, p.speed!!, 0.001)
    }

    @Test
    fun testItemErrors() {
        data class TC(val name: String, val packet: String, val wantCode: String)

        val tests =
            listOf(
                TC("too short", "N0CALL-1>APRS:)SH!1234", ParseError.ITEM_SHORT),
                TC("no name terminator", "N0CALL-1>APRS:)ABCDEFGHIJ4903.50N/07201.75W>", ParseError.ITEM_INV),
            )

        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse(tc.packet) }
            assertEquals(tc.wantCode, ex.code, tc.name)
        }
    }
}
