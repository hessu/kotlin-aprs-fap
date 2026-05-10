package fi.aprs.fap

import kotlin.test.*

// Tests ported from perl-aprs-fap/t/90check_ax25_call.t

class Ax25CallTest {
    @Test
    fun testCheckAX25Call() {
        data class TC(val name: String, val input: String, val want: String?)

        val tests =
            listOf(
                TC("no SSID", "N0CAL", "N0CAL"),
                TC("SSID 9", "N0CAL-9", "N0CAL-9"),
                TC("SSID 15", "N0CAL-15", "N0CAL-15"),
                TC("SSID characters invalid", "N0CAL-IS", null),
                TC("SSID dash without SSID", "N0CAL-", null),
                TC("space in front invalid", " N0CAL", null),
                TC("space in back invalid", "N0CAL ", null),
                TC("two dashes SSID invalid", "N0CAL--1", null),
                TC("post-dash SSID invalid", "N0CA-1-", null),
                TC("only SSID invalid", "-1", null),
                TC("underscore SSID invalid", "N0CAL_1", null),
                TC("SSID 16 invalid", "N0CAL-16", null),
                TC("SSID 166 invalid", "N0CAL-166", null),
                TC("too long invalid", "N0CALXXXX", null),
                TC("characters invalid", "N0CÄL-1", null),
            )

        for (tc in tests) {
            val got = checkAX25Call(tc.input)
            assertEquals(tc.want, got, tc.name)
        }
    }
}
