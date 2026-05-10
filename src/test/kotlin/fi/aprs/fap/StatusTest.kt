package fi.aprs.fap

import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.*

// Tests ported from go-aprs-fap status_test.go

class StatusTest {
    @Test
    fun testParseCapabilities() {
        val p = parse("OH7LZB>APRS:<IGATE,MSG_CNT=5,LOC_CNT=10")
        assertEquals(PacketType.CAPABILITIES, p.type)
        val caps = assertNotNull(p.capabilities)
        assertEquals("5", caps["MSG_CNT"])
        assertTrue(caps.containsKey("IGATE"), "IGATE capability not found")
    }

    @Test
    fun testStatusWithTimestamp() {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val tstamp = "%02d%02d%02dz".format(now.dayOfMonth, now.hour, now.minute)
        val outcome = ZonedDateTime.of(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, 0, 0, ZoneOffset.UTC).toInstant()

        val msg = ">>Nashville,TN>>Toronto,ON"
        val p = parse("KB3HVP-14>APU25N,WIDE2-2,qAR,LANSNG:>$tstamp$msg")

        assertEquals(PacketType.STATUS, p.type)
        assertNotNull(p.timestamp)
        assertEquals(outcome, p.timestamp)
        assertEquals(msg, p.status)
    }
}
