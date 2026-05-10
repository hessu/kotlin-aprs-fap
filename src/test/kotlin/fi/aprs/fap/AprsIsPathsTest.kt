package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap aprsis_paths_test.go

class AprsIsPathsTest {
    @Test
    fun testAPRSISPathIPv6AfterQConstruct() {
        val input = "IQ3VQ>APD225,TCPIP*,qAI,IQ3VQ,THIRD,92E5A2B6,T2HUB1,200106F8020204020000000000000002,T2FINLAND:!4526.66NI01104.68E#PHG21306/- Lnx APRS Srv - sez. ARI VR EST"
        val p = parse(input)
        assertEquals(8, p.digipeaters.size)
        assertEquals("200106F8020204020000000000000002", p.digipeaters[6].call)
    }

    @Test
    fun testAPRSISPathIPv6BeforeQConstruct() {
        assertFailsWith<ParseException> {
            parse(
                "IQ3VQ>APD225,200106F8020204020000000000000002,TCPIP*,qAI,IQ3VQ,THIRD,92E5A2B6,T2HUB1,200106F8020204020000000000000002,T2FINLAND:!4526.66NI01104.68E#PHG21306/- Lnx APRS Srv - sez. ARI VR EST",
            )
        }
    }
}
