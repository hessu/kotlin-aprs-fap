package fi.aprs.fap

/** Data from an APRS message, ack, or reject packet. */
class Message {
    /** Destination callsign (addressee). */
    var destination: String = ""

    /** Message text body. */
    var text: String = ""

    /** Message ID (1–5 alphanumeric characters). */
    var id: String = ""

    /** Acknowledgment message ID. */
    var ackId: String = ""

    /** Reject message ID. */
    var rejId: String = ""
}

// ── Parser ────────────────────────────────────────────────────────────────────

internal fun Packet.parseMessage(options: ParseOptions) {
    type = PacketType.MESSAGE

    val bodyStr = bodyAsString().drop(1) // skip ':'

    if (bodyStr.length < 11) fail(ParseError.MSG_SHORT, "message packet too short")

    if (bodyStr[9] != ':') fail(ParseError.MSG_INV, "message addressee field malformed")

    val msg = Message()
    message = msg

    msg.destination = bodyStr.substring(0, 9).trim()
    val msgBody = bodyStr.substring(10)

    if (msgBody.startsWith("ack")) {
        msg.ackId = msgBody.substring(3)
        return
    }

    if (msgBody.startsWith("rej")) {
        msg.rejId = msgBody.substring(3)
        return
    }

    val braceIdx = msgBody.lastIndexOf('{')
    if (braceIdx >= 0) {
        msg.text = msgBody.substring(0, braceIdx)
        val idPart = msgBody.substring(braceIdx + 1)
        val closeIdx = idPart.indexOf('}')
        if (closeIdx >= 0) {
            msg.id = idPart.substring(0, closeIdx)
            msg.ackId = idPart.substring(closeIdx + 1)
        } else {
            msg.id = idPart
        }
    } else {
        msg.text = msgBody
    }

    if (isTelemetryMessage(msg.text)) {
        type = PacketType.TELEMETRY_MESSAGE
    }
}

private fun isTelemetryMessage(text: String): Boolean {
    if (text.length < 5 || text[4] != '.') return false
    val prefix = text.substring(0, 4).uppercase()
    return prefix == "PARM" || prefix == "UNIT" || prefix == "EQNS" || prefix == "BITS"
}

// ── Encoder ───────────────────────────────────────────────────────────────────

/**
 * Encodes a [Message] into an APRS message body string (starting with ':').
 * Throws [ParseException] if the message fields are invalid.
 */
fun encodeMessage(msg: Message): String {
    if (msg.destination.isEmpty()) {
        throw ParseException(ParseError.MSG_NO_DST, "message destination is required")
    }
    if (msg.destination.length > 9) {
        throw ParseException(ParseError.MSG_DST_LONG, "message destination too long (max 9 characters)")
    }
    if (msg.ackId.isNotEmpty() && msg.rejId.isNotEmpty()) {
        throw ParseException(ParseError.MSG_ACK_REJ, "message cannot have both ack and reject")
    }
    if (msg.text.isNotEmpty() && msg.rejId.isNotEmpty()) {
        throw ParseException(ParseError.MSG_ACK_REJ, "message cannot have both content and reject")
    }
    if (containsCRLF(msg.destination) || containsCRLF(msg.text) ||
        containsCRLF(msg.id) || containsCRLF(msg.ackId) || containsCRLF(msg.rejId)
    ) {
        throw ParseException(ParseError.MSG_CR, "message fields must not contain CR or LF")
    }
    if (msg.id.isNotEmpty() && !isValidMsgID(msg.id)) {
        throw ParseException(ParseError.MSG_ID_INV, "message ID must be 1-5 alphanumeric characters")
    }

    val addressee = msg.destination + " ".repeat(9 - msg.destination.length)

    val content =
        when {
            msg.ackId.isNotEmpty() && msg.id.isEmpty() -> "ack" + msg.ackId
            msg.rejId.isNotEmpty() -> "rej" + msg.rejId
            else -> {
                var c = msg.text
                if (msg.id.isNotEmpty()) {
                    if (msg.ackId.isNotEmpty()) {
                        if (msg.id.length + 1 + msg.ackId.length > 5) {
                            throw ParseException(ParseError.MSG_REPLYACK, "reply-ack too long to embed, send ack separately")
                        }
                        c += "{" + msg.id + "}" + msg.ackId
                    } else {
                        c += "{" + msg.id
                    }
                }
                c
            }
        }

    return ":$addressee:$content"
}

private fun containsCRLF(s: String): Boolean = s.contains('\r') || s.contains('\n')

private fun isValidMsgID(id: String): Boolean {
    if (id.isEmpty() || id.length > 5) return false
    return id.all { it.isDigit() || it in 'a'..'z' || it in 'A'..'Z' }
}
