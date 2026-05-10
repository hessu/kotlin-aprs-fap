package fi.aprs.fap

internal fun Packet.parseItem(options: ParseOptions) {
    type = PacketType.ITEM

    val bodyStr = bodyAsString().drop(1) // skip ')'

    if (bodyStr.length < 18) fail(ParseError.ITEM_SHORT, "item packet too short")

    var nameEnd = -1
    for (i in 0 until minOf(bodyStr.length, 10)) {
        if (bodyStr[i] == '!' || bodyStr[i] == '_') {
            nameEnd = i
            break
        }
    }

    if (nameEnd < 0) fail(ParseError.ITEM_INV, "item name terminator not found")

    itemName = bodyStr.substring(0, nameEnd)
    alive = (bodyStr[nameEnd] == '!')

    val posBody = bodyStr.substring(nameEnd + 1)

    if (posBody[0].isDigit() || posBody[0] == ' ') {
        parseUncompressedPosition(posBody, options)
    } else {
        parseCompressedPosition(posBody, options)
    }
}
