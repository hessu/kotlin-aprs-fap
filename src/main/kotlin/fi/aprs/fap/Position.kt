package fi.aprs.fap

import kotlin.math.floor
import kotlin.math.pow

// ── Internal sentinel for ambiguity errors ────────────────────────────────────

private class AmbiguityException(message: String) : Exception(message)

// ── Position entry points ─────────────────────────────────────────────────────

internal fun Packet.parsePositionNoTimestamp(options: ParseOptions, typeChar: Char) {
    type = PacketType.LOCATION
    messaging = (typeChar == '=')

    val bodyStr = bodyAsString().drop(1) // skip type character
    if (bodyStr.isEmpty()) fail(ParseError.POS_SHORT, "position body too short")

    if (bodyStr[0].isDigit() || bodyStr[0] == ' ') {
        parseUncompressedPosition(bodyStr, options)
    } else {
        parseCompressedPosition(bodyStr, options)
    }
}

internal fun Packet.parsePositionWithTimestamp(options: ParseOptions, typeChar: Char) {
    type = PacketType.LOCATION
    messaging = (typeChar == '@')

    val bodyStr = bodyAsString().drop(1) // skip type character
    if (bodyStr.length < 7) fail(ParseError.POS_SHORT, "position body too short for timestamp")

    if (options.rawTimestamp) {
        rawTimestamp = bodyStr.substring(0, 6)
    } else {
        val ts = parseTimestamp(bodyStr.substring(0, 7))
        if (ts == null) {
            warn(ParseError.TIMESTAMP_INV, "invalid timestamp: ${bodyStr.substring(0, 7)}")
        } else {
            timestamp = ts
        }
    }

    val posBody = bodyStr.drop(7)
    if (posBody.isEmpty()) fail(ParseError.POS_SHORT, "no position data after timestamp")

    if (posBody[0].isDigit() || posBody[0] == ' ') {
        parseUncompressedPosition(posBody, options)
    } else {
        parseCompressedPosition(posBody, options)
    }
}

// ── Uncompressed position ─────────────────────────────────────────────────────

internal fun Packet.parseUncompressedPosition(bodyStr: String, options: ParseOptions) {
    format = Format.UNCOMPRESSED

    if (bodyStr.length < 19) fail(ParseError.POS_SHORT, "uncompressed position too short")

    val (lat, ambiguity) = try {
        parseUncompressedLat(bodyStr.substring(0, 8))
    } catch (e: AmbiguityException) {
        fail(ParseError.LOC_AMB_INV, e.message ?: "invalid position ambiguity")
    } catch (e: Exception) {
        fail(ParseError.LOC_INV, "invalid latitude: ${e.message}")
    }

    latitude = lat
    posAmbiguity = ambiguity

    val symTable = bodyStr[8]
    if (!isValidSymbolTable(symTable)) {
        fail(ParseError.SYM_INV_TABLE, "invalid symbol table: $symTable")
    }
    symbolTable = symTable

    val lon = try {
        parseUncompressedLon(bodyStr.substring(9, 18), ambiguity)
    } catch (e: AmbiguityException) {
        fail(ParseError.LOC_AMB_INV, e.message ?: "invalid position ambiguity")
    } catch (e: Exception) {
        fail(ParseError.POS_LON_INV, "invalid longitude: ${e.message}")
    }
    longitude = lon

    symbolCode = bodyStr[18]

    val res = posResolution(ambiguity)
    posResolution = res

    if (bodyStr.length > 19) {
        parsePositionComment(bodyStr.drop(19))
    }
}

// ── Compressed position ───────────────────────────────────────────────────────

