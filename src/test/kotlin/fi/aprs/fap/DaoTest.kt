package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap dao_test.go

class DaoTest {

    @Test
    fun testDAOUncompressedHumanReadable() {
        val p = parse("K0ELR-15>APOT02,WIDE1-1,WIDE2-1,qAo,K0ELR:/102033h4133.03NX09029.49Wv204/000!W33! 12.3V 21C/A=000665")

        assertEquals('W', p.daoDatumByte)
        assertEquals("12.3V 21C", p.comment)
        assertEquals(41.55055, p.latitude!!, 0.00001)
        assertEquals(-90.49155, p.longitude!!, 0.00001)
        assertEquals(203.0, p.altitude!!, 1.0)
        assertEquals(1.852, p.posResolution!!, 0.001)
    }

    @Test
    fun testDAOCompressedBase91() {
        val p = parse("OH7LZB-9>APZMDR,WIDE2-2,qAo,OH2RCH:!/0(yiTc5y>{2O http://aprs.fi/!w11!")

        assertEquals('W', p.daoDatumByte)
        assertEquals("http://aprs.fi/", p.comment)
        assertEquals(60.15273, p.latitude!!, 0.00001)
        assertEquals(24.66222, p.longitude!!, 0.00001)
        assertEquals(0.1852, p.posResolution!!, 0.0001)
    }

    @Test
    fun testDAOMicEBase91() {
        val p = parse("OH2JCQ-9>VP1U88,TRACE2-2,qAR,OH2RDK-5:'5'9\"^Rj/]\"4-}Foo !w66!Bar")

        assertEquals('W', p.daoDatumByte)
        assertEquals("]Foo Bar", p.comment)
        assertEquals(60.26471, p.latitude!!, 0.00001)
        assertEquals(25.18821, p.longitude!!, 0.00001)
        assertEquals(0.1852, p.posResolution!!, 0.0001)
    }
}
