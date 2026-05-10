package fi.aprs.fap

/**
 * Parses an APRS packet from a raw byte array (as received from an APRS-IS stream).
 *
 * Returns a fully-populated [Packet] on success.
 * Throws [ParseException] on failure; the exception carries the partially-parsed
 * packet in [ParseException.packet] so callers can inspect fields decoded before the error.
 */
fun parse(raw: ByteArray, options: ParseOptions = ParseOptions()): Packet {
    val p = Packet(raw)

    val colonIdx = raw.indexOf(':'.code.toByte())
    if (colonIdx < 0) p.fail(ParseError.PACKET_NO_BODY, "no packet body after header")

    p.header = raw.sliceArray(0 until colonIdx).toString(Charsets.ISO_8859_1)
    p.body = raw.sliceArray(colonIdx + 1 until raw.size)

    p.parseHeader(options)
    p.parseBody(options)

    return p
}

/**
 * Convenience overload accepting a [String]. The string is encoded as ISO-8859-1
 * before parsing, matching how APRS-IS packets appear on a stream.
 */
fun parse(raw: String, options: ParseOptions = ParseOptions()): Packet =
    parse(raw.toByteArray(Charsets.ISO_8859_1), options)

/**
 * Validates and normalizes an AX.25 callsign.
 * Returns the normalized callsign (uppercased, with SSID if present),
 * or null if invalid.
 *
 * Rules: base 1–6 alphanumeric characters (A–Z, 0–9), optional SSID -0 to -15.
 */
fun checkAX25Call(call: String): String? {
    val s = call.uppercase()
    val dashIdx = s.indexOf('-')
    val base: String
    val ssidStr: String

    if (dashIdx >= 0) {
        base = s.substring(0, dashIdx)
        ssidStr = s.substring(dashIdx + 1)
        if (ssidStr.isEmpty()) return null
    } else {
        base = s
        ssidStr = ""
    }

    if (base.length < 1 || base.length > 6) return null
    for (c in base) {
        if (!c.isDigit() && c !in 'A'..'Z') return null
    }

    if (ssidStr.isEmpty()) return base

    if (ssidStr.length > 2) return null
    for (c in ssidStr) {
        if (!c.isDigit()) return null
    }
    val ssid = ssidStr.toInt()
    if (ssid > 15) return null

    return "$base-$ssidStr"
}

// ── Internal extension: find byte in ByteArray ────────────────────────────────

internal fun ByteArray.indexOf(b: Byte): Int {
    for (i in indices) if (this[i] == b) return i
    return -1
}

// ── Header parsing ────────────────────────────────────────────────────────────

internal fun Packet.parseHeader(options: ParseOptions) {
    val gtIdx = header.indexOf('>')
    if (gtIdx < 0) fail(ParseError.SRCCALL_NOGT, "no '>' in header")

    srcCallsign = header.substring(0, gtIdx)
    if (srcCallsign.isEmpty()) fail(ParseError.SRCCALL_EMPTY, "source callsign is empty")

    if (options.isAX25) {
        srcCallsign = checkAX25Call(srcCallsign)
            ?: fail(ParseError.SRCCALL_NOAX25, "source callsign is not a valid AX.25 call")
    } else if (!isValidSrcCall(srcCallsign)) {
        fail(ParseError.SRCCALL_BADCHARS, "source callsign contains bad characters")
    }

    val rest = header.substring(gtIdx + 1)
    if (rest.isEmpty()) fail(ParseError.DSTCALL_EMPTY, "destination callsign is empty")

    val parts = rest.split(",")

    if (options.isAX25 && parts.size > 9) {
        fail(ParseError.DSTPATH_TOOMANY, "too many path components for AX.25")
    }

    dstCallsign = checkAX25Call(parts[0])
        ?: fail(ParseError.DSTCALL_NOAX25, "destination callsign is not a valid AX.25 call")

    var seenQConstr = false
    for (d in parts.drop(1)) {
        val wasDigied = d.endsWith("*")
        val call = if (wasDigied) d.dropLast(1) else d
        if (call.isEmpty()) fail(ParseError.DIGI_EMPTY, "empty digipeater callsign")

        if (options.isAX25) {
            val normalized = checkAX25Call(call)
                ?: fail(ParseError.DIGICALL_NOAX25, "digipeater callsign is not a valid AX.25 call")
            digipeaters = digipeaters + Digipeater(normalized, wasDigied)
        } else {
            if (isValidDigiCall(call)) {
                if (isQConstruct(call)) seenQConstr = true
            } else if (seenQConstr && isIPv6Hex(call)) {
                // IPv6 hex address after q-construct is allowed
            } else {
                fail(ParseError.DIGICALL_BADCHARS, "digipeater callsign contains bad characters")
            }
            digipeaters = digipeaters + Digipeater(call, wasDigied)
        }
    }
}

