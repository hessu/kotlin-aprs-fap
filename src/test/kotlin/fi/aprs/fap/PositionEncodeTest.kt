package fi.aprs.fap

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.*

// Tests ported from go-aprs-fap position_encode_test.go

class PositionEncodeTest {
    private fun ts(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        min: Int,
        sec: Int,
    ): Instant = ZonedDateTime.of(year, month, day, hour, min, sec, 0, ZoneOffset.UTC).toInstant()

    @Test
    fun testEncodePosition() {
        data class TC(
            val name: String,
            val lat: Double,
            val lon: Double,
            val speed: Double?,
            val course: Double?,
            val altitude: Double?,
            val symbol: String,
            val opts: EncodePositionOpts?,
            val want: String,
        )

        val tests =
            listOf(
                TC("basic NE, no speed/course/alt", 63.06716666666667, 27.6605, null, null, null, "/#", null, "!6304.03N/02739.63E#"),
                TC("basic SW, no speed/course/alt", -23.64266666666667, -46.797, null, null, null, "/#", null, "!2338.56S/04647.82W#"),
                TC("minute rounding, no speed/course/alt", 22.9999999, -177.9999999, null, null, null, "/#", null, "!2259.99N/17759.99W#"),
                TC("NE, has speed/course/alt", 52.364, 14.1045, 83.34, 353.0, 95.7072, "/>", null, "!5221.84N/01406.27E>353/045/A=000314"),
                TC("NE, no speed/course, has alt", 52.364, 14.1045, null, null, 95.7072, "/>", null, "!5221.84N/01406.27E>/A=000314"),
                TC("NE, ambiguity 1", 52.364, 14.1045, null, null, null, "/>", EncodePositionOpts(ambiguity = 1), "!5221.8 N/01406.2 E>"),
                TC("NE, ambiguity 2", 52.364, 14.1045, null, null, null, "/>", EncodePositionOpts(ambiguity = 2), "!5221.  N/01406.  E>"),
                TC("NE, ambiguity 3", 52.364, 14.1045, null, null, null, "/>", EncodePositionOpts(ambiguity = 3), "!522 .  N/0140 .  E>"),
                TC("NE, ambiguity 4", 52.364, 14.1045, null, null, null, "/>", EncodePositionOpts(ambiguity = 4), "!52  .  N/014  .  E>"),
                TC(
                    "DAO position, US", 39.15380036630037, -84.62208058608059, null, null, null, "/>",
                    EncodePositionOpts(
                        dao = true,
                    ),
                    "!3909.22N/08437.32W>!wjM!",
                ),
                TC(
                    "DAO rounding", 39.9999999, -84.9999999, null, null, null, "/>",
                    EncodePositionOpts(
                        dao = true,
                    ),
                    "!3959.99N/08459.99W>!w{{!",
                ),
                TC(
                    "DAO with speed/course/alt/comment",
                    48.37314835164835, 15.71477838827839, 62.968, 321.0, 192.9384, "/>",
                    EncodePositionOpts(dao = true, comment = "Comment blah"),
                    "!4822.38N/01542.88E>321/034/A=000633Comment blah!wr^!",
                ),
                TC(
                    "with timestamp",
                    63.06716666666667, 27.6605, null, null, null, "/#",
                    EncodePositionOpts(timestamp = ts(2024, 3, 15, 12, 30, 45)),
                    "/123045h6304.03N/02739.63E#",
                ),
                TC(
                    "with timestamp and speed/course/alt",
                    52.364, 14.1045, 83.34, 353.0, 95.7072, "/>",
                    EncodePositionOpts(timestamp = ts(2024, 1, 1, 0, 0, 0)),
                    "/000000h5221.84N/01406.27E>353/045/A=000314",
                ),
                TC(
                    "messaging capable, no timestamp",
                    63.06716666666667, 27.6605, null, null, null, "/#",
                    EncodePositionOpts(messagingCapable = true),
                    "=6304.03N/02739.63E#",
                ),
                TC(
                    "messaging capable, with timestamp",
                    63.06716666666667, 27.6605, null, null, null, "/#",
                    EncodePositionOpts(messagingCapable = true, timestamp = ts(2024, 3, 15, 12, 30, 45)),
                    "@123045h6304.03N/02739.63E#",
                ),
            )

        for (tc in tests) {
            val got = encodePosition(tc.lat, tc.lon, tc.speed, tc.course, tc.altitude, tc.symbol, tc.opts)
            assertEquals(tc.want, got, tc.name)
        }
    }

    @Test
    fun testEncodePositionErrors() {
        data class TC(val name: String, val lat: Double, val lon: Double, val symbol: String)

        // Go test has "/\x1f" (2 chars: '/' + 0x1f) for "invalid symbol code"
        val invalidCodeSym = "/" + 0x1f.toChar()

        val tests =
            listOf(
                TC("lat too high", 91.0, 0.0, "/#"),
                TC("lat too low", -91.0, 0.0, "/#"),
                TC("lon too high", 0.0, 181.0, "/#"),
                TC("lon too low", 0.0, -181.0, "/#"),
                TC("invalid symbol table", 0.0, 0.0, "a#"),
                TC("invalid symbol code", 0.0, 0.0, invalidCodeSym),
                TC("symbol too short", 0.0, 0.0, "/"),
                TC("symbol too long", 0.0, 0.0, "//#"),
            )
        for (tc in tests) {
            val ex =
                assertFailsWith<ParseException>(tc.name) {
                    encodePosition(tc.lat, tc.lon, null, null, null, tc.symbol)
                }
            assertEquals(ParseError.POS_ENC_INV, ex.code, tc.name)
        }
    }
}
