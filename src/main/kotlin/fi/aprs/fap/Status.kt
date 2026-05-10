package fi.aprs.fap

internal fun Packet.parseStatus(options: ParseOptions) {
    type = PacketType.STATUS

    val bodyStr = bodyAsString().drop(1) // skip '>'

    var rest = bodyStr
    if (bodyStr.length >= 7) {
        val indicator = bodyStr[6]
        if (indicator == 'z' || indicator == '/') {
            val ts = parseTimestamp(bodyStr.substring(0, 7))
            if (ts == null) {
                warn(ParseError.TIMESTAMP_INV, "invalid timestamp: ${bodyStr.substring(0, 7)}")
            } else {
                timestamp = ts
            }
            rest = bodyStr.drop(7)
        }
    }

    status = rest
}

internal fun Packet.parseCapabilities(options: ParseOptions) {
    type = PacketType.CAPABILITIES
    val caps = mutableMapOf<String, String>()

    val bodyStr = bodyAsString().drop(1) // skip '<'
    for (part in bodyStr.split(",")) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        val eqIdx = trimmed.indexOf('=')
        if (eqIdx >= 0) {
            caps[trimmed.substring(0, eqIdx)] = trimmed.substring(eqIdx + 1)
        } else {
            caps[trimmed] = ""
        }
    }

    capabilities = caps
}