internal fun Packet.parseCompressedPosition(bodyStr: String, options: ParseOptions) {
    format = Format.COMPRESSED

    if (bodyStr.length < 13) fail(ParseError.COMP_SHORT, "compressed position too short")

    symbolTable = bodyStr[0]

    val lat = 90.0 - (
        (bodyStr[1].code - 33) * 91 * 91 * 91 +
        (bodyStr[2].code - 33) * 91 * 91 +
        (bodyStr[3].code - 33) * 91 +
        (bodyStr[4].code - 33)
    ).toDouble() / 380926.0

    val lon = -180.0 + (
        (bodyStr[5].code - 33) * 91 * 91 * 91 +
        (bodyStr[6].code - 33) * 91 * 91 +
        (bodyStr[7].code - 33) * 91 +
        (bodyStr[8].code - 33)
    ).toDouble() / 190463.0

    latitude = lat
    longitude = lon

    symbolCode = bodyStr[9]

    val c1 = bodyStr[10].code - 33
    val s1 = bodyStr[11].code - 33
    val compType = bodyStr[12].code - 33

    if (c1 != -1) {
        gpsFixStatus = if (compType and 0x20 != 0) 1 else 0
    }

    posResolution = 0.291

    if (c1 != -1 && s1 != -1) {
        when {
            compType and 0x18 == 0x10 -> {
                // Altitude (GPGGA source)
                val cs = c1 * 91 + s1
                altitude = 1.002.pow(cs.toDouble()) * 0.3048
            }
            c1 in 0..89 -> {
                // Course/speed
                course = if (c1 == 0) 360 else c1 * 4
                speed = (1.08.pow(s1.toDouble()) - 1.0) * 1.852
            }
            c1 == 90 -> {
                // Radio range
                radioRange = 2.0 * 1.08.pow(s1.toDouble()) * 1.609344
            }
        }
    }

    if (bodyStr.length > 13) {
        val comment = bodyStr.drop(13)

        if (symbolCode == '_') {
            type = PacketType.WX
            // Weather parsing from comment is handled when wx module is implemented
            return
        }

        val afterTelemetry = stripInlineTelemetry(comment)
        val afterDAO = parseDAO(afterTelemetry)
        this.comment = afterDAO.trim()
    }
}

// ── Position fallback (last-resort !-position) ────────────────────────────────

internal fun Packet.parsePositionFallback(options: ParseOptions) {
    val bodyStr = bodyAsString()
    val idx = bodyStr.indexOf('!')
    if (idx < 0 || idx > 39) fail(ParseError.TYPE_NOT_SUPPORTED, "unsupported packet type")

    val rest = bodyStr.drop(idx + 1)

    if (rest.isNotEmpty() && (rest[0].isDigit() || rest[0] == ' ')) {
        if (rest.length < 19) fail(ParseError.TYPE_NOT_SUPPORTED, "unsupported packet type")
        type = PacketType.LOCATION
        messaging = false
        parseUncompressedPosition(rest, options)
        return
    }

    if (rest.length < 13 || !isCompressedTableChar(rest[0])) {
        fail(ParseError.TYPE_NOT_SUPPORTED, "unsupported packet type")
    }
    type = PacketType.LOCATION
    messaging = false
    parseCompressedPosition(rest, options)
}

// ── Position comment ──────────────────────────────────────────────────────────

internal fun Packet.parsePositionComment(comment: String) {
    if (symbolCode == '_') {
        type = PacketType.WX
        // Full weather parsing from comment handled when wx module is implemented
        return
    }

    var s = comment

    // PHG / PHGRA
    if (s.startsWith("PHG") && s.length >= 7 && isPhgBase(s.substring(3, 7))) {
        if (s.length >= 9 && isUpperAlphaNum(s[7]) && s[8] == '/') {
            phg = s.substring(3, 8)
            s = s.drop(9)
        } else {
            phg = s.substring(3, 7)
            s = s.drop(7)
        }
    }

    // RNG
    if (s.startsWith("RNG") && s.length >= 7) {
        val rng = s.substring(3, 7).toIntOrNull()
        if (rng != null) {
            radioRange = rng * 1.609344
            s = s.drop(7)
        }
    }

    // Course/speed: CCC/SSS
    if (s.length >= 7 && s[3] == '/') {
        val courseStr = s.substring(0, 3)
        val speedStr = s.substring(4, 7)
        val c = courseStr.toIntOrNull()
        val sp = speedStr.toIntOrNull()
        if (c != null && sp != null && c in 0..360) {
            course = c
            speed = sp * 1.852
            s = s.drop(7)
        }
    }

    // Altitude: /A=NNNNNN (exactly 6 chars, may be negative e.g. /A=-00079)
    val altIdx = s.indexOf("/A=")
    if (altIdx >= 0) {
        val altStart = altIdx + 3
        val altEnd = altStart + 6
        if (altEnd <= s.length) {
            val altFt = s.substring(altStart, altEnd).toIntOrNull()
            if (altFt != null) {
                altitude = altFt * 0.3048
                s = s.substring(0, altIdx) + s.substring(altEnd)
            }
        }
    }

    s = parseDAO(s)

    // Strip leading '/'
    if (s.isNotEmpty() && s[0] == '/') s = s.drop(1)

    this.comment = s.trim()
}

