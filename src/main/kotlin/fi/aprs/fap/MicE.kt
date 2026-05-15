package fi.aprs.fap

// ── Mic-E destination field decode table ─────────────────────────────────────

private data class MicEDestInfo(val digit: Int, val msgBit: Int, val isNorth: Boolean)

private val micEDestTable: Map<Char, MicEDestInfo> =
    mapOf(
        '0' to MicEDestInfo(0, 0, false),
        '1' to MicEDestInfo(1, 0, false),
        '2' to MicEDestInfo(2, 0, false),
        '3' to MicEDestInfo(3, 0, false),
        '4' to MicEDestInfo(4, 0, false),
        '5' to MicEDestInfo(5, 0, false),
        '6' to MicEDestInfo(6, 0, false),
        '7' to MicEDestInfo(7, 0, false),
        '8' to MicEDestInfo(8, 0, false),
        '9' to MicEDestInfo(9, 0, false),
        'A' to MicEDestInfo(0, 1, false),
        'B' to MicEDestInfo(1, 1, false),
        'C' to MicEDestInfo(2, 1, false),
        'D' to MicEDestInfo(3, 1, false),
        'E' to MicEDestInfo(4, 1, false),
        'F' to MicEDestInfo(5, 1, false),
        'G' to MicEDestInfo(6, 1, false),
        'H' to MicEDestInfo(7, 1, false),
        'I' to MicEDestInfo(8, 1, false),
        'J' to MicEDestInfo(9, 1, false),
        'K' to MicEDestInfo(0, 1, false),
        'L' to MicEDestInfo(0, 0, false),
        'P' to MicEDestInfo(0, 1, true),
        'Q' to MicEDestInfo(1, 1, true),
        'R' to MicEDestInfo(2, 1, true),
        'S' to MicEDestInfo(3, 1, true),
        'T' to MicEDestInfo(4, 1, true),
        'U' to MicEDestInfo(5, 1, true),
        'V' to MicEDestInfo(6, 1, true),
        'W' to MicEDestInfo(7, 1, true),
        'X' to MicEDestInfo(8, 1, true),
        'Y' to MicEDestInfo(9, 1, true),
        'Z' to MicEDestInfo(0, 1, true),
    )

// ── Main Mic-E parser ─────────────────────────────────────────────────────────

internal fun Packet.parseMicE(options: ParseOptions) {
    type = PacketType.LOCATION
    format = Format.MICE

    // body[0] is the type char (' or `), info field starts at body[1]
    val info = body.copyOfRange(1, body.size)

    var dst = dstCallsign
    val dashIdx = dst.indexOf('-')
    if (dashIdx >= 0) dst = dst.substring(0, dashIdx)

    if (dst.length < 6) fail(ParseError.MICE_INV_DSTCALL, "Mic-E destination callsign too short")
    if (info.size < 8) fail(ParseError.MICE_SHORT, "Mic-E information field too short")

    val (lat, isNorth, lonOffset, isWest, msgBits) = decodeMicEDest(dst)
    mBits = msgBits
    latitude = lat
    posAmbiguity = 0
    posResolution = posResolutionValue(0)

    decodeMicELon(info, lonOffset, isWest)

    // Speed and course from bytes 3-5
    val sp = info[3].toInt().and(0xFF) - 28
    val dc = info[4].toInt().and(0xFF) - 28
    val se = info[5].toInt().and(0xFF) - 28

    var spd = (sp * 10 + dc / 10).toDouble()
    var crs = (dc % 10) * 100 + se

    if (spd >= 800) spd -= 800
    if (crs >= 400) crs -= 400

    speed = spd * 1.852
    course = crs

    symbolCode = info[6].toInt().and(0xFF).toChar()
    symbolTable = info[7].toInt().and(0xFF).toChar()

    if (!isValidMicESymbolTable(symbolTable)) {
        fail(ParseError.SYM_INV_TABLE, "invalid Mic-E symbol table: $symbolTable")
    }

    var comment = if (info.size > 8) info.copyOfRange(8, info.size).toString(Charsets.ISO_8859_1) else ""

    // The first byte of the comment area is the Mic-E messaging-prefix
    // indicator. Set the messaging flag based on it; the byte itself is
    // left in place — downstream consumers (e.g. kotlin-aprs-deviceid)
    // strip it from the comment, since the messaging-capable state is
    // now exposed here on Packet.messaging.
    //   '`' (0x60) → current TNC, messaging-capable
    //   "'" (0x27) → old TNC, not messaging-capable
    if (comment.isNotEmpty()) {
        when (comment[0]) {
            '`' -> messaging = true
            '\'' -> messaging = false
        }
    }

    comment = parseMicEBase91Telemetry(comment)

    // Altitude: 3 base-91 chars followed by '}'
    val braceIdx = comment.indexOf('}')
    if (braceIdx >= 3) {
        val a1 = comment[braceIdx - 3]
        val a2 = comment[braceIdx - 2]
        val a3 = comment[braceIdx - 1]
        if (a1 in '!'..'{' && a2 in '!'..'{' && a3 in '!'..'{') {
            altitude = (
                (a1.code - 33) * 91 * 91 +
                    (a2.code - 33) * 91 +
                    (a3.code - 33)
            ).toDouble() - 10000.0
            comment = comment.substring(0, braceIdx - 3) + comment.substring(braceIdx + 1)
        }
    }

    comment = parseDAO(comment)

    if (comment.length >= 2 && (comment[0] == '\'' || comment[0] == '`')) {
        comment = parseMicETelemetry(comment)
    }

    this.comment = comment
}

