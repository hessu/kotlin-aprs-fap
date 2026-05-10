package fi.aprs.fap

/** Telemetry data from a T# telemetry packet or embedded Mic-E telemetry. */
class Telemetry {
    /** Sequence number. */
    var seq: Int = 0
    /** Analog channel values. Null elements represent undefined channels. */
    var vals: List<Double?> = emptyList()
    /** Digital bits as an 8-character binary string (e.g. "10110000"), LSB first. */
    var bits: String = ""
}

// ── T# telemetry parser ───────────────────────────────────────────────────────

internal fun Packet.parseTelemetry(options: ParseOptions) {
    type = PacketType.TELEMETRY

    val body = bodyAsString().drop(2) // skip 'T#'

    val parts = body.split(",", limit = 8)
    if (parts.size < 2) fail(ParseError.TLM_INV, "telemetry packet has too few fields")

    val tlm = Telemetry()

    tlm.seq = parts[0].toIntOrNull() ?: fail(ParseError.TLM_INV, "invalid telemetry sequence number: ${parts[0]}")

    val vals = arrayOfNulls<Double>(5)
    outer@ for (i in 1 until minOf(parts.size, 6)) {
        val field = parts[i].trim()
        if (field.isEmpty()) continue

        if (!isNumericValue(field)) {
            if (field == "-" || field.endsWith(".")) {
                fail(ParseError.TLM_INV, "invalid telemetry value: $field")
            }
            break@outer
        }

        val v = field.toDoubleOrNull() ?: break@outer
        vals[i - 1] = v
    }
    tlm.vals = vals.toList()

    if (parts.size >= 7) {
        val bitsField = parts[6].trim()
        if (bitsField.length >= 8) {
            val bits = bitsField.substring(0, 8)
            if (bits.all { it == '0' || it == '1' }) {
                tlm.bits = bits
            }
        }
    }

    telemetryData = tlm
}

// ── Numeric value validator ───────────────────────────────────────────────────

internal fun isNumericValue(s: String): Boolean {
    if (s.isEmpty()) return false
    var i = 0
    if (s[i] == '-') i++
    while (i < s.length && s[i].isDigit()) i++
    if (i == s.length) {
        return i > 0 && s[i - 1].isDigit()
    }
    if (s[i] != '.') return false
    i++
    val start = i
    while (i < s.length && s[i].isDigit()) i++
    return i == s.length && i > start
}