// ── DAO extension ─────────────────────────────────────────────────────────────

internal fun Packet.parseDAO(comment: String): String {
    var s = comment
    var i = 0
    while (i + 4 < s.length) {
        if (s[i] == '!' && s[i + 4] == '!') {
            val datumByte = s[i + 1]
            val d1 = s[i + 2]
            val d2 = s[i + 3]

            if (datumByte in 'A'..'Z' && d1.isDigit() && d2.isDigit()) {
                daoDatumByte = datumByte
                applyHumanDAO(d1, d2)
                s = s.substring(0, i) + s.substring(i + 5)
                break
            }
            if (datumByte in 'a'..'z' && d1 in '!'..'{'  && d2 in '!'..'{'  ) {
                daoDatumByte = datumByte.uppercaseChar()
                applyBase91DAO(d1, d2)
                s = s.substring(0, i) + s.substring(i + 5)
                break
            }
        }
        i++
    }
    return s
}

private fun Packet.applyHumanDAO(d1: Char, d2: Char) {
    val lat = latitude ?: return
    val lon = longitude ?: return
    val latAdd = (d1 - '0') * 0.001 / 60.0
    val lonAdd = (d2 - '0') * 0.001 / 60.0
    latitude = if (lat < 0) lat - latAdd else lat + latAdd
    longitude = if (lon < 0) lon - lonAdd else lon + lonAdd
    posResolution = 1.852
}

private fun Packet.applyBase91DAO(d1: Char, d2: Char) {
    val lat = latitude ?: return
    val lon = longitude ?: return
    val latAdd = (d1.code - 33) / 91.0 * 0.01 / 60.0
    val lonAdd = (d2.code - 33) / 91.0 * 0.01 / 60.0
    latitude = if (lat < 0) lat - latAdd else lat + latAdd
    longitude = if (lon < 0) lon - lonAdd else lon + lonAdd
    posResolution = 0.1852
}

// ── Inline telemetry stripping ────────────────────────────────────────────────

internal fun stripInlineTelemetry(comment: String): String {
    val lastPipe = comment.lastIndexOf('|')
    if (lastPipe > 0) {
        val firstPipe = comment.indexOf('|')
        if (firstPipe < lastPipe) {
            return comment.substring(0, firstPipe) + comment.substring(lastPipe + 1)
        }
    }
    return comment
}

// ── Lat/lon parsing helpers ───────────────────────────────────────────────────

private data class DegMin(val deg: Double, val mm: Double, val ambiguity: Int)

