package fi.aprs.fap

import java.io.PrintWriter
import java.net.ServerSocket
import java.time.Duration
import kotlin.test.*

// Tests ported from go-aprs-fap aprsis_test.go

class AprsIsTest {

    @Test
    fun testDial() {
        val server = ServerSocket(0)
        val addr = "127.0.0.1:${server.localPort}"

        val packets = listOf(
            "N0CALL-1>APRS:;LEADER   *092345z4903.50N/07201.75W>088/036",
            "N0CALL-1>APRS:)AID #2!4903.50N/07201.75WA",
            "# server comment",
            "N0CALL-2>TQ4W2V,WIDE2-1,qAo,IGATE:`c51!f?>/]\"3x}=",
        )

        val serverThread = Thread {
            val conn = server.accept()
            val reader = conn.getInputStream().bufferedReader(Charsets.US_ASCII)
            val writer = PrintWriter(conn.getOutputStream())

            val login = reader.readLine() ?: return@Thread
            assert(login.startsWith("user N0CALL pass 13023")) { "unexpected login: $login" }

            writer.print("# logresp N0CALL verified, server T2TEST\r\n")
            writer.flush()

            for (pkt in packets) {
                writer.print("$pkt\r\n")
                writer.flush()
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val conn = dial(addr, "N0CALL", "13023", "kotlintest", "1.0")
        try {
            for (pkt in packets) {
                if (pkt.startsWith("#")) continue

                val got = conn.readPacket(Duration.ofSeconds(2))
                assertEquals(pkt, got)

                val p = parse(got)
                assertTrue(p.srcCallsign.isNotEmpty(), "empty source callsign for: $got")
            }
        } finally {
            conn.close()
            server.close()
        }

        serverThread.join(3000)
    }

    @Test
    fun testDialWithFilter() {
        val server = ServerSocket(0)
        val addr = "127.0.0.1:${server.localPort}"

        val serverThread = Thread {
            val conn = server.accept()
            val reader = conn.getInputStream().bufferedReader(Charsets.US_ASCII)
            val writer = PrintWriter(conn.getOutputStream())

            val login = reader.readLine() ?: return@Thread
            assert(login.contains("filter r/60.0/25.0/100")) { "filter not in login: $login" }

            writer.print("# logresp N0CALL verified, server T2TEST\r\n")
            writer.flush()
        }
        serverThread.isDaemon = true
        serverThread.start()

        val conn = dial(addr, "N0CALL", "13023", "kotlintest", "1.0", "r/60.0/25.0/100")
        conn.close()
        server.close()

        serverThread.join(3000)
    }

    @Test
    fun testAprsPasscode() {
        data class TC(val callsign: String, val expected: Short)
        val tests = listOf(
            TC("N0CALL", 13023),
            TC("N1CALL", 13022),
            TC("N0CALL-9", 13023),
            TC("n0call", 13023),
        )
        for (tc in tests) {
            assertEquals(tc.expected, aprsPasscode(tc.callsign), "aprsPasscode(${tc.callsign})")
        }
    }
}
