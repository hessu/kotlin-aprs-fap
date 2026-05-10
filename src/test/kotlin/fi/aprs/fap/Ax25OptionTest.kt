package fi.aprs.fap

import kotlin.test.*

class Ax25OptionTest {

    @Test
    fun testWithAX25() {
        data class TC(
            val name: String,
            val packet: String,
            val wantErr: Boolean = false,
            val wantCode: String? = null,
            val wantSrc: String? = null,
            val wantDst: String? = null,
            val wantDigis: List<String>? = null,
        )

        val ax25 = ParseOptions(isAX25 = true)

        val tests = listOf(
            TC(
                name = "valid AX.25 packet",
                packet = "OH7LZB-9>APX200,OH7AA-1*,WIDE2-1:!6028.51N/02505.68E#PHG7220/RELAY,WIDE, OH7LZB testing",
                wantSrc = "OH7LZB-9",
                wantDst = "APX200",
                wantDigis = listOf("OH7AA-1", "WIDE2-1"),
            ),
            TC(
                name = "valid AX.25 no digipeaters",
                packet = "OH7LZB>APRS:>status",
                wantSrc = "OH7LZB",
                wantDst = "APRS",
                wantDigis = emptyList(),
            ),
            TC(
                name = "invalid AX.25 source callsign",
                packet = "TOOLONGCALLSIGN>APRS:>status",
                wantErr = true,
                wantCode = ParseError.SRCCALL_NOAX25,
            ),
            TC(
                name = "source SSID too large for AX.25",
                packet = "OH7LZB-16>APRS:>status",
                wantErr = true,
                wantCode = ParseError.SRCCALL_NOAX25,
            ),
            TC(
                name = "invalid AX.25 digipeater callsign",
                packet = "OH7LZB>APRS,TOOLONGCALLSIGN:>status",
                wantErr = true,
                wantCode = ParseError.DIGICALL_NOAX25,
            ),
            TC(
                name = "too many path components for AX.25",
                packet = "OH7LZB>APRS,D1,D2,D3,D4,D5,D6,D7,D8,D9:>status",
                wantErr = true,
                wantCode = ParseError.DSTPATH_TOOMANY,
            ),
            TC(
                name = "max 8 digipeaters is valid",
                packet = "OH7LZB>APRS,D1,D2,D3,D4,D5,D6,D7,D8:>status",
                wantSrc = "OH7LZB",
                wantDst = "APRS",
                wantDigis = listOf("D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8"),
            ),
            TC(
                name = "source callsign normalized to uppercase",
                packet = "oh7lzb>APRS:>status",
                wantSrc = "OH7LZB",
                wantDst = "APRS",
                wantDigis = emptyList(),
            ),
            TC(
                name = "invalid AX.25 destination callsign",
                packet = "OH7LZB>TOOLONGDST:>status",
                wantErr = true,
                wantCode = ParseError.DSTCALL_NOAX25,
            ),
            TC(
                name = "destination SSID too large for AX.25",
                packet = "OH7LZB>APRS-16:>status",
                wantErr = true,
                wantCode = ParseError.DSTCALL_NOAX25,
            ),
            TC(
                name = "destination callsign normalized to uppercase",
                packet = "OH7LZB>aprs:>status",
                wantSrc = "OH7LZB",
                wantDst = "APRS",
                wantDigis = emptyList(),
            ),
        )

        for (tc in tests) {
            if (tc.wantErr) {
                val ex = assertFailsWith<ParseException>(tc.name) { parse(tc.packet, ax25) }
                assertEquals(tc.wantCode, ex.code, tc.name)
            } else {
                val p = parse(tc.packet, ax25) // throws on unexpected error, failing the test
                if (tc.wantSrc != null) assertEquals(tc.wantSrc, p.srcCallsign, tc.name)
                if (tc.wantDst != null) assertEquals(tc.wantDst, p.dstCallsign, tc.name)
                if (tc.wantDigis != null) {
                    assertEquals(tc.wantDigis.size, p.digipeaters.size, "${tc.name}: digipeater count")
                    for ((i, want) in tc.wantDigis.withIndex()) {
                        assertEquals(want, p.digipeaters[i].call, "${tc.name}: digi[$i]")
                    }
                }
            }
        }
    }

    @Test
    fun testDstCallNoAX25WithoutOption() {
        val ex = assertFailsWith<ParseException> { parse("OH7LZB>TOOLONGDST:>status") }
        assertEquals(ParseError.DSTCALL_NOAX25, ex.code)
    }
}
