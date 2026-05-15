package fi.aprs.fap

/**
 * Thrown when an AX.25 frame is structurally invalid — too short, wrong control byte, etc.
 * Distinct from [ParseException] which signals APRS content errors.
 */
class Ax25DecodeException(message: String) : Exception(message)

/**
 * A decoded AX.25 frame: addressing fields plus the raw information field.
 * Always available even when the APRS body parse fails.
 *
 * @property srcCall Source callsign, e.g. "OH7LZB-9".
 * @property dstCall Destination callsign, e.g. "APFII0".
 * @property digipeaters Digipeater path; [Digipeater.wasDigied] reflects the AX.25 H-bit.
 * @property body AX.25 information field — the APRS payload bytes.
 */
data class Ax25Frame(
    val srcCall: String,
    val dstCall: String,
    val digipeaters: List<Digipeater>,
    val body: ByteArray,
) {
    /**
     * Builds the TNC2 monitoring line:
     * `"SRCALL>DSTCALL,DIGI1*,DIGI2:body_iso8859_1"`.
     * The AX.25 H-bit (has-been-digipeated) is rendered as a trailing `*`.
     */
    fun toMonitorLine(): String = buildHeader() + ":" + body.toString(Charsets.ISO_8859_1)

    internal fun buildHeader(): String = buildString {
        append(srcCall)
        append('>')
        append(dstCall)
        for (digi in digipeaters) {
            append(',')
            append(digi.call)
            if (digi.wasDigied) append('*')
        }
    }

    // ByteArray does not implement structural equals/hashCode, so we do it manually.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Ax25Frame) return false
        return srcCall == other.srcCall &&
            dstCall == other.dstCall &&
            digipeaters == other.digipeaters &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = srcCall.hashCode()
        result = 31 * result + dstCall.hashCode()
        result = 31 * result + digipeaters.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

/**
 * Result of [parseAx25].
 *
 * [frame] and [monitorLine] are always populated from the AX.25 binary header.
 * Either [packet] (on success) or [parseError] (on failure) is non-null.
 */
data class Ax25ParseResult(
    val frame: Ax25Frame,
    val monitorLine: String,
    val packet: Packet?,
    val parseError: ParseException?,
)

/**
 * Decodes a raw AX.25 UI frame and parses its APRS payload.
 *
 * Throws [Ax25DecodeException] for structural failures (frame too short, wrong control byte).
 * APRS body parse failures are captured in [Ax25ParseResult.parseError] rather than thrown,
 * so callers always receive the [Ax25ParseResult.monitorLine] for logging or igating.
 */
fun parseAx25(
    frame: ByteArray,
    options: ParseOptions = ParseOptions(),
): Ax25ParseResult {
    // Minimum viable frame: dst(7) + src(7) + ctrl(1) + pid(1) = 16 bytes.
    if (frame.size < 16) {
        throw Ax25DecodeException("AX.25 frame too short: ${frame.size} bytes")
    }

    var offset = 0
    val dst = decodeAx25Address(frame, offset)
    offset += 7
    val src = decodeAx25Address(frame, offset)
    offset += 7

    val digipeaters = mutableListOf<Digipeater>()
    if (!src.isLast) {
        var count = 0
        while (offset + 7 <= frame.size && count < 8) {
            val digi = decodeAx25Address(frame, offset)
            offset += 7
            digipeaters.add(Digipeater(call = digi.callString, wasDigied = digi.hBit))
            count++
            if (digi.isLast) break
        }
    }

    if (offset + 2 > frame.size) {
        throw Ax25DecodeException("AX.25 frame truncated: no room for ctrl+pid after address fields")
    }

    val ctrl = frame[offset].toInt() and 0xFF
    offset++ // ctrl
    offset++ // pid (accept any PID value; non-APRS PIDs result in a body parse failure below)

    if (ctrl != 0x03) {
        throw Ax25DecodeException("Not a UI frame: ctrl=0x${ctrl.toString(16).padStart(2, '0')}")
    }

    // APRS info fields are single-line: CR/LF terminate the packet. Truncate
    // at the first CR or LF so downstream parsers (and toMonitorLine) never
    // see stray bytes some senders append to the AX.25 info field.
    val rawBody = frame.sliceArray(offset until frame.size)
    val terminatorIdx = rawBody.indexOfFirst { it == 0x0D.toByte() || it == 0x0A.toByte() }
    val body = if (terminatorIdx >= 0) rawBody.sliceArray(0 until terminatorIdx) else rawBody

    val ax25Frame = Ax25Frame(
        srcCall = src.callString,
        dstCall = dst.callString,
        digipeaters = digipeaters,
        body = body,
    )

    val monitorLine = ax25Frame.toMonitorLine()

    val p = Packet(frame)
    p.srcCallsign = src.callString
    p.dstCallsign = dst.callString
    p.digipeaters = digipeaters
    p.header = ax25Frame.buildHeader()
    p.body = ax25Frame.body

    return try {
        p.parseBody(options)
        Ax25ParseResult(frame = ax25Frame, monitorLine = monitorLine, packet = p, parseError = null)
    } catch (e: ParseException) {
        Ax25ParseResult(frame = ax25Frame, monitorLine = monitorLine, packet = null, parseError = e)
    }
}

/**
 * Encodes structured data as an AX.25 UI frame suitable for APRS transmission.
 *
 * [digipeaters] is the outgoing path without trailing stars,
 * e.g. `listOf("WIDE1-1", "WIDE2-1")`.
 *
 * Throws [IllegalArgumentException] for any invalid AX.25 callsign.
 */
fun encodeAx25Ui(
    srcCall: String,
    dstCall: String,
    digipeaters: List<String>,
    body: ByteArray,
): ByteArray {
    val validSrc = checkAX25Call(srcCall)
        ?: throw IllegalArgumentException("Invalid AX.25 source callsign: $srcCall")
    val validDst = checkAX25Call(dstCall)
        ?: throw IllegalArgumentException("Invalid AX.25 destination callsign: $dstCall")
    val validDigis = digipeaters.map { d ->
        checkAX25Call(d) ?: throw IllegalArgumentException("Invalid AX.25 digipeater callsign: $d")
    }

    val frameSize = (2 + validDigis.size) * 7 + 1 + 1 + body.size
    val out = ByteArray(frameSize)
    var offset = 0

    // Destination: bit 7 (C-bit) = 1 for a command frame; not the last address field.
    offset = encodeAx25Address(out, offset, validDst, isLast = false, bit7 = true)
    // Source: C-bit = 0; isLast only when there are no digipeaters.
    offset = encodeAx25Address(out, offset, validSrc, isLast = validDigis.isEmpty(), bit7 = false)
    for ((i, digi) in validDigis.withIndex()) {
        // H-bit = 0 on outgoing frames (packet has not yet been repeated).
        offset = encodeAx25Address(out, offset, digi, isLast = i == validDigis.lastIndex, bit7 = false)
    }

    out[offset++] = 0x03           // control: UI frame
    out[offset++] = 0xF0.toByte() // PID: no layer 3 (APRS)
    body.copyInto(out, offset)

    return out
}

// ── Internal helpers ──────────────────────────────────────────────────────────

private class DecodedAddress(val callString: String, val hBit: Boolean, val isLast: Boolean)

private fun decodeAx25Address(frame: ByteArray, offset: Int): DecodedAddress {
    val sb = StringBuilder(6)
    for (i in 0 until 6) {
        sb.append(((frame[offset + i].toInt() and 0xFF) ushr 1).toChar())
    }
    val base = sb.toString().trimEnd()

    val ssidByte = frame[offset + 6].toInt() and 0xFF
    val ssid = (ssidByte ushr 1) and 0x0F
    val hBit = (ssidByte and 0x80) != 0
    val isLast = (ssidByte and 0x01) != 0

    val callString = if (ssid == 0) base else "$base-$ssid"
    return DecodedAddress(callString, hBit, isLast)
}

private fun encodeAx25Address(
    out: ByteArray,
    offset: Int,
    call: String,
    isLast: Boolean,
    bit7: Boolean,
): Int {
    val dashIdx = call.indexOf('-')
    val base = if (dashIdx >= 0) call.substring(0, dashIdx) else call
    val ssid = if (dashIdx >= 0) call.substring(dashIdx + 1).toInt() else 0

    val padded = base.padEnd(6, ' ')
    for (i in 0 until 6) {
        out[offset + i] = (padded[i].code shl 1).toByte()
    }

    var ssidByte = ssid shl 1
    if (bit7) ssidByte = ssidByte or 0x80
    if (isLast) ssidByte = ssidByte or 0x01
    out[offset + 6] = ssidByte.toByte()

    return offset + 7
}