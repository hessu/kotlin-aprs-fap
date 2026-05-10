package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap wx_ultw_test.go

class WxUltwTest {

    @Test
    fun testULTW() {
        val packet = "N0CALL-14>APN391,WIDE2-1,qAo,IGATE-3:\$ULTW0053002D028D02FA2813000D87BD000103E8015703430010000C"
        val p = parse(packet)

        val wx = assertNotNull(p.wx)
        assertEquals(64.0, wx.windDirection!!, 0.001)
        assertEquals(0.3, wx.windSpeed!!, 0.05)
        assertEquals(2.3, wx.windGust!!, 0.05)
        assertEquals(18.5, wx.temp!!, 0.05)
        assertEquals(100, wx.humidity)
        assertEquals(1025.9, wx.pressure!!, 0.05)
        assertNull(wx.rain24h)
        assertNull(wx.rain1h)
        assertEquals(4.1, wx.rainMidnight!!, 0.05)
        assertEquals("", wx.software)
    }

    @Test
    fun testULTWBelowZero() {
        val packet = "N0CALL>APN391,qAo,IGATE-1:\$ULTW00000000FFEA0000296F000A9663000103E80016025D"
        val p = parse(packet)

        val wx = assertNotNull(p.wx)
        assertEquals(0.0, wx.windDirection!!, 0.001)
        assertNull(wx.windSpeed)
        assertEquals(0.0, wx.windGust!!, 0.001)
        assertEquals(-19.0, wx.temp!!, 0.05)
        assertEquals(100, wx.humidity)
        assertEquals(1060.7, wx.pressure!!, 0.05)
        assertNull(wx.rain24h)
        assertNull(wx.rain1h)
        assertEquals(0.0, wx.rainMidnight!!, 0.001)
        assertEquals("", wx.software)
    }

    @Test
    fun testULTWLogging() {
        val packet = "N0CALL>APRS,TCPIP*,qAC,APRSUK2:!!00000066013D000028710166--------0158053201200210"
        val p = parse(packet)

        val wx = assertNotNull(p.wx)
        assertEquals(144.0, wx.windDirection!!, 0.001)
        assertEquals(14.7, wx.windSpeed!!, 0.05)
        assertNull(wx.windGust)
        assertEquals(-0.2, wx.temp!!, 0.05)
        assertEquals(2.1, wx.tempIn!!, 0.05)
        assertNull(wx.humidity)
        assertNull(wx.humidityIn)
        assertEquals(1035.3, wx.pressure!!, 0.05)
        assertNull(wx.rain24h)
        assertNull(wx.rain1h)
        assertEquals(73.2, wx.rainMidnight!!, 0.05)
        assertEquals("", wx.software)
    }

    @Test
    fun testULTWLoggingHumidity() {
        val packet = "N0CALL>APRS,TCPIP*,qAC,APRSUK2:!!00000066013D00002871016602BC031E0158053201200210"
        val p = parse(packet)

        val wx = assertNotNull(p.wx)
        assertEquals(70, wx.humidity)
        assertEquals(79, wx.humidityIn)
    }
}
