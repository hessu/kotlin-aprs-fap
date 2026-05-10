package fi.aprs.fap

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.*

// Tests ported from go-aprs-fap timestamp_test.go

class TimestampTest {

    @Test
    fun testTimestampRawDHMzUTC() {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val tstamp = "%02d%02d%02d".format(now.dayOfMonth, now.hour, now.minute)

        val p = parse(
            "KB3HVP-14>APU25N,N8TJG-10*,WIDE2-1,qAR,LANSNG:@${tstamp}z4231.16N/08449.88Wu227/052/A=000941 {UIV32N}",
            ParseOptions(rawTimestamp = true),
        )
        assertEquals(tstamp, p.rawTimestamp)
    }

    @Test
    fun testTimestampDecodedDHMzUTC() {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val tstamp = "%02d%02d%02d".format(now.dayOfMonth, now.hour, now.minute)
        val outcome = ZonedDateTime.of(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, 0, 0, ZoneOffset.UTC).toInstant()

        val p = parse("KB3HVP-14>APU25N,N8TJG-10*,WIDE2-1,qAR,LANSNG:@${tstamp}z4231.16N/08449.88Wu227/052/A=000941 {UIV32N}")
        assertEquals(outcome, p.timestamp)
    }

    @Test
    fun testTimestampRawHMSh() {
        val p = parse(
            "G4EUM-9>APOTC1,G4EUM*,WIDE2-2,qAS,M3SXA-10:/055816h5134.38N/00019.47W>155/023!W26!/A=000188 14.3V 27C HDOP01.0 SATS09",
            ParseOptions(rawTimestamp = true),
        )
        assertEquals("055816", p.rawTimestamp)
    }

    @Test
    fun testTimestampDecodedHMSh() {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val tstamp = "%02d%02d%02d".format(now.hour, now.minute, now.second)
        val outcome = ZonedDateTime.of(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second, 0, ZoneOffset.UTC).toInstant()

        val p = parse("G4EUM-9>APOTC1,G4EUM*,WIDE2-2,qAS,M3SXA-10:/${tstamp}h5134.38N/00019.47W>155/023!W26!/A=000188 14.3V 27C HDOP01.0 SATS09")
        assertEquals(outcome, p.timestamp)
    }

    @Test
    fun testTimestampRawDHMLocal() {
        val p = parse(
            "G4EUM-9>APOTC1,G4EUM*,WIDE2-2,qAS,M3SXA-10:/060642/5134.38N/00019.47W>155/023!W26!/A=000188 14.3V 27C HDOP01.0 SATS09",
            ParseOptions(rawTimestamp = true),
        )
        assertEquals("060642", p.rawTimestamp)
    }

    @Test
    fun testTimestampDecodedDHMLocal() {
        val now = ZonedDateTime.now()
        val tstamp = "%02d%02d%02d".format(now.dayOfMonth, now.hour, now.minute)
        val outcome = ZonedDateTime.of(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, 0, 0, now.zone).toInstant()

        val p = parse("G4EUM-9>APOTC1,G4EUM*,WIDE2-2,qAS,M3SXA-10:/${tstamp}/5134.38N/00019.47W>155/023!W26!/A=000188 14.3V 27C HDOP01.0 SATS09")
        assertEquals(outcome, p.timestamp)
    }

    @Test
    fun testParseTimestampErrors() {
        data class TC(val name: String, val input: String)
        val tests = listOf(
            TC("too short", "12345z"),
            TC("too long", "12345678"),
            TC("empty", ""),
            TC("unknown indicator", "010000x"),
            TC("z: day zero", "000000z"),
            TC("z: day 32", "320000z"),
            TC("z: day non-numeric", "ab0000z"),
            TC("z: hours 24", "012400z"),
            TC("z: hours non-numeric", "01xx00z"),
            TC("z: minutes 60", "010060z"),
            TC("z: minutes non-numeric", "0100xxz"),
            TC("/: day zero", "000000/"),
            TC("/: day 32", "320000/"),
            TC("/: day non-numeric", "ab0000/"),
            TC("/: hours 24", "012400/"),
            TC("/: hours non-numeric", "01xx00/"),
            TC("/: minutes 60", "010060/"),
            TC("/: minutes non-numeric", "0100xx/"),
            TC("h: hours 24", "240000h"),
            TC("h: hours non-numeric", "xx0000h"),
            TC("h: minutes 60", "006000h"),
            TC("h: minutes non-numeric", "00xx00h"),
            TC("h: seconds 60", "000060h"),
            TC("h: seconds non-numeric", "0000xxh"),
        )
        for (tc in tests) {
            assertNull(parseTimestamp(tc.input), tc.name)
        }
    }

    @Test
    fun testTimestampFutureRollback() {
        val now = ZonedDateTime.now()
        var futureDay = now.dayOfMonth + 1
        if (futureDay > 28) futureDay = 1
        val tstamp = "%02d2359".format(futureDay)

        data class TC(val name: String, val packet: String, val zone: ZoneId)
        val tests = listOf(
            TC(
                "DDHHMMz UTC",
                "KB3HVP-14>APU25N,N8TJG-10*,WIDE2-1,qAR,LANSNG:@${tstamp}z4231.16N/08449.88Wu227/052/A=000941 {UIV32N}",
                ZoneOffset.UTC,
            ),
            TC(
                "DDHHMM/ local",
                "G4EUM-9>APOTC1,G4EUM*,WIDE2-2,qAS,M3SXA-10:/${tstamp}/5134.38N/00019.47W>155/023!W26!/A=000188 14.3V 27C HDOP01.0 SATS09",
                now.zone,
            ),
        )

        for (tc in tests) {
            val futureTime = ZonedDateTime.of(now.year, now.monthValue, futureDay, 23, 59, 0, 0, tc.zone)
            if (!futureTime.toInstant().isAfter(ZonedDateTime.now(tc.zone).toInstant())) continue // skip if can't guarantee future

            val outcome = futureTime.minusMonths(1).toInstant()
            val p = parse(tc.packet)
            assertNotNull(p.timestamp, tc.name)
            assertEquals(outcome, p.timestamp, tc.name)
            val tsInZone = p.timestamp!!.atZone(tc.zone)
            assertNotEquals(now.monthValue, tsInZone.monthValue, "${tc.name}: expected previous month rollback")
        }
    }

    @Test
    fun testTimestampInvalidLocation() {
        val p = parse("KB3HVP-14>APU25N,N8TJG-10*,WIDE2-1,qAR,LANSNG:@000000z4231.16N/08449.88Wu227/052/A=000941 {UIV32N}")
        assertNull(p.timestamp)
        assertEquals(1, p.warnings.size)
        assertEquals(ParseError.TIMESTAMP_INV, p.warnings[0].code)
        assertNotNull(p.latitude)
    }

    @Test
    fun testTimestampInvalidObject() {
        val p = parse("SRC>APRS,TCPIP*:;TestObj  *000000z4903.50N/07201.75W-Test")
        assertNull(p.timestamp)
        assertEquals(1, p.warnings.size)
        assertEquals(ParseError.TIMESTAMP_INV, p.warnings[0].code)
        assertNotNull(p.latitude)
    }

    @Test
    fun testTimestampInvalidStatus() {
        val p = parse("SRC>APRS,TCPIP*:>000000zStatus text here")
        assertNull(p.timestamp)
        assertEquals(1, p.warnings.size)
        assertEquals(ParseError.TIMESTAMP_INV, p.warnings[0].code)
        assertEquals("Status text here", p.status)
    }
}
