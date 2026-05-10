package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap wx_basic_test.go

class WxBasicTest {

    @Test
    fun testWxBasic() {
        val packet = "OH2RDP-1>BEACON-15,WIDE2-1,qAo,OH2MQK-1:=6030.35N/02443.91E_150/002g004t039r001P002p004h00b10125XRSW"
        val p = parse(packet)

        assertEquals("OH2RDP-1", p.srcCallsign)
        assertEquals("BEACON-15", p.dstCallsign)
        assertEquals(60.5058, p.latitude!!, 0.0001)
        assertEquals(24.7318, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)

        val wx = assertNotNull(p.wx)
        assertEquals(150.0, wx.windDirection!!, 0.001)
        assertEquals(0.9, wx.windSpeed!!, 0.05)
        assertEquals(1.8, wx.windGust!!, 0.05)
        assertEquals(3.9, wx.temp!!, 0.05)
        assertEquals(100, wx.humidity)
        assertEquals(1012.5, wx.pressure!!, 0.05)
        assertEquals(1.0, wx.rain24h!!, 0.05)
        assertEquals(0.3, wx.rain1h!!, 0.05)
        assertEquals(0.5, wx.rainMidnight!!, 0.05)
        assertEquals("XRSW", wx.software)
    }

    @Test
    fun testWxWithComment() {
        val packet = "OH2GAX>APU25N,TCPIP*,qAC,OH2GAX:@101317z6024.78N/02503.97E_156/001g005t038r000p000P000h91b10093/type ?sade for more wx info"
        val p = parse(packet)

        assertEquals(60.4130, p.latitude!!, 0.0001)
        assertEquals(25.0662, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)
        assertEquals("/type ?sade for more wx info", p.comment)

        val wx = assertNotNull(p.wx)
        assertEquals(156.0, wx.windDirection!!, 0.001)
        assertEquals(0.4, wx.windSpeed!!, 0.05)
        assertEquals(2.2, wx.windGust!!, 0.05)
        assertEquals(3.3, wx.temp!!, 0.05)
        assertEquals(91, wx.humidity)
        assertEquals(1009.3, wx.pressure!!, 0.05)
        assertEquals(0.0, wx.rain24h!!, 0.001)
        assertEquals(0.0, wx.rain1h!!, 0.001)
        assertEquals(0.0, wx.rainMidnight!!, 0.001)
    }

    @Test
    fun testWxThirdWithComment() {
        val packet = "JH9YVX>APU25N,TCPIP*,qAC,T2TOKYO3:@011241z3558.58N/13629.67E_068/001g001t033r000p020P020b09860h98Oregon WMR100N Weather Station {UIV32N}"
        val p = parse(packet)

        assertEquals(35.9763, p.latitude!!, 0.0001)
        assertEquals(136.4945, p.longitude!!, 0.0001)
        assertEquals(18.52, p.posResolution!!, 0.01)
        assertEquals("Oregon WMR100N Weather Station {UIV32N}", p.comment)

        val wx = assertNotNull(p.wx)
        assertEquals(68.0, wx.windDirection!!, 0.001)
        assertEquals(0.4, wx.windSpeed!!, 0.05)
        assertEquals(0.4, wx.windGust!!, 0.05)
        assertEquals(0.6, wx.temp!!, 0.05)
        assertEquals(98, wx.humidity)
        assertEquals(986.0, wx.pressure!!, 0.05)
        assertEquals(0.0, wx.rain1h!!, 0.001)
        assertEquals(5.1, wx.rain24h!!, 0.05)
        assertEquals(5.1, wx.rainMidnight!!, 0.05)
    }

    @Test
    fun testWxNoWindDirectionCourse() {
        val packet = "N0CALL>APU25N,TCPIP*,qAC,T2TOKYO3:@011241z3558.58N/13629.67E_.../...g001t033r000p020P020b09860h98Oregon WMR100N Weather Station {UIV32N}"
        val p = parse(packet)

        val wx = assertNotNull(p.wx)
        assertEquals(0.4, wx.windGust!!, 0.05)
    }

    @Test
    fun testWxNoWindNoTemp() {
        val packet = "N0CALL>APJLSX,TCPIP*,qAS,KG4EXY:@061750z3849.10N/07725.10W_.../...g...t...r008p011P011b.....h.."
        val p = parse(packet)

        val wx = assertNotNull(p.wx)
        assertEquals(2.0, wx.rain1h!!, 0.05)
    }

    @Test
    fun testWxSpaceInWindGust() {
        val packet = "N0CALL>APU25N,TCPIP*,qAC,T2TOKYO3:@011241z3558.58N/13629.67E_.../...g   t033r000p020P020b09860h98Oregon WMR100N Weather Station {UIV32N}"
        val p = parse(packet)

        val wx = assertNotNull(p.wx)
        assertEquals(0.6, wx.temp!!, 0.05)
    }

