package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap message_test.go

private val testMessageIDs = listOf("1", "42", "10512", "a", "1Ff84", "F00b4")

class MessageTest {

    @Test
    fun testMessageNormal() {
        for (msgid in testMessageIDs) {
            val packet = "OH7AA-1>APRS,WIDE1-1,WIDE2-2,qAo,OH7AA::N0CALL   :Testing, 1 2 3{$msgid"
            val p = parse(packet)
            assertEquals(PacketType.MESSAGE, p.type, "id=$msgid")
            val msg = assertNotNull(p.message, "id=$msgid")
            assertEquals("N0CALL", msg.destination, "id=$msgid")
            assertEquals(msgid, msg.id, "id=$msgid")
            assertEquals("Testing, 1 2 3", msg.text, "id=$msgid")
            assertEquals("", msg.ackId, "id=$msgid")
        }
    }

    @Test
    fun testMessageReplyAckEmpty() {
        for (msgid in testMessageIDs) {
            val packet = "OH7AA-1>APRS,WIDE1-1,WIDE2-2,qAo,OH7AA::N0CALL   :Testing, 1 2 3{$msgid}"
            val p = parse(packet)
            assertEquals(PacketType.MESSAGE, p.type, "id=$msgid")
            val msg = assertNotNull(p.message, "id=$msgid")
            assertEquals("N0CALL", msg.destination, "id=$msgid")
            assertEquals(msgid, msg.id, "id=$msgid")
            assertEquals("", msg.ackId, "id=$msgid")
        }
    }

    @Test
    fun testMessageReplyAck() {
        for (msgid in testMessageIDs) {
            val packet = "OH7AA-1>APRS,WIDE1-1,WIDE2-2,qAo,OH7AA::N0CALL   :Testing, 1 2 3{$msgid}f001"
            val p = parse(packet)
            assertEquals(PacketType.MESSAGE, p.type, "id=$msgid")
            val msg = assertNotNull(p.message, "id=$msgid")
            assertEquals("N0CALL", msg.destination, "id=$msgid")
            assertEquals(msgid, msg.id, "id=$msgid")
            assertEquals("f001", msg.ackId, "id=$msgid")
        }
    }

    @Test
    fun testMessageAck() {
        for (msgid in testMessageIDs) {
            val packet = "OH7AA-1>APRS,WIDE1-1,WIDE2-2,qAo,OH7AA::N0CALL   :ack$msgid"
            val p = parse(packet)
            assertEquals(PacketType.MESSAGE, p.type, "id=$msgid")
            val msg = assertNotNull(p.message, "id=$msgid")
            assertEquals("N0CALL", msg.destination, "id=$msgid")
            assertEquals(msgid, msg.ackId, "id=$msgid")
        }
    }

    @Test
    fun testMessageReject() {
        for (msgid in testMessageIDs) {
            val packet = "OH7AA-1>APRS,WIDE1-1,WIDE2-2,qAo,OH7AA::N0CALL   :rej$msgid"
            val p = parse(packet)
            assertEquals(PacketType.MESSAGE, p.type, "id=$msgid")
            val msg = assertNotNull(p.message, "id=$msgid")
            assertEquals("N0CALL", msg.destination, "id=$msgid")
            assertEquals(msgid, msg.rejId, "id=$msgid")
        }
    }

    @Test
    fun testMessageNoID() {
        val packet = "OH7AA-1>APRS,WIDE1-1,WIDE2-2,qAo,OH7AA::N0CALL   :Hello world"
        val p = parse(packet)
        assertEquals(PacketType.MESSAGE, p.type)
        val msg = assertNotNull(p.message)
        assertEquals("N0CALL", msg.destination)
        assertEquals("Hello world", msg.text)
        assertEquals("", msg.id)
        assertEquals("", msg.ackId)
        assertEquals("", msg.rejId)
    }

    @Test
    fun testEncodeMessageNormal() {
        for (msgid in testMessageIDs) {
            val msg = Message().also { it.destination = "N0CALL"; it.text = "Testing, 1 2 3"; it.id = msgid }
            val body = encodeMessage(msg)
            val packet = "OH7AA-1>APRS::${body.substring(1)}"
            val p = parse(packet)
            val pm = assertNotNull(p.message)
            assertEquals("N0CALL", pm.destination, "id=$msgid")
            assertEquals("Testing, 1 2 3", pm.text, "id=$msgid")
            assertEquals(msgid, pm.id, "id=$msgid")
            assertEquals("", pm.ackId, "id=$msgid")
        }
    }

    @Test
    fun testEncodeMessageAck() {
        for (msgid in testMessageIDs) {
            val msg = Message().also { it.destination = "N0CALL"; it.ackId = msgid }
            val body = encodeMessage(msg)
            val packet = "OH7AA-1>APRS::${body.substring(1)}"
            val p = parse(packet)
            val pm = assertNotNull(p.message)
            assertEquals("N0CALL", pm.destination, "id=$msgid")
            assertEquals(msgid, pm.ackId, "id=$msgid")
        }
    }

