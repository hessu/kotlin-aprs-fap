package fi.aprs.fap

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Parses a 7-character APRS timestamp. Formats:
 *   - `DDHHMMz` — day/hours/minutes UTC
 *   - `DDHHMM/` — day/hours/minutes local time
 *   - `HHMMSSh` — hours/minutes/seconds UTC
 *
 * Returns the parsed [Instant], or null on parse failure.
 */
internal fun parseTimestamp(s: String): Instant? {
    if (s.length != 7) return null

    return when (s[6]) {
        'z' -> {
            val dd = s.substring(0, 2).toIntOrNull()?.takeIf { it in 1..31 } ?: return null
            val hh = s.substring(2, 4).toIntOrNull()?.takeIf { it <= 23 } ?: return null
            val mm = s.substring(4, 6).toIntOrNull()?.takeIf { it <= 59 } ?: return null
            val now = ZonedDateTime.now(ZoneId.of("UTC"))
            var t = ZonedDateTime.of(now.year, now.monthValue, dd, hh, mm, 0, 0, ZoneId.of("UTC"))
            if (t.toInstant().isAfter(now.toInstant())) {
                t = t.minusMonths(1)
            }
            t.toInstant()
        }
        '/' -> {
            val dd = s.substring(0, 2).toIntOrNull()?.takeIf { it in 1..31 } ?: return null
            val hh = s.substring(2, 4).toIntOrNull()?.takeIf { it <= 23 } ?: return null
            val mm = s.substring(4, 6).toIntOrNull()?.takeIf { it <= 59 } ?: return null
            val now = ZonedDateTime.now()
            var t = ZonedDateTime.of(now.year, now.monthValue, dd, hh, mm, 0, 0, now.zone)
            if (t.toInstant().isAfter(now.toInstant())) {
                t = t.minusMonths(1)
            }
            t.toInstant()
        }
        'h' -> {
            val hh = s.substring(0, 2).toIntOrNull()?.takeIf { it <= 23 } ?: return null
            val mm = s.substring(2, 4).toIntOrNull()?.takeIf { it <= 59 } ?: return null
            val ss = s.substring(4, 6).toIntOrNull()?.takeIf { it <= 59 } ?: return null
            val now = ZonedDateTime.now(ZoneId.of("UTC"))
            ZonedDateTime.of(now.year, now.monthValue, now.dayOfMonth, hh, mm, ss, 0, ZoneId.of("UTC"))
                .toInstant()
        }
        else -> null
    }
}
