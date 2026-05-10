package fi.aprs.fap

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Optional parameters for [encodePosition]. */
data class EncodePositionOpts(
    val ambiguity: Int = 0,
    val timestamp: Instant? = null,
    val messagingCapable: Boolean = false,
    val dao: Boolean = false,
    val comment: String = "",
)

/**
 * Creates an uncompressed APRS position string.
 *
 * @param lat latitude in decimal degrees
 * @param lon longitude in decimal degrees
 * @param speed speed in km/h, or null
 * @param course course in degrees, or null
 * @param altitude altitude in meters, or null
 * @param symbol 2-character string: symbol table + symbol code
 * @param opts optional encoding parameters
 * @throws ParseException with code [ParseError.POS_ENC_INV] for invalid inputs
 */
fun encodePosition(
    lat: Double,
    lon: Double,
    speed: Double?,
    course: Double?,
    altitude: Double?,
    symbol: String,
    opts: EncodePositionOpts? = null,
): String {
    val o = if (opts != null && opts.ambiguity > 0) opts.copy(dao = false) else opts ?: EncodePositionOpts()

    if (lat < -89.99999 || lat > 89.99999 || lon < -179.99999 || lon > 179.99999) {
        throw ParseException(ParseError.POS_ENC_INV, "invalid coordinates: lat=$lat lon=$lon")
    }

    if (symbol.length != 2) {
        throw ParseException(ParseError.POS_ENC_INV, "invalid symbol length: ${symbol.length}")
    }
    val symbolTable = symbol[0]
    val symbolCode = symbol[1]
    if (!isValidEncSymbolTable(symbolTable)) {
        throw ParseException(ParseError.POS_ENC_INV, "invalid symbol table: $symbolTable")
    }
    val sc = symbolCode.code
    if (sc < 0x21 || (sc > 0x7b && sc != 0x7d)) {
        throw ParseException(ParseError.POS_ENC_INV, "invalid symbol code: $symbolCode")
    }

    val absLat = if (lat < 0) -lat else lat
    val absLon = if (lon < 0) -lon else lon
    val latDeg = absLat.toInt()
    val lonDeg = absLon.toInt()
    val latMin = (absLat - latDeg) * 60
    val lonMin = (absLon - lonDeg) * 60

    val (latMinS, latMinDAO) = formatMinutes(latMin, o.dao)
    val (lonMinS, lonMinDAO) = formatMinutes(lonMin, o.dao)

    var latString = "%02d%s.%s".format(latDeg, latMinS.substring(0, 2), latMinS.substring(2, 4))
    var lonString = "%03d%s.%s".format(lonDeg, lonMinS.substring(0, 2), lonMinS.substring(2, 4))

    val amb = o.ambiguity
    if (amb in 1..4) {
        val latBytes = latString.toByteArray()
        val lonBytes = lonString.toByteArray()
        when (amb) {
            1, 2 -> {
                for (i in 0 until amb) {
                    latBytes[7 - 1 - i] = ' '.code.toByte()
                    lonBytes[8 - 1 - i] = ' '.code.toByte()
                }
                latString = String(latBytes)
                lonString = String(lonBytes)
            }
            3 -> {
                latString = latString.substring(0, 3) + " .  "
                lonString = lonString.substring(0, 4) + " .  "
            }
            4 -> {
                latString = latString.substring(0, 2) + "  .  "
                lonString = lonString.substring(0, 3) + "  .  "
            }
        }
    }

    latString += if (lat >= 0) "N" else "S"
    lonString += if (lon >= 0) "E" else "W"

    val result =
        buildString {
            val ts = o.timestamp
            if (ts != null) {
                val zdt = ZonedDateTime.ofInstant(ts, ZoneOffset.UTC)
                val dtid = if (o.messagingCapable) '@' else '/'
                append("%c%02d%02d%02dh".format(dtid, zdt.hour, zdt.minute, zdt.second))
            } else {
                append(if (o.messagingCapable) '=' else '!')
            }
            append(latString)
            append(symbolTable)
            append(lonString)
            append(symbolCode)

            if (speed != null && course != null && speed >= 0 && course >= 0) {
                val speedKnots = minOf(speed / 1.852, 999.0)
                val c = if (course > 360) 0.0 else course
                append("%03.0f/%03.0f".format(c, speedKnots))
            }

            if (altitude != null) {
                val altFeet = altitude / 0.3048
                if (altFeet >= 0) {
                    append("/A=%06.0f".format(altFeet))
                } else {
                    append("/A=-%05.0f".format(-altFeet))
                }
            }

            if (o.comment.isNotEmpty()) append(o.comment)

            if (o.dao && latMinDAO.isNotEmpty() && lonMinDAO.isNotEmpty()) {
                val latDAO = latMinDAO.toInt()
                val lonDAO = lonMinDAO.toInt()
                val latChar = (latDAO / 1.1 + 0.5).toInt() + 33
                val lonChar = (lonDAO / 1.1 + 0.5).toInt() + 33
                append("!w")
                append(latChar.toChar())
                append(lonChar.toChar())
                append('!')
            }
        }

    return result
}

private fun formatMinutes(
    min: Double,
    dao: Boolean,
): Pair<String, String> {
    val minS: String
    var daoS = ""
    if (dao) {
        minS = "%06.0f".format(min * 10000)
        if (minS.length > 4) daoS = minS.substring(4, 6)
    } else {
        minS = "%04.0f".format(min * 100)
    }
    if (minS.length >= 2 && minS[0] == '6' && minS[1] == '0') {
        return Pair("5999", "99")
    }
    return Pair(minS, daoS)
}

private fun isValidEncSymbolTable(c: Char): Boolean = c == '/' || c == '\\' || c in 'A'..'Z' || c in '0'..'9'
