package fi.aprs.fap

import java.time.Instant
import kotlin.test.*

// Tests ported from go-aprs-fap nmea_test.go

class NmeaTest {
    @Test
    fun testGPRMC() {
        val packet = "N0CALL-11>APRS,DIGI*,WIDE2-1,qAR,IGATE:\$GPRMC,145526,A,3349.0378,N,08406.2617,W,23.726,27.9,121207,4.9,W*7A"
        val p = parse(packet)

        assertEquals("N0CALL-11>APRS,DIGI*,WIDE2-1,qAR,IGATE", p.header)
        assertEquals("\$GPRMC,145526,A,3349.0378,N,08406.2617,W,23.726,27.9,121207,4.9,W*7A", p.bodyAsString())
        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(Format.NMEA, p.format)

        assertNull(p.posAmbiguity)
        assertNull(p.messaging)

        assertEquals(true, p.checksumOK)

        val expectedTs = Instant.ofEpochSecond(1197471326)
        assertEquals(expectedTs, p.timestamp)

        assertEquals(33.8173, p.latitude!!, 0.0001)
        assertEquals(-84.1044, p.longitude!!, 0.0001)
        assertEquals(0.1852, p.posResolution!!, 0.0001)
        assertEquals(43.94, p.speed!!, 0.01)
        assertEquals(28, p.course)
        assertNull(p.altitude)
    }