// ── Mangled Mic-E parser ──────────────────────────────────────────────────────

internal fun Packet.parseMicEMangled(options: ParseOptions) {
    var info = body.copyOfRange(1, body.size)

    var dst = dstCallsign
    val dashIdx = dst.indexOf('-')
    if (dashIdx >= 0) dst = dst.substring(0, dashIdx)

    if (dst.length < 6 || info.size < 7) {
        fail(ParseError.MICE_SHORT, "mangled Mic-E packet too short")
    }

    // Check for known corruption pattern: 4 valid lon bytes, one collapsed space, symbol code/table
    val b0 = info[0].toInt().and(0xFF)
    val b1 = info[1].toInt().and(0xFF)
    val b2 = info[2].toInt().and(0xFF)
    val b3 = info[3].toInt().and(0xFF)
    val b4 = info[4].toInt().and(0xFF)
    val b5 = info[5].toInt().and(0xFF)
    val b6 = info[6].toInt().and(0xFF)

    if (b0 in 0x26..0x7F &&
        b1 in 0x26..0x61 &&
        b2 in 0x1C..0x7F &&
        b3 in 0x1C..0x7F &&
        b4 == 0x20 &&
        b5 in 0x21..0x7D &&
        (
            b6 == '/'.code || b6 == '\\'.code ||
                (b6 in 'A'.code..'Z'.code) || (b6 in '0'.code..'9'.code)
        )
    ) {
        // Insert missing space at position 5 to restore 8-byte layout
        val fixed = ByteArray(info.size + 1)
        info.copyInto(fixed, 0, 0, 5)
        fixed[5] = ' '.code.toByte()
        info.copyInto(fixed, 6, 5)
        info = fixed
    } else {
        fail(ParseError.MICE_INV_INFOFIELD, "invalid Mic-E information field")
    }

    miceMangled = true
    type = PacketType.LOCATION
    format = Format.MICE

    val (lat, isNorth, lonOffset, isWest, msgBits) = decodeMicEDest(dst)
    mBits = msgBits
    latitude = lat
    posAmbiguity = 0
    posResolution = posResolutionValue(0)

    decodeMicELon(info, lonOffset, isWest)

    // Skip speed/course — unreliable in mangled packets
    symbolCode = info[6].toInt().and(0xFF).toChar()
    symbolTable = info[7].toInt().and(0xFF).toChar()

    comment = if (info.size > 8) info.copyOfRange(8, info.size).toString(Charsets.ISO_8859_1) else ""

    // See main parser: set messaging flag from the comment's leading byte.
    if (comment.isNotEmpty()) {
        when (comment[0]) {
            '`' -> messaging = true
            '\'' -> messaging = false
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

private data class MicEDestResult(
    val lat: Double,
    val isNorth: Boolean,
    val lonOffset: Int,
    val isWest: Boolean,
    val msgBits: String,
)

private fun Packet.decodeMicEDest(dst: String): MicEDestResult {
    val latDigits = IntArray(6)
    val msgBuf = CharArray(3)
    var isNorth = false
    var lonOffset = 0
    var isWest = false

    for (i in 0 until 6) {
        val info =
            micEDestTable[dst[i]]
                ?: fail(ParseError.MICE_INV_DSTCALL, "invalid Mic-E destination character: ${dst[i]}")
        latDigits[i] = info.digit
        if (i < 3) msgBuf[i] = '0' + info.msgBit
        if (i == 3 && info.isNorth) isNorth = true
        if (i == 4 && dst[i] in 'P'..'Z') lonOffset = 100
        if (i == 5 && dst[i] in 'P'..'Z') isWest = true
    }

    val latDeg = latDigits[0] * 10.0 + latDigits[1]
    val latMin = latDigits[2] * 10.0 + latDigits[3] + (latDigits[4] * 10.0 + latDigits[5]) / 100.0
    var lat = latDeg + latMin / 60.0
    if (!isNorth) lat = -lat

    return MicEDestResult(lat, isNorth, lonOffset, isWest, String(msgBuf))
}

private fun Packet.decodeMicELon(
    info: ByteArray,
    lonOffset: Int,
    isWest: Boolean,
) {
    var lonDeg = info[0].toInt().and(0xFF) - 28 + lonOffset
    if (lonDeg in 180..189) {
        lonDeg -= 80
    } else if (lonDeg in 190..199) {
        lonDeg -= 190
    }

    var lonMin = info[1].toInt().and(0xFF) - 28
    if (lonMin >= 60) lonMin -= 60

    val lonHMin = info[2].toInt().and(0xFF) - 28

    var lon = lonDeg + (lonMin + lonHMin / 100.0) / 60.0
    if (isWest) lon = -lon
    longitude = lon
}

private fun isValidMicESymbolTable(c: Char): Boolean = c == '/' || c == '\\' || c in 'A'..'Z' || c in '0'..'9'

// ── Base-91 telemetry in Mic-E comments ──────────────────────────────────────

internal fun Packet.parseMicEBase91Telemetry(comment: String): String {
    var bestStart = -1
    var bestEnd = -1

    var end = comment.length - 1
    while (end >= 0) {
        if (comment[end] != '|') {
            end--
            continue
        }
        var start = end - 1
        while (start >= 0) {
            if (comment[start] != '|') {
                start--
                continue
            }
            val content = comment.substring(start + 1, end)
            if (content.length >= 4 && content.length % 2 == 0 &&
                content.all { it in '!'..'{' }
            ) {
                if (start > bestStart) {
                    bestStart = start
                    bestEnd = end
                }
                break
            }
            start--
        }
        if (bestStart >= 0) break
        end--
    }

    if (bestStart < 0) return comment

    val tlmData = comment.substring(bestStart + 1, bestEnd)
    val pairs = tlmData.length / 2

    val seq = (tlmData[0].code - 33) * 91 + (tlmData[1].code - 33)
    val tlm = Telemetry()
    tlm.seq = seq

    val vals = arrayOfNulls<Double>(5)
    for (i in 1 until minOf(pairs, 6)) {
        val idx = i * 2
        val v = ((tlmData[idx].code - 33) * 91 + (tlmData[idx + 1].code - 33)).toDouble()
        vals[i - 1] = v
    }

    if (pairs >= 7) {
        val bitsVal = (tlmData[12].code - 33) * 91 + (tlmData[13].code - 33)
        val bits = CharArray(8)
        for (b in 0 until 8) {
            bits[b] = if (bitsVal and (1 shl b) != 0) '1' else '0'
        }
        tlm.bits = String(bits)
    }

    tlm.vals = vals.toList()
    telemetryData = tlm

    return (comment.substring(0, bestStart) + comment.substring(bestEnd + 1)).trim()
}

// ── Hex telemetry in Mic-E comments ──────────────────────────────────────────

internal fun Packet.parseMicETelemetry(comment: String): String {
    if (comment.length < 2 || comment[0] != '\'') return comment

    val rest = comment.substring(1)
    val hexLen =
        when {
            rest.length >= 10 && isHexString(rest.substring(0, 10)) -> 10
            rest.length >= 4 && isHexString(rest.substring(0, 4)) -> 4
            else -> return comment
        }

    val hex = rest.substring(0, hexLen)
    val vals: MutableList<Double?> = mutableListOf()

    if (hexLen == 4) {
        val v1 = hex.substring(0, 2).toLong(16).toDouble()
        val v3 = hex.substring(2, 4).toLong(16).toDouble()
        vals.add(v1)
        vals.add(0.0)
        vals.add(v3)
    } else {
        for (i in 0 until hexLen step 2) {
            vals.add(hex.substring(i, i + 2).toLong(16).toDouble())
        }
    }

    telemetryData = Telemetry().also { it.vals = vals }

    return rest.substring(hexLen).trimStart()
}

private fun isHexString(s: String): Boolean = s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

// ── MBits → human-readable message type ──────────────────────────────────────

/** Converts Mic-E message bits to a human-readable status string. */
fun micEMBitsToMessage(mbits: String): String =
    when (mbits) {
        "111" -> "Off Duty"
        "110" -> "En Route"
        "101" -> "In Service"
        "100" -> "Returning"
        "011" -> "Committed"
        "010" -> "Special"
        "001" -> "Priority"
        "000" -> "Emergency"
        else -> "Unknown"
    }

// ── Position resolution helper (reused from Position.kt via internal) ─────────

private fun posResolutionValue(ambiguity: Int): Double =
    when (ambiguity) {
        0 -> 1852.0 / 100.0
        else -> 1852.0 / 100.0
    }
