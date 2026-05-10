package fi.aprs.fap

import java.time.ZonedDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.pow

internal fun Packet.parseNMEA(options: ParseOptions) {
    type = PacketType.LOCATION
    format = Format.NMEA

    var body = bodyAsString().trimEnd(' ', '\t', '\r', '\n')

    if (!body.startsWith("\$GP")) {
        fail(ParseError.NMEA_INVALID, "NMEA sentence must start with \$GP")
    }

    val starIdx = body.indexOf('*')
    if (starIdx >= 0) {
        val checksumStr = body.substring(starIdx + 1)
        val checksumArea = body.substring(0, starIdx)
        if (checksumStr.length == 2) {
            val given = checksumStr.toLongOrNull(16)
            if (given != null) {
                var calculated = 0
                val start = if (checksumArea.isNotEmpty() && checksumArea[0] == '$') 1 else 0
                for (i in start until checksumArea.length) {
                    calculated = calculated xor checksumArea[i].code
                }
                calculated = calculated and 0xFF
                if (given.toInt() != calculated) {
                    fail(ParseError.NMEA_INVALID, "NMEA checksum mismatch")
                }
                checksumOK = true
            }
        }
        body = checksumArea
    }

    val parts = body.split(",")
    if (parts.size < 2) fail(ParseError.NMEA_SHORT, "NMEA sentence too short")

    when (parts[0]) {
        "\$GPRMC" -> parseGPRMC(parts)
        "\$GPGGA" -> parseGPGGA(parts)
        "\$GPGLL" -> parseGPGLL(parts)
        else -> fail(ParseError.NMEA_INVALID, "unsupported NMEA sentence: ${parts[0]}")
    }
}

private fun Packet.parseGPRMC(parts: List<String>) {
    if (parts.size < 10) fail(ParseError.NMEA_SHORT, "GPRMC sentence too short")

    if (parts[2] != "A") fail(ParseError.GPRMC_NOFIX, "GPRMC: no valid fix")

    parseGPRMCTimestamp(parts[1], parts[9])

    val latResult = runCatching { parseNMEACoordWithRes(parts[3], parts[4], false) }
        .getOrElse { fail(ParseError.POS_LAT_INV, "GPRMC: ${it.message}") }
    latitude = latResult.value

    val lonResult = runCatching { parseNMEACoordWithRes(parts[5], parts[6], true) }
        .getOrElse { fail(ParseError.POS_LON_INV, "GPRMC: ${it.message}") }
    longitude = lonResult.value

    posResolution = maxOf(latResult.resolution, lonResult.resolution)

    if (parts[7].isNotEmpty()) {
        parts[7].toDoubleOrNull()?.let { speed = it * 1.852 }
    }

    if (parts[8].isNotEmpty()) {
        parts[8].toDoubleOrNull()?.let { cf ->
            var c = (cf + 0.5).toInt()
            if (c == 0) c = 360 else if (c > 360) c = 0
            course = c
        }
    } else {
        course = 0
    }
}

private fun Packet.parseGPRMCTimestamp(timeStr: String, dateStr: String) {
    var t = timeStr.trim()
    val dotIdx = t.indexOf('.')
    if (dotIdx >= 0) t = t.substring(0, dotIdx)
    if (t.length != 6) fail(ParseError.NMEA_INVALID, "GPRMC: invalid time")

    val hour = t.substring(0, 2).toIntOrNull() ?: fail(ParseError.NMEA_INVALID, "GPRMC: invalid time")
    if (hour > 23) fail(ParseError.NMEA_INVALID, "GPRMC: invalid time")
    val minute = t.substring(2, 4).toIntOrNull() ?: fail(ParseError.NMEA_INVALID, "GPRMC: invalid time")
    if (minute > 59) fail(ParseError.NMEA_INVALID, "GPRMC: invalid time")
    val second = t.substring(4, 6).toIntOrNull() ?: fail(ParseError.NMEA_INVALID, "GPRMC: invalid time")
    if (second > 59) fail(ParseError.NMEA_INVALID, "GPRMC: invalid time")

    val d = dateStr.trim()
    if (d.length != 6) fail(ParseError.NMEA_INVALID, "GPRMC: invalid date")

    val day = d.substring(0, 2).toIntOrNull() ?: fail(ParseError.NMEA_INVALID, "GPRMC: invalid date")
    val month = d.substring(2, 4).toIntOrNull() ?: fail(ParseError.NMEA_INVALID, "GPRMC: invalid date")
    val yy = d.substring(4, 6).toIntOrNull() ?: fail(ParseError.NMEA_INVALID, "GPRMC: invalid date")
    val year = if (yy >= 70) 1900 + yy else 2000 + yy

    timestamp = ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC).toInstant()
}

