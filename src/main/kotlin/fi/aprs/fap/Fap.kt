package fi.aprs.fap

/**
 * Parses an APRS packet from a raw byte array (as received from an APRS-IS stream).
 *
 * Returns a fully-populated [Packet] on success.
 * Throws [ParseException] on failure; the exception carries the partially-parsed
 * packet in [ParseException.packet] so callers can inspect fields decoded before the error.
 */
fun parse(
    raw: ByteArray,
    options: ParseOptions = ParseOptions(),
): Packet {
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
fun parse(
    raw: String,
    options: ParseOptions = ParseOptions(),
): Packet = parse(raw.toByteArray(Charsets.ISO_8859_1), options)

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
            val normalized =
                checkAX25Call(call)
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
        '}' -> {
            // Third-party encapsulation is only valid on RF. When not allowed,
            // fail rather than fall through to last-resort parsing, which would
            // mis-attribute the inner frame's position to the outer callsign.
            if (options.allowThirdParty) {
                parseThirdParty(options)
            } else {
                fail(ParseError.THIRDPARTY_NOT_ALLOWED, "third-party packets are not allowed in this context")
            }
        }
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

/**
 * Parses an APRS "third party" packet (body type identifier `}`).
 *
 * A third-party packet encapsulates a complete, original APRS frame inside the
 * body of a relaying frame — typically produced by an igate injecting a packet
 * heard on one medium (e.g. APRS-IS) onto another (e.g. RF), or vice versa.
 * The wire form of the body is:
 *
 * ```
 * }SRC>DST,PATH:innerbody
 * ```
 *
 * where everything after the leading `}` is itself a complete TNC2-format
 * packet. The header already parsed for this [Packet] describes the *outer*
 * (relaying) frame; it is moved to the `outer*` fields, and the encapsulated
 * inner frame is parsed in its place so that [srcCallsign], [dstCallsign],
 * [digipeaters] and all content fields describe the *original* station.
 *
 * Only a single layer of encapsulation is supported: if the inner frame is
 * itself a third-party packet (its body again begins with `}`), parsing fails
 * with [ParseError.THIRDPARTY_NESTED].
 */
internal fun Packet.parseThirdParty(options: ParseOptions) {
    // The header parsed so far belongs to the outer (relaying) frame: record it.
    thirdParty = true
    outerSrcCallsign = srcCallsign
    outerDstCallsign = dstCallsign
    outerDigipeaters = digipeaters

    // Everything after the leading '}' is a complete TNC2 packet "HDR:innerbody".
    val inner = body.sliceArray(1 until body.size)
    val colonIdx = inner.indexOf(':'.code.toByte())
    if (colonIdx < 0) fail(ParseError.THIRDPARTY_NO_BODY, "no inner packet body in third-party packet")

    header = inner.sliceArray(0 until colonIdx).toString(Charsets.ISO_8859_1)
    body = inner.sliceArray(colonIdx + 1 until inner.size)

    // Reject a second layer of encapsulation: only one is supported.
    if (body.isNotEmpty() && body[0] == '}'.code.toByte()) {
        fail(ParseError.THIRDPARTY_NESTED, "nested third-party encapsulation is not supported")
    }

    // parseHeader appends to digipeaters; reset the outer path first.
    digipeaters = emptyList()

    // The encapsulated frame is always TNC2 text format with APRS-IS callsign
    // rules, even when the outer frame arrived as a binary AX.25 RF frame.
    val innerOptions = if (options.isAX25) options.copy(isAX25 = false) else options
    parseHeader(innerOptions)
    parseBody(innerOptions)
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

// ── Callsign validation helpers ───────────────────────────────────────────────

private fun isAlnumDash(
    s: String,
    maxLen: Int,
): Boolean {
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
