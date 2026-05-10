package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap telemetry_test.go

class TelemetryTest {
    @Test
    fun testTelemetryClassic() {
        val p = parse("SRCCALL>APRS:T#324,000,038,255,.12,50.12,01000001")
        val tlm = assertNotNull(p.telemetryData)
        assertEquals(324, tlm.seq)
        assertEquals("01000001", tlm.bits)
        assertTrue(tlm.vals.size >= 5)
        val expected = listOf(0.0, 38.0, 255.0, 0.12, 50.12)
        for ((i, want) in expected.withIndex()) {
            assertEquals(want, tlm.vals[i]!!, 0.0001, "vals[$i]")
        }
    }

    @Test
    fun testTelemetryRelaxed() {
        val p = parse("SRCCALL>APRS:T#1,-1,2147483647,-2147483648,0.000001,-0.0000001,01000001 comment")
        val tlm = assertNotNull(p.telemetryData)
        assertEquals(1, tlm.seq)
        assertEquals("01000001", tlm.bits)
        val expected = listOf(-1.0, 2147483647.0, -2147483648.0, 0.000001, -0.0000001)
        for ((i, want) in expected.withIndex()) {
            assertEquals(want, tlm.vals[i]!!, 1e-10, "vals[$i]")
        }
    }

    @Test
    fun testTelemetryShort() {
        val p = parse("SRCCALL>APRS:T#001,42")
        val tlm = assertNotNull(p.telemetryData)
        assertEquals(1, tlm.seq)
        assertEquals("", tlm.bits)
        assertTrue(tlm.vals.size >= 5)
        assertEquals(42.0, tlm.vals[0]!!, 0.001)
        for (i in 1..4) assertNull(tlm.vals[i], "vals[$i] should be null")
    }

    @Test
    fun testTelemetryUndefinedMiddle() {
        val p = parse("SRCCALL>APRS:T#1,1,,3,,5")
        val tlm = assertNotNull(p.telemetryData)
        assertEquals(1.0, tlm.vals[0]!!, 0.001)
        assertNull(tlm.vals[1])
        assertEquals(3.0, tlm.vals[2]!!, 0.001)
        assertNull(tlm.vals[3])
        assertEquals(5.0, tlm.vals[4]!!, 0.001)
    }

    @Test
    fun testTelemetryPartiallyCorrect() {
        val p = parse("SRCCALL>APRS:T#1,1,f,3")
        val tlm = assertNotNull(p.telemetryData)
        assertEquals(1.0, tlm.vals[0]!!, 0.001)
        for (i in 1..4) assertNull(tlm.vals[i], "vals[$i] should be null")
    }

    @Test
    fun testIsNumericValue() {
        data class TC(val input: String, val want: Boolean)
        val tests =
            listOf(
                TC("0", true), TC("1", true), TC("42", true), TC("123456789", true),
                TC("-1", true), TC("-42", true),
                TC("0.5", true), TC("3.14", true), TC(".5", true), TC(".123", true), TC("0.000001", true),
                TC("-0.5", true), TC("-.5", true), TC("-0.0000001", true),
                TC("123.456", true), TC("2147483647", true),
                TC("", false), TC("-", false),
                TC("1.", false), TC("-1.", false), TC("0.", false),
                TC(".", false), TC("-.", false),
                TC("abc", false), TC("1a", false), TC("a1", false), TC("-a", false),
                TC(" 1", false), TC("1 ", false), TC(" ", false),
                TC("1.2.3", false), TC("--1", false), TC("1-2", false),
            )
        for (tc in tests) {
            assertEquals(tc.want, isNumericValue(tc.input), "isNumericValue(${tc.input})")
        }
    }

    @Test
    fun testTelemetryInvalidDash() {
        val ex = assertFailsWith<ParseException> { parse("SRCCALL>APRS:T#1,1,-,3") }
        assertEquals(ParseError.TLM_INV, ex.code)
    }

    @Test
    fun testTelemetryInvalidTrailingDot() {
        val ex = assertFailsWith<ParseException> { parse("SRCCALL>APRS:T#1,1,-1.,3") }
        assertEquals(ParseError.TLM_INV, ex.code)
    }

    @Test
    fun testTelemetryParameterMessage() {
        val p = parse("SRCCALL>APRS::N0CALL-10:PARM.Vin,Rx1h,Dg1h,Eff1h,EffTo,O1,O2,O3,O4,I1,I2,I3,I4")
        assertEquals(PacketType.TELEMETRY_MESSAGE, p.type)
        val msg = assertNotNull(p.message)
        assertEquals("N0CALL-10", msg.destination)
        assertEquals("PARM.Vin,Rx1h,Dg1h,Eff1h,EffTo,O1,O2,O3,O4,I1,I2,I3,I4", msg.text)
    }

    @Test
    fun testTelemetryParameterMessageTypes() {
        data class TC(val name: String, val body: String)
        val tests =
            listOf(
                TC("PARM", "::N0CALL   :PARM.Vin,Rx1h"),
                TC("UNIT", "::N0CALL   :UNIT.Volt,Pkt/h"),
                TC("EQNS", "::N0CALL   :EQNS.0,0.075,0,0,1,0"),
                TC("BITS", "::N0CALL   :BITS.11111111,My station"),
                TC("parm lowercase", "::N0CALL   :parm.Vin,Rx1h"),
            )
        for (tc in tests) {
            val p = parse("SRCCALL>APRS${tc.body}")
            assertEquals(PacketType.TELEMETRY_MESSAGE, p.type, tc.name)
        }
    }

    @Test
    fun testRegularMessageNotTelemetryMessage() {
        val p = parse("SRCCALL>APRS::N0CALL   :Hello world")
        assertEquals(PacketType.MESSAGE, p.type)
    }
}