private fun Packet.parseGPGGA(parts: List<String>) {
    if (parts.size < 11) fail(ParseError.NMEA_SHORT, "GPGGA sentence too short")

    if (parts[6] == "0") fail(ParseError.NMEA_INVALID, "GPGGA: no valid fix")

    val latResult = runCatching { parseNMEACoordWithRes(parts[2], parts[3], false) }
        .getOrElse { fail(ParseError.POS_LAT_INV, "GPGGA: ${it.message}") }
    latitude = latResult.value

    val lonResult = runCatching { parseNMEACoordWithRes(parts[4], parts[5], true) }
        .getOrElse { fail(ParseError.POS_LON_INV, "GPGGA: ${it.message}") }
    longitude = lonResult.value

    posResolution = maxOf(latResult.resolution, lonResult.resolution)

    if (parts[9].isNotEmpty()) {
        parts[9].toDoubleOrNull()?.let { altitude = it }
    }
}

private fun Packet.parseGPGLL(parts: List<String>) {
    if (parts.size < 5) fail(ParseError.NMEA_SHORT, "GPGLL sentence too short")

    val latResult = runCatching { parseNMEACoordWithRes(parts[1], parts[2], false) }
        .getOrElse { fail(ParseError.POS_LAT_INV, "GPGLL: ${it.message}") }
    latitude = latResult.value

    val lonResult = runCatching { parseNMEACoordWithRes(parts[3], parts[4], true) }
        .getOrElse { fail(ParseError.POS_LON_INV, "GPGLL: ${it.message}") }
    longitude = lonResult.value

    posResolution = maxOf(latResult.resolution, lonResult.resolution)
}

// ── Coordinate parsing ────────────────────────────────────────────────────────

internal data class NMEACoordResult(val value: Double, val resolution: Double)

internal fun parseNMEACoordWithRes(coord: String, hemisphere: String, isLon: Boolean): NMEACoordResult {
    if (coord.isEmpty() || hemisphere.isEmpty()) {
        throw Exception("empty coordinate or hemisphere")
    }

    val degLen = if (isLon) 3 else 2

    if (coord.length < degLen + 1) throw Exception("coordinate too short")

    val deg = coord.substring(0, degLen).toDoubleOrNull()
        ?: throw Exception("invalid degrees: ${coord.substring(0, degLen)}")

    val min = coord.substring(degLen).toDoubleOrNull()
        ?: throw Exception("invalid minutes: ${coord.substring(degLen)}")

    var result = deg + min / 60.0

    if (hemisphere == "S" || hemisphere == "W") result = -result

    val maxVal = if (isLon) 180.0 else 90.0
    if (abs(result) > maxVal) throw Exception("coordinate out of range: $result")

    val minPart = coord.substring(degLen)
    val dotIdx = minPart.indexOf('.')
    val decimals = if (dotIdx >= 0) minPart.length - dotIdx - 1 else 0
    val res = nmeaPosResolution(decimals)

    return NMEACoordResult(result, res)
}

private fun nmeaPosResolution(decimals: Int): Double {
    val base = if (decimals <= -2) 600.0 else 1000.0
    return 1.852 * base * 10.0.pow(-decimals.toDouble())
}