    @Test
    fun testWxWaterLevel() {
        data class TC(val name: String, val packet: String, val want: Double?, val wantNil: Boolean = false)
        val tests = listOf(
            TC("positive", "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033F0123", 123.0/10.0*0.3048),
            TC("negative", "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033F-042", -42.0/10.0*0.3048),
            TC("missing",  "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033F....", null, wantNil = true),
        )
        for (tc in tests) {
            val p = parse(tc.packet)
            val wx = assertNotNull(p.wx, tc.name)
            if (tc.wantNil) {
                assertNull(wx.waterLevel, tc.name)
            } else {
                assertEquals(tc.want!!, wx.waterLevel!!, 0.0001, tc.name)
            }
        }
    }

    @Test
    fun testWxRadiation() {
        data class TC(val name: String, val packet: String, val want: Double, val wantNil: Boolean = false)
        val tests = listOf(
            TC("12 microsieverts/hr", "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033X123", 12000.0),
            TC("45 millisieverts/hr", "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033X456", 45000000.0),
            TC("zero exponent",       "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033X100", 10.0),
            TC("missing",             "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033X...", 0.0, wantNil = true),
        )
        for (tc in tests) {
            val p = parse(tc.packet)
            val wx = assertNotNull(p.wx, tc.name)
            if (tc.wantNil) {
                assertNull(wx.radiation, tc.name)
            } else {
                assertEquals(tc.want, wx.radiation!!, 0.0001, tc.name)
            }
        }
    }

    @Test
    fun testWxBatteryVoltage() {
        data class TC(val name: String, val packet: String, val want: Double, val wantNil: Boolean = false)
        val tests = listOf(
            TC("battery voltage", "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033V128", 12.8),
            TC("missing",         "N0CALL>APRS,TCPIP*:_12032359c000s000g000t033V...", 0.0, wantNil = true),
        )
        for (tc in tests) {
            val p = parse(tc.packet)
            val wx = assertNotNull(p.wx, tc.name)
            if (tc.wantNil) {
                assertNull(wx.batteryVoltage, tc.name)
            } else {
                assertEquals(tc.want, wx.batteryVoltage!!, 0.05, tc.name)
            }
        }
    }

    @Test
    fun testIsSoftwareID() {
        data class TC(val input: String, val want: Boolean)
        val tests = listOf(
            TC("XRSW", true), TC("abc", true), TC("Ab-_9", true), TC("Z1234", true),
            TC("ab", false), TC("", false), TC("abcdef", false),
            TC("ab c", false), TC("ab.c", false), TC("ab/c", false), TC("XR{W", false),
        )
        for (tc in tests) {
            assertEquals(tc.want, isSoftwareID(tc.input), "isSoftwareID(${tc.input})")
        }
    }

    @Test
    fun testWxAllFieldsMissing() {
        val packet = "N0CALL>APRS,TCPIP*:_12032359c...s...g...t...r...p...P...h..b.....#...Os...L...F....X...V..."
        val p = parse(packet)
        val wx = assertNotNull(p.wx)
        assertNull(wx.windDirection)
        assertNull(wx.windSpeed)
        assertNull(wx.windGust)
        assertNull(wx.temp)
        assertNull(wx.rain1h)
        assertNull(wx.rain24h)
        assertNull(wx.rainMidnight)
        assertNull(wx.humidity)
        assertNull(wx.pressure)
        assertNull(wx.snow24h)
        assertNull(wx.luminosity)
        assertNull(wx.waterLevel)
        assertNull(wx.radiation)
        assertNull(wx.batteryVoltage)
    }

    @Test
    fun testWxPositionlessWithSnowfall() {
        val packet = "JH9YVX>APU25N,TCPIP*,qAC,T2TOKYO3:_12032359c180s001g002t033r010p040P080#456b09860h98Os010L500"
        val p = parse(packet)

        assertNull(p.latitude)
        assertNull(p.longitude)
        assertNull(p.posResolution)

        val wx = assertNotNull(p.wx)
        assertEquals(180.0, wx.windDirection!!, 0.001)
        assertEquals(0.4, wx.windSpeed!!, 0.05)
        assertEquals(0.9, wx.windGust!!, 0.05)
        assertEquals(0.6, wx.temp!!, 0.05)
        assertEquals(98, wx.humidity)
        assertEquals(986.0, wx.pressure!!, 0.05)
        assertEquals(2.5, wx.rain1h!!, 0.05)
        assertEquals(10.2, wx.rain24h!!, 0.05)
        assertEquals(20.3, wx.rainMidnight!!, 0.05)
        assertEquals(2.5, wx.snow24h!!, 0.05)
        assertEquals(500, wx.luminosity)
    }
}
