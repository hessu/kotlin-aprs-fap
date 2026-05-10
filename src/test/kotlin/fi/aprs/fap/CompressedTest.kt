package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap compressed_test.go

class CompressedTest {

    @Test
    fun testCompressedNonMoving() {
        val header = "OH2KKU-15>APRS,TCPIP*,qAC,FOURTH"
        val body = "!I0-X;T_Wv&{-Aigate testing"
        val p = parse("$header:$body")

        assertEquals("OH2KKU-15", p.srcCallsign)
        assertEquals("APRS", p.dstCallsign)
        assertEquals(header, p.header)
        assertEquals(body, p.bodyAsString())
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(Format.COMPRESSED, p.format)
        assertEquals("igate testing", p.comment)

        assertEquals(3, p.digipeaters.size)
        val wantDigis = listOf(
            Digipeater("TCPIP", true),
            Digipeater("qAC", false),
            Digipeater("FOURTH", false),
        )
        for ((i, want) in wantDigis.withIndex()) {
            assertEquals(want.call, p.digipeaters[i].call, "digi[$i].call")
            assertEquals(want.wasDigied, p.digipeaters[i].wasDigied, "digi[$i].wasDigied")
        }

        assertEquals('I', p.symbolTable)
        assertEquals('&', p.symbolCode)
        assertNull(p.posAmbiguity)
        assertEquals(false, p.messaging)

        assertEquals(60.0520, p.latitude!!, 0.0001)
        assertEquals(24.5045, p.longitude!!, 0.0001)
        assertEquals(0.291, p.posResolution!!, 0.001)

        assertNull(p.speed)
        assertNull(p.course)
        assertNull(p.altitude)
    }

    @Test
    fun testCompressedMoving() {
        val header = "OH2LCQ-10>APZMDR,WIDE3-2,qAo,OH2MQK-1"
        val comment = "Tero, Green Volvo 960, GGL-880"
        val body = "!//zPHTfVv>!V_ $comment|!!!!!!!!!!!!!!|"
        val p = parse("$header:$body")

        assertEquals("OH2LCQ-10", p.srcCallsign)
        assertEquals("APZMDR", p.dstCallsign)
        assertEquals(header, p.header)
        assertEquals(body, p.bodyAsString())
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(comment, p.comment)

        assertEquals(3, p.digipeaters.size)
        val wantDigis = listOf(
            Digipeater("WIDE3-2", false),
            Digipeater("qAo", false),
            Digipeater("OH2MQK-1", false),
        )
        for ((i, want) in wantDigis.withIndex()) {
            assertEquals(want.call, p.digipeaters[i].call, "digi[$i].call")
            assertEquals(want.wasDigied, p.digipeaters[i].wasDigied, "digi[$i].wasDigied")
        }

        assertEquals('/', p.symbolTable)
        assertEquals('>', p.symbolCode)
        assertNull(p.posAmbiguity)
        assertEquals(false, p.messaging)

        assertEquals(60.3582, p.latitude!!, 0.0001)
        assertEquals(24.8084, p.longitude!!, 0.0001)
        assertEquals(0.291, p.posResolution!!, 0.001)

        assertEquals(107.57, p.speed!!, 0.01)
        assertEquals(360, p.course)
        assertNull(p.altitude)
    }

    @Test
    fun testCompressedTooShort() {
        assertFailsWith<ParseException> {
            parse("KJ4ERJ-AL>APWW05,TCPIP*,qAC,FOURTH:@075111h/@@.Y:*lol ")
        }
    }

    @Test
    fun testCompressedWeather() {
        val p = parse("SV4IKL-2>APU25N,WIDE2-2,qAR,SV6EXB-1:@011444z/:JF!T/W-_e!bg001t054r000p010P010h65b10073WS 2300 {UIV32N}")

        assertEquals('/', p.symbolTable)
        assertEquals('_', p.symbolCode)
        assertEquals("WS 2300 {UIV32N}", p.comment)

        val wx = assertNotNull(p.wx)
        assertEquals(0.4, wx.windGust!!, 0.05)
        assertEquals(12.2, wx.temp!!, 0.05)
        assertEquals(65, wx.humidity)
        assertEquals(1007.3, wx.pressure!!, 0.05)
    }

    @Test
    fun testCompressedWeatherSpaceGust() {
        val p = parse("SV4IKL-2>APU25N,WIDE2-2,qAR,SV6EXB-1:@011444z/:JF!T/W-_e!bg   t054r000p010P010h65b10073WS 2300 {UIV32N}")
        val wx = assertNotNull(p.wx)
        assertEquals(12.2, wx.temp!!, 0.05)
    }
}