    @Test
    fun testGPRMCNoFix() {
        val packet = "SRC>APRS,WIDE1-1,GATE,qAR,GATE-1:\$GPRMC,041518,V,,,,,,,230309,,*33"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.GPRMC_NOFIX, ex.code)
    }

    @Test
    fun testGPGGA() {
        val packet = "N0CALL-11>APRS,WIDE2-1,qAR,IGATE:\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,47.0,M,,*4F"
        val p = parse(packet)

        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(Format.NMEA, p.format)
        assertEquals(true, p.checksumOK)

        assertEquals(48.1173, p.latitude!!, 0.0001)
        assertEquals(11.5167, p.longitude!!, 0.0001)
        assertEquals(1.8520, p.posResolution!!, 0.0001)
        assertEquals(545.4, p.altitude!!, 0.05)

        assertNull(p.speed)
        assertNull(p.course)
        assertNull(p.timestamp)
    }

    @Test
    fun testGPGGANoFix() {
        val packet = "N0CALL-11>APRS,WIDE2-1,qAR,IGATE:\$GPGGA,123519,4807.038,N,01131.000,E,0,08,0.9,545.4,M,47.0,M,,*48"
        val ex = assertFailsWith<ParseException> { parse(packet) }
        assertEquals(ParseError.NMEA_INVALID, ex.code)
    }

    @Test
    fun testGPGLL() {
        val packet = "N0CALL-11>APRS,WIDE2-1,qAR,IGATE:\$GPGLL,4916.45,N,12311.12,W,225444,A*31"
        val p = parse(packet)

        assertEquals(PacketType.LOCATION, p.type)
        assertEquals(Format.NMEA, p.format)
        assertEquals(true, p.checksumOK)

        assertEquals(49.2742, p.latitude!!, 0.0001)
        assertEquals(-123.1853, p.longitude!!, 0.0001)
        assertEquals(18.5200, p.posResolution!!, 0.0001)

        assertNull(p.altitude)
        assertNull(p.speed)
        assertNull(p.course)
        assertNull(p.timestamp)
    }

    @Test
    fun testGPGLLMinimal() {
        val packet = "N0CALL-11>APRS,WIDE2-1,qAR,IGATE:\$GPGLL,4916.45,N,12311.12,W"
        val p = parse(packet)

        assertEquals(49.2742, p.latitude!!, 0.0001)
        assertEquals(-123.1853, p.longitude!!, 0.0001)
    }

    @Test
    fun testParseNMEACoordWithResErrors() {
        data class TC(val name: String, val coord: String, val hemisphere: String, val isLon: Boolean, val errIs: String)
        val tests =
            listOf(
                TC("empty coord", "", "N", false, "empty coordinate or hemisphere"),
                TC("empty hemisphere", "4807.038", "", false, "empty coordinate or hemisphere"),
                TC("both empty", "", "", false, "empty coordinate or hemisphere"),
                TC("lat too short", "48", "N", false, "coordinate too short"),
                TC("lon too short", "011", "E", true, "coordinate too short"),
                TC("lat invalid degrees", "XX07.038", "N", false, "invalid degrees"),
                TC("lon invalid degrees", "XXX31.000", "E", true, "invalid degrees"),
                TC("lat invalid minutes", "48XX.XXX", "N", false, "invalid minutes"),
                TC("lon invalid minutes", "011XX.XXX", "E", true, "invalid minutes"),
                TC("lat out of range", "9100.000", "N", false, "coordinate out of range"),
                TC("lon out of range", "18100.000", "E", true, "coordinate out of range"),
            )
        for (tc in tests) {
            val ex = assertFailsWith<Exception>(tc.name) { parseNMEACoordWithRes(tc.coord, tc.hemisphere, tc.isLon) }
            assertTrue(ex.message!!.contains(tc.errIs), "${tc.name}: got '${ex.message}', want contains '${tc.errIs}'")
        }
    }

    @Test
    fun testParseNMEAErrors() {
        data class TC(val name: String, val body: String, val errCode: String, val msgIs: String)
        val tests =
            listOf(
                TC("not starting with \$GP", "\$XXXXX,A,B,C", ParseError.NMEA_INVALID, "must start with \$GP"),
                TC(
                    "checksum mismatch",
                    "\$GPRMC,145526,A,3349.0378,N,08406.2617,W,23.726,27.9,121207,4.9,W*FF",
                    ParseError.NMEA_INVALID,
                    "checksum mismatch",
                ),
                TC("too few fields", "\$GPXXX", ParseError.NMEA_SHORT, "too short"),
                TC("unsupported sentence", "\$GPVTG,0,T,0,M,0,N,0,K", ParseError.NMEA_INVALID, "unsupported NMEA sentence"),
            )
        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse("N0CALL>APRS,WIDE2-1:${tc.body}") }
            assertEquals(tc.errCode, ex.code, tc.name)
            assertTrue(ex.message!!.contains(tc.msgIs), "${tc.name}: message '${ex.message}' should contain '${tc.msgIs}'")
        }
    }

    @Test
    fun testParseGPRMCErrors() {
        data class TC(val name: String, val body: String, val errCode: String)
        val tests =
            listOf(
                TC("too few fields", "\$GPRMC,145526,A,3349.0378,N,08406.2617,W,23.726,27.9", ParseError.NMEA_SHORT),
                TC("no fix", "\$GPRMC,145526,V,3349.0378,N,08406.2617,W,23.726,27.9,121207,4.9,W", ParseError.GPRMC_NOFIX),
                TC("invalid latitude", "\$GPRMC,145526,A,XXXX.XXXX,N,08406.2617,W,23.726,27.9,121207,4.9,W", ParseError.POS_LAT_INV),
                TC("empty latitude", "\$GPRMC,145526,A,,N,08406.2617,W,23.726,27.9,121207,4.9,W", ParseError.POS_LAT_INV),
                TC("invalid longitude", "\$GPRMC,145526,A,3349.0378,N,XXXXX.XXXX,W,23.726,27.9,121207,4.9,W", ParseError.POS_LON_INV),
                TC("empty longitude", "\$GPRMC,145526,A,3349.0378,N,,W,23.726,27.9,121207,4.9,W", ParseError.POS_LON_INV),
            )
        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse("N0CALL>APRS,WIDE2-1:${tc.body}") }
            assertEquals(tc.errCode, ex.code, tc.name)
        }
    }

    @Test
    fun testParseGPGGAErrors() {
        data class TC(val name: String, val body: String, val errCode: String)
        val tests =
            listOf(
                TC("too few fields", "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4", ParseError.NMEA_SHORT),
                TC("no fix", "\$GPGGA,123519,4807.038,N,01131.000,E,0,08,0.9,545.4,M,47.0,M,,", ParseError.NMEA_INVALID),
                TC("invalid latitude", "\$GPGGA,123519,XXXX.XXX,N,01131.000,E,1,08,0.9,545.4,M,47.0,M,,", ParseError.POS_LAT_INV),
                TC("empty latitude", "\$GPGGA,123519,,N,01131.000,E,1,08,0.9,545.4,M,47.0,M,,", ParseError.POS_LAT_INV),
                TC("invalid longitude", "\$GPGGA,123519,4807.038,N,XXXXX.XXX,E,1,08,0.9,545.4,M,47.0,M,,", ParseError.POS_LON_INV),
                TC("empty longitude", "\$GPGGA,123519,4807.038,N,,E,1,08,0.9,545.4,M,47.0,M,,", ParseError.POS_LON_INV),
            )
        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse("N0CALL>APRS,WIDE2-1:${tc.body}") }
            assertEquals(tc.errCode, ex.code, tc.name)
        }
    }

    @Test
    fun testParseGPGLLErrors() {
        data class TC(val name: String, val body: String, val errCode: String)
        val tests =
            listOf(
                TC("too few fields", "\$GPGLL,4916.45,N,12311.12", ParseError.NMEA_SHORT),
                TC("invalid latitude", "\$GPGLL,XXXX.XX,N,12311.12,W", ParseError.POS_LAT_INV),
                TC("empty latitude", "\$GPGLL,,N,12311.12,W", ParseError.POS_LAT_INV),
                TC("invalid longitude", "\$GPGLL,4916.45,N,XXXXX.XX,W", ParseError.POS_LON_INV),
                TC("empty longitude", "\$GPGLL,4916.45,N,,W", ParseError.POS_LON_INV),
            )
        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse("N0CALL>APRS,WIDE2-1:${tc.body}") }
            assertEquals(tc.errCode, ex.code, tc.name)
        }
    }

    @Test
    fun testParseGPRMCTimestampErrors() {
        fun mkPacket(
            timeStr: String,
            dateStr: String,
        ) = "N0CALL>APRS,WIDE2-1:\$GPRMC,$timeStr,A,3349.0378,N,08406.2617,W,0,0,$dateStr,0,W"

        data class TC(val name: String, val time: String, val date: String, val msgIs: String)
        val tests =
            listOf(
                TC("time too short", "1234", "121207", "invalid time"),
                TC("time too long", "12345678", "121207", "invalid time"),
                TC("time empty", "", "121207", "invalid time"),
                TC("hour non-numeric", "XX0000", "121207", "invalid time"),
                TC("hour 24", "240000", "121207", "invalid time"),
                TC("minute non-numeric", "12XX00", "121207", "invalid time"),
                TC("minute 60", "126000", "121207", "invalid time"),
                TC("second non-numeric", "1200XX", "121207", "invalid time"),
                TC("second 60", "120060", "121207", "invalid time"),
                TC("date too short", "145526", "1212", "invalid date"),
                TC("date too long", "145526", "12120712", "invalid date"),
                TC("date empty", "145526", "", "invalid date"),
                TC("day non-numeric", "145526", "XX1207", "invalid date"),
                TC("month non-numeric", "145526", "12XX07", "invalid date"),
                TC("year non-numeric", "145526", "1212XX", "invalid date"),
            )
        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse(mkPacket(tc.time, tc.date)) }
            assertEquals(ParseError.NMEA_INVALID, ex.code, tc.name)
            assertTrue(ex.message!!.contains(tc.msgIs), "${tc.name}: '${ex.message}' should contain '${tc.msgIs}'")
        }
    }
}
