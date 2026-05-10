package fi.aprs.fap

import kotlin.test.*

// Tests ported from perl-aprs-fap/t/54decode-tlm-mice.t

class TelemetryMicETest {

    @Test
    fun testMicEBase91Telemetry5Ch() {
        // Sequence 00, 5 channels of telemetry and one channel of binary bits
        val packet = "OH7LZB-13>SX15S6,TCPIP*,qAC,FOURTH:'I',l >/ comment |!!!!!!!!!!!!!!|"
        val p = parse(packet)

        assertEquals("comment", p.comment)

        val tlm = assertNotNull(p.telemetryData)
        assertEquals(0, tlm.seq)
        assertTrue(tlm.vals.size >= 5)
        for (i in 0 until 5) {
            assertEquals(0.0, tlm.vals[i], "vals[$i]")
        }
        assertEquals("00000000", tlm.bits)
    }

    @Test
    fun testMicEBase91Telemetry1Ch() {
        // Sequence 00, 1 channel of telemetry
        val packet = "OH7LZB-13>SX15S6,TCPIP*,qAC,FOURTH:'I',l >/ comment |!!!!|"
        val p = parse(packet)

        assertEquals("comment", p.comment)

        val tlm = assertNotNull(p.telemetryData)
        assertEquals(0, tlm.seq)
        assertTrue(tlm.vals.size >= 5)
        assertEquals(0.0, tlm.vals[0])
        for (i in 1..4) {
            assertNull(tlm.vals[i], "vals[$i] should be null")
        }
    }

    @Test
    fun testMicEBase91TelemetryHarder() {
        val packet = "N6BG-1>S6QTUX:`+,^l!cR/'\";z}||ss11223344bb!\"|!w>f!|3"
        val p = parse(packet)
        val tlm = assertNotNull(p.telemetryData)
        assertEquals("10000000", tlm.bits)
    }

    @Test
    fun testMicEBase91TelemetryDAOConfusing() {
        // Telemetry that looks like it could be a DAO extension
        val packet = "OH7LZB-13>SX15S6,TCPIP*,qAC,FOURTH:'I',l >/ comment |!wEU!![S|"
        val p = parse(packet)

        assertEquals("comment", p.comment)
        assertNotNull(p.telemetryData, "telemetry should be parsed, not confused with DAO")
    }
}