private fun parseDegreesMinutes(
    s: String,
    degDigits: Int,
    computeAmbiguity: Boolean,
    knownAmbiguity: Int,
): DegMin {
    var deg = 0.0
    for (i in 0 until degDigits) {
        val c = s[i]
        if (!c.isDigit()) throw Exception("invalid degrees character: $c")
        deg = deg * 10 + (c - '0')
    }

    val dotPos = degDigits + 2
    if (s[dotPos] != '.' && s[dotPos] != ' ') {
        throw Exception("expected '.' at position $dotPos, got '${s[dotPos]}'")
    }

    val minPositions = intArrayOf(degDigits, degDigits + 1, degDigits + 3, degDigits + 4)
    val minDigits = IntArray(4)
    val isSpace = BooleanArray(4)
    for ((idx, pos) in minPositions.withIndex()) {
        val c = s[pos]
        when {
            c == ' ' -> { minDigits[idx] = 0; isSpace[idx] = true }
            c.isDigit() -> minDigits[idx] = c - '0'
            else -> throw Exception("invalid minute character at position $pos: $c")
        }
    }

    val ambiguity: Int
    if (computeAmbiguity) {
        var amb = 0
        for (i in 3 downTo 0) {
            if (isSpace[i]) amb++ else break
        }
        for (i in 0 until 4 - amb) {
            if (isSpace[i]) throw AmbiguityException("space in non-trailing position")
        }
        ambiguity = amb
    } else {
        ambiguity = knownAmbiguity
        for (i in 0 until 4 - ambiguity) {
            if (isSpace[i]) throw AmbiguityException("longitude has spaces in non-ambiguous digits")
        }
    }

    var mm = minDigits[0] * 10.0 + minDigits[1] + minDigits[2] * 0.1 + minDigits[3] * 0.01

    if (ambiguity > 0) {
        mm = when (ambiguity) {
            1 -> floor(mm / 0.1) * 0.1 + 0.05
            2 -> floor(mm) + 0.5
            3 -> floor(mm / 10) * 10 + 5.0
            4 -> { deg = floor(deg); 30.0 }
            else -> mm
        }
    }

    return DegMin(deg, mm, ambiguity)
}

private fun parseUncompressedLat(s: String): Pair<Double, Int> {
    if (s.length != 8) throw Exception("latitude must be 8 characters, got ${s.length}")

    val hemisphere = s[7]
    if (hemisphere != 'N' && hemisphere != 'S') {
        throw Exception("invalid hemisphere: $hemisphere")
    }

    val dm = parseDegreesMinutes(s.substring(0, 7), 2, true, 0)
    var lat = dm.deg + dm.mm / 60.0

    if (lat > 90.0 || lat < 0.0) throw Exception("latitude out of range: $lat")

    if (hemisphere == 'S') lat = -lat
    return Pair(lat, dm.ambiguity)
}

private fun parseUncompressedLon(s: String, ambiguity: Int): Double {
    if (s.length != 9) throw Exception("longitude must be 9 characters, got ${s.length}")

    val hemisphere = s[8]
    if (hemisphere != 'E' && hemisphere != 'W') {
        throw Exception("invalid hemisphere: $hemisphere")
    }

    val dm = parseDegreesMinutes(s.substring(0, 8), 3, false, ambiguity)
    var lon = dm.deg + dm.mm / 60.0

    if (lon > 180.0 || lon < 0.0) throw Exception("longitude out of range: $lon")

    if (hemisphere == 'W') lon = -lon
    return lon
}

// ── Symbol table / position validation helpers ────────────────────────────────

private fun isValidSymbolTable(c: Char): Boolean =
    c == '/' || c == '\\' || c in 'A'..'Z' || c in '0'..'9'

internal fun isCompressedTableChar(c: Char): Boolean =
    c == '/' || c == '\\' || c in 'A'..'Z' || c in 'a'..'j'

private fun isPhgBase(s: String): Boolean =
    s.length >= 4 &&
    s[0].isDigit() &&
    s[1].code in 0x30..0x7e &&
    s[2].isDigit() &&
    s[3].isDigit()

private fun isUpperAlphaNum(c: Char): Boolean = c.isDigit() || c in 'A'..'Z'

// ── Position resolution ───────────────────────────────────────────────────────

private fun posResolution(ambiguity: Int): Double = when (ambiguity) {
    0 -> 1852.0 / 100.0
    1 -> 1852.0 / 10.0
    2 -> 1852.0
    3 -> 1852.0 * 10.0
    4 -> 1852.0 * 60.0
    else -> 1852.0 / 100.0
}
