package fi.aprs.fap

import kotlin.test.*

// Tests ported from go-aprs-fap message_binary_clean_test.go

private fun buildBinaryMessagePacket(content: String): Pair<String, String> {
    val destination = "OH7LZB   "
    val messageid = "42"
    val message = content.replace("{", "").replace("~", "").replace("|", "")
    val packet = "OH7AA-1>APRS,WIDE1-1,WIDE2-2,qAo,OH7AA::$destination:$message{$messageid"
    return Pair(packet, message)
}

private fun testBinaryMessage(
    setName: String,
    content: String,
) {
    val (packet, message) = buildBinaryMessagePacket(content)
    val p = parse(packet)
    assertEquals(PacketType.MESSAGE, p.type, "$setName: wrong packet type")
    val msg = assertNotNull(p.message, "$setName: message is nil")
    assertEquals("OH7LZB", msg.destination, "$setName: wrong destination")
    assertEquals("42", msg.id, "$setName: wrong message id")
    assertEquals(message, msg.text, "$setName: wrong message")
}

class MessageBinaryCleanTest {
    @Test
    fun testBinaryCleanASCII() {
        val sb = StringBuilder()
        for (i in 32..126) sb.append(i.toChar())
        testBinaryMessage("msg set ascii", sb.toString())
    }

    @Test
    fun testBinaryCleanSet1() {
        val sb = StringBuilder()
        for (i in 32 until 32 + 67) sb.append(i.toChar())
        testBinaryMessage("msg set 1", sb.toString())
    }

    @Test
    fun testBinaryCleanSet2() {
        val sb = StringBuilder()
        for (i in 32 + 67 until 32 + 67 + 67) {
            if (i != 127) sb.append(i.toChar())
        }
        testBinaryMessage("msg set 2", sb.toString())
    }

    @Test
    fun testBinaryCleanSet3() {
        val sb = StringBuilder()
        for (i in 32 + 67 + 67 until 32 + 67 + 67 + 67) sb.append(i.toChar())
        testBinaryMessage("msg set 3", sb.toString())
    }

    @Test
    fun testBinaryCleanSet4() {
        val sb = StringBuilder()
        for (i in 32 + 67 + 67 + 67 until 255) sb.append(i.toChar())
        testBinaryMessage("msg set 4", sb.toString())
    }

    @Test
    fun testBinaryCleanIndividualChars() {
        for (i in 32 until 255) {
            if (i == 127) continue
            val ch = i.toChar().toString()
            val content = "char: $ch"
            testBinaryMessage("binary char $i", content)
        }
    }
}
