package fi.aprs.fap

internal fun Packet.parseObject(options: ParseOptions) {
    type = PacketType.OBJECT

    val bodyStr = bodyAsString().drop(1) // skip ';'

    if (bodyStr.length < 31) fail(ParseError.OBJ_SHORT, "object packet too short")

    objectName = bodyStr.substring(0, 9)

    when (bodyStr[9]) {
        '*' -> alive = true
        '_' -> alive = false
        else -> fail(ParseError.OBJ_INV, "invalid object alive/killed indicator: ${bodyStr[9]}")
    }

    val ts = parseTimestamp(bodyStr.substring(10, 17))
    if (ts == null) {
        warn(ParseError.TIMESTAMP_INV, "invalid object timestamp: ${bodyStr.substring(10, 17)}")
    } else {
        timestamp = ts
    }

    val posBody = bodyStr.drop(17)

    if (posBody[0].isDigit() || posBody[0] == ' ') {
        parseUncompressedPosition(posBody, options)
    } else {
        parseCompressedPosition(posBody, options)
    }
}