// ── Body dispatch ─────────────────────────────────────────────────────────────

internal fun Packet.parseBody(options: ParseOptions) {
    if (body.isEmpty()) fail(ParseError.PACKET_NO_BODY, "packet body is empty")

    val typeChar = body[0].toInt().and(0xFF).toChar()

    when (typeChar) {
        '!' -> {
            if (body.size > 1 && body[1] == '!'.code.toByte()) {
                parseULTWLogging(options)
            } else {
                parsePositionNoTimestamp(options, typeChar)
            }
        }
        '=' -> parsePositionNoTimestamp(options, typeChar)
        '/', '@' -> parsePositionWithTimestamp(options, typeChar)
        '\'', '`' -> {
            try {
                parseMicE(options)
            } catch (e: ParseException) {
                if (options.acceptBrokenMicE) {
                    speed = null
                    course = null
                    altitude = null
                    comment = ""
                    parseMicEMangled(options)
                } else {
                    throw e
                }
            }
        }
        ':' -> parseMessage(options)
        ';' -> parseObject(options)
        ')' -> parseItem(options)
        '>' -> parseStatus(options)
        '<' -> parseCapabilities(options)
        '_' -> parseWeatherPositionless(options)
        '$' -> {
            val bodyStr = bodyAsString()
            if (bodyStr.startsWith("\$ULTW")) parseULTW(options) else parseNMEA(options)
        }
        'T' -> {
            if (body.size > 1 && body[1] == '#'.code.toByte()) {
                parseTelemetry(options)
            } else {
                parsePositionOrBeacon(options)
            }
        }
        '{' -> {
            if (body.size > 1 && body[1] == '{'.code.toByte()) {
                fail(ParseError.EXP_UNSUPP, "unsupported experimental packet")
            }
            parsePositionOrBeacon(options)
        }
        else -> parsePositionOrBeacon(options)
    }
}

internal fun Packet.parsePositionOrBeacon(options: ParseOptions) {
    try {
        parsePositionFallback(options)
    } catch (e: ParseException) {
        if (isBeacon()) {
            type = PacketType.BEACON
        } else {
            throw e
        }
    }
}

private val beaconDestinations = setOf("ID", "BEACON", "UIDIGI", "CQ")

fun Packet.isBeacon(): Boolean = dstCallsign in beaconDestinations

// ── Stubs for packet types not yet implemented ────────────────────────────────

internal fun Packet.parseMessage(options: ParseOptions) {
    fail(ParseError.TYPE_NOT_SUPPORTED, "message not yet implemented")
}

internal fun Packet.parseItem(options: ParseOptions) {
    fail(ParseError.TYPE_NOT_SUPPORTED, "item not yet implemented")
}

internal fun Packet.parseWeatherPositionless(options: ParseOptions) {
    fail(ParseError.TYPE_NOT_SUPPORTED, "positionless weather not yet implemented")
}

internal fun Packet.parseULTW(options: ParseOptions) {
    fail(ParseError.TYPE_NOT_SUPPORTED, "ULTW weather not yet implemented")
}

internal fun Packet.parseULTWLogging(options: ParseOptions) {
    fail(ParseError.TYPE_NOT_SUPPORTED, "ULTW logging not yet implemented")
}

internal fun Packet.parseNMEA(options: ParseOptions) {
    fail(ParseError.TYPE_NOT_SUPPORTED, "NMEA not yet implemented")
}

internal fun Packet.parseTelemetry(options: ParseOptions) {
    fail(ParseError.TYPE_NOT_SUPPORTED, "telemetry not yet implemented")
}

// ── Callsign validation helpers ───────────────────────────────────────────────

private fun isAlnumDash(s: String, maxLen: Int): Boolean {
    if (s.isEmpty() || s.length > maxLen) return false
    return s.all { it.isDigit() || it in 'A'..'Z' || it in 'a'..'z' || it == '-' }
}

private fun isValidSrcCall(s: String): Boolean = isAlnumDash(s, 9)
private fun isValidDigiCall(s: String): Boolean = isAlnumDash(s, 9)
private fun isQConstruct(s: String): Boolean = s.length == 3 && s[0] == 'q'

internal fun isIPv6Hex(s: String): Boolean {
    if (s.length != 32) return false
    return s.all { it in '0'..'9' || it in 'A'..'F' }
}
