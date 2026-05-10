package fi.aprs.fap

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.time.Duration
import java.time.Instant

/** A connection to an APRS-IS server. */
class AprsIsConnection(
    private val socket: Socket,
    private val reader: BufferedReader,
    private val writer: PrintWriter,
) {
    /** Reads a single line from the connection, stripping trailing CR/LF. */
    fun readLine(timeout: Duration): String {
        socket.soTimeout = timeout.toMillis().toInt().coerceAtLeast(1)
        return reader.readLine() ?: throw java.io.EOFException("connection closed")
    }

    /** Reads lines until a non-comment line (not starting with '#') is found. */
    fun readPacket(timeout: Duration): String {
        while (true) {
            val line = readLine(timeout)
            if (!line.startsWith("#")) return line
        }
    }

    /** Writes a line followed by CR/LF to the connection. */
    fun sendLine(line: String) {
        writer.print("$line\r\n")
        writer.flush()
    }

    /** Closes the underlying TCP connection. */
    fun close() = socket.close()
}

/**
 * Connects to an APRS-IS server at [addr] (host:port), sends the login line,
 * and waits for a `# logresp` reply. An optional [filter] string can be provided.
 */
fun dial(addr: String, callsign: String, passcode: String, appName: String, appVer: String, filter: String = ""): AprsIsConnection {
    val colonIdx = addr.lastIndexOf(':')
    val host = if (colonIdx >= 0) addr.substring(0, colonIdx) else addr
    val port = if (colonIdx >= 0) addr.substring(colonIdx + 1).toInt() else 10152

    val socket = Socket(host, port)
    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
    val writer = PrintWriter(socket.getOutputStream())

    val conn = AprsIsConnection(socket, reader, writer)

    var login = "user $callsign pass $passcode vers $appName $appVer"
    if (filter.isNotEmpty()) login += " filter $filter"

    conn.sendLine(login)

    val deadline = Instant.now().plus(Duration.ofSeconds(5))
    while (Instant.now().isBefore(deadline)) {
        val remaining = Duration.between(Instant.now(), deadline)
        val line = try {
            conn.readLine(remaining)
        } catch (e: Exception) {
            socket.close()
            throw Exception("failed to read login response: ${e.message}", e)
        }
        if (line.startsWith("# logresp")) return conn
    }

    socket.close()
    throw Exception("login timed out waiting for logresp")
}

/**
 * Calculates the APRS-IS passcode for a given callsign.
 * The SSID suffix is stripped and the callsign is uppercased before computing the hash.
 */
fun aprsPasscode(callsign: String): Short {
    var cs = callsign
    val dashIdx = cs.indexOf('-')
    if (dashIdx >= 0) cs = cs.substring(0, dashIdx)
    cs = cs.uppercase()

    var hash = 0x73E2
    var i = 0
    while (i < cs.length) {
        hash = hash xor (cs[i].code shl 8)
        if (i + 1 < cs.length) hash = hash xor cs[i + 1].code
        hash = hash and 0xFFFF
        i += 2
    }

    return (hash and 0x7FFF).toShort()
}