    @Test
    fun testEncodeMessageReject() {
        for (msgid in testMessageIDs) {
            val msg = Message().also { it.destination = "N0CALL"; it.rejId = msgid }
            val body = encodeMessage(msg)
            val packet = "OH7AA-1>APRS::${body.substring(1)}"
            val p = parse(packet)
            val pm = assertNotNull(p.message)
            assertEquals("N0CALL", pm.destination, "id=$msgid")
            assertEquals(msgid, pm.rejId, "id=$msgid")
        }
    }

    @Test
    fun testEncodeMessageReplyAck() {
        data class TC(val id: String, val ackId: String)
        val tests = listOf(TC("1", "abc"), TC("42", "ab"), TC("ab", "cd"), TC("abc", "d"))
        for (tc in tests) {
            val msg = Message().also { it.destination = "N0CALL"; it.text = "Testing, 1 2 3"; it.id = tc.id; it.ackId = tc.ackId }
            val body = encodeMessage(msg)
            val packet = "OH7AA-1>APRS::${body.substring(1)}"
            val p = parse(packet)
            val pm = assertNotNull(p.message)
            assertEquals("N0CALL", pm.destination)
            assertEquals("Testing, 1 2 3", pm.text)
            assertEquals(tc.id, pm.id)
            assertEquals(tc.ackId, pm.ackId)
        }
    }

    @Test
    fun testEncodeMessageNoID() {
        val msg = Message().also { it.destination = "N0CALL"; it.text = "Hello world" }
        val body = encodeMessage(msg)
        val packet = "OH7AA-1>APRS::${body.substring(1)}"
        val p = parse(packet)
        val pm = assertNotNull(p.message)
        assertEquals("N0CALL", pm.destination)
        assertEquals("Hello world", pm.text)
        assertEquals("", pm.id)
    }

    @Test
    fun testEncodeMessageErrors() {
        data class TC(val name: String, val msg: Message, val wantCode: String)

        val tests = listOf(
            TC("empty destination",      Message().also { it.text = "hello" },                                                  ParseError.MSG_NO_DST),
            TC("destination too long",   Message().also { it.destination = "0123456789"; it.text = "hello" },                   ParseError.MSG_DST_LONG),
            TC("message ID too long",    Message().also { it.destination = "N0CALL"; it.text = "hello"; it.id = "123456" },     ParseError.MSG_ID_INV),
            TC("message ID with space",  Message().also { it.destination = "N0CALL"; it.text = "hello"; it.id = "1 2" },        ParseError.MSG_ID_INV),
            TC("message ID special char",Message().also { it.destination = "N0CALL"; it.text = "hello"; it.id = "ab!c" },       ParseError.MSG_ID_INV),
            TC("reply-ack too long",     Message().also { it.destination = "N0CALL"; it.text = "hello"; it.id = "abc"; it.ackId = "de" }, ParseError.MSG_REPLYACK),
            TC("reply-ack just over",    Message().also { it.destination = "N0CALL"; it.text = "hello"; it.id = "ab"; it.ackId = "cde" }, ParseError.MSG_REPLYACK),
            TC("both ack and reject",    Message().also { it.destination = "N0CALL"; it.ackId = "1"; it.rejId = "2" },          ParseError.MSG_ACK_REJ),
            TC("content with reject",    Message().also { it.destination = "N0CALL"; it.text = "hello"; it.rejId = "1" },       ParseError.MSG_ACK_REJ),
            TC("LF in text",             Message().also { it.destination = "N0CALL"; it.text = "hello\nworld" },                ParseError.MSG_CR),
            TC("CR in text",             Message().also { it.destination = "N0CALL"; it.text = "hello\rworld" },                ParseError.MSG_CR),
            TC("LF in destination",      Message().also { it.destination = "N0\nCALL"; it.text = "hello" },                     ParseError.MSG_CR),
            TC("CR in ack ID",           Message().also { it.destination = "N0CALL"; it.ackId = "1\r" },                        ParseError.MSG_CR),
        )

        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { encodeMessage(tc.msg) }
            assertEquals(tc.wantCode, ex.code, tc.name)
        }
    }

    @Test
    fun testMessageErrors() {
        data class TC(val name: String, val packet: String, val wantCode: String)

        val tests = listOf(
            TC("too short",                    "OH7AA-1>APRS::N0CALL  ",           ParseError.MSG_SHORT),
            TC("missing colon after addressee","OH7AA-1>APRS::N0CALL  XHello world", ParseError.MSG_INV),
        )

        for (tc in tests) {
            val ex = assertFailsWith<ParseException>(tc.name) { parse(tc.packet) }
            assertEquals(tc.wantCode, ex.code, tc.name)
        }
    }
}
