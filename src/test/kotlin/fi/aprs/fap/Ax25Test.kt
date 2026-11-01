package fi.aprs.fap

import kotlin.test.*

class Ax25Test {
    // ── encodeAx25Ui ─────────────────────────────────────────────────────────

    @Test
    fun testEncodeSimpleFrame() {
        val body = ">status".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB", "APRS", emptyList(), body)

        // dst(7) + src(7) + ctrl(1) + pid(1) + body(7) = 23
        assertEquals(23, frame.size)

        // Destination "APRS  ": C-bit set, ssid=0, not last
        assertEquals('A'.code shl 1, frame[0].toInt() and 0xFF)
        assertEquals('P'.code shl 1, frame[1].toInt() and 0xFF)
        assertEquals('R'.code shl 1, frame[2].toInt() and 0xFF)
        assertEquals('S'.code shl 1, frame[3].toInt() and 0xFF)
        assertEquals(' '.code shl 1, frame[4].toInt() and 0xFF)
        assertEquals(' '.code shl 1, frame[5].toInt() and 0xFF)
        assertEquals(0x80, frame[6].toInt() and 0xFF) // C=1, ssid=0, isLast=0

        // Source "OH7LZB": C-bit clear, ssid=0, isLast=1 (no digipeaters follow)
        assertEquals('O'.code shl 1, frame[7].toInt() and 0xFF)
        assertEquals('H'.code shl 1, frame[8].toInt() and 0xFF)
        assertEquals('7'.code shl 1, frame[9].toInt() and 0xFF)
        assertEquals('L'.code shl 1, frame[10].toInt() and 0xFF)
        assertEquals('Z'.code shl 1, frame[11].toInt() and 0xFF)
        assertEquals('B'.code shl 1, frame[12].toInt() and 0xFF)
        assertEquals(0x01, frame[13].toInt() and 0xFF) // C=0, ssid=0, isLast=1

        // ctrl + pid
        assertEquals(0x03, frame[14].toInt() and 0xFF)
        assertEquals(0xF0, frame[15].toInt() and 0xFF)

        // body bytes
        assertContentEquals(body, frame.sliceArray(16 until frame.size))
    }

    @Test
    fun testEncodeWithSsidAndDigipeaters() {
        val body = "!pos".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB-9", "APFII0", listOf("WIDE1-1", "WIDE2-1"), body)

        // dst(7) + src(7) + digi1(7) + digi2(7) + ctrl(1) + pid(1) + body(4) = 34
        assertEquals(34, frame.size)

        // Destination SSID: C=1, ssid=0, not last
        assertEquals(0x80, frame[6].toInt() and 0xFF)

        // Source SSID: C=0, ssid=9, not last (digipeaters follow)
        assertEquals(9 shl 1, frame[13].toInt() and 0xFF)

        // Digi 1 (WIDE1-1): H=0, ssid=1, not last
        assertEquals(1 shl 1, frame[20].toInt() and 0xFF)

        // Digi 2 (WIDE2-1): H=0, ssid=1, last
        assertEquals((1 shl 1) or 1, frame[27].toInt() and 0xFF)

        // ctrl + pid
        assertEquals(0x03, frame[28].toInt() and 0xFF)
        assertEquals(0xF0, frame[29].toInt() and 0xFF)
    }

    @Test
    fun testEncodeInvalidSrcCall() {
        assertFailsWith<IllegalArgumentException> {
            encodeAx25Ui("TOOLONGCALL", "APRS", emptyList(), ByteArray(0))
        }
    }

    @Test
    fun testEncodeInvalidDstCall() {
        assertFailsWith<IllegalArgumentException> {
            encodeAx25Ui("OH7LZB", "TOOLONGCALL", emptyList(), ByteArray(0))
        }
    }

    @Test
    fun testEncodeInvalidDigiCall() {
        assertFailsWith<IllegalArgumentException> {
            encodeAx25Ui("OH7LZB", "APRS", listOf("TOOLONGCALL"), ByteArray(0))
        }
    }

    @Test
    fun testEncodeEmptyBody() {
        val frame = encodeAx25Ui("OH7LZB", "APRS", emptyList(), ByteArray(0))
        // dst(7) + src(7) + ctrl(1) + pid(1) = 16
        assertEquals(16, frame.size)
    }

    // ── parseAx25 ─────────────────────────────────────────────────────────────

    @Test
    fun testDecodeSimpleFrame() {
        val body = ">status".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB", "APRS", emptyList(), body)
        val result = parseAx25(frame)

        assertEquals("OH7LZB", result.frame.srcCall)
        assertEquals("APRS", result.frame.dstCall)
        assertEquals(0, result.frame.digipeaters.size)
        assertContentEquals(body, result.frame.body)
        assertEquals("OH7LZB>APRS:>status", result.monitorLine)
        assertNotNull(result.packet)
        assertNull(result.parseError)
    }

    @Test
    fun testDecodeWithSsid() {
        val body = ">ssid test".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB-9", "APRS", emptyList(), body)
        val result = parseAx25(frame)

        assertEquals("OH7LZB-9", result.frame.srcCall)
        assertEquals("APRS", result.frame.dstCall)
        assertEquals(0, result.frame.digipeaters.size)
    }

    @Test
    fun testDecodeWithDigipeaters() {
        val body = ">digi test".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB", "APRS", listOf("WIDE1-1", "WIDE2-1"), body)
        val result = parseAx25(frame)

        assertEquals("OH7LZB", result.frame.srcCall)
        assertEquals(2, result.frame.digipeaters.size)
        assertEquals("WIDE1-1", result.frame.digipeaters[0].call)
        assertFalse(result.frame.digipeaters[0].wasDigied)
        assertEquals("WIDE2-1", result.frame.digipeaters[1].call)
        assertFalse(result.frame.digipeaters[1].wasDigied)
        assertEquals("OH7LZB>APRS,WIDE1-1,WIDE2-1:>digi test", result.monitorLine)
    }

    @Test
    fun testDecodeHBitOnDigipeater() {
        // Build a frame, then set H-bit (bit 7) on the first digipeater's SSID byte.
        // Frame layout: dst(0-6) src(7-13) digi1(14-20) digi2(21-27) ctrl pid body
        // Digi 1 SSID byte is at offset 20.
        val body = ">hbit test".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB", "APRS", listOf("OH7AA", "WIDE2-1"), body).toMutableList()
        frame[20] = (frame[20].toInt() or 0x80).toByte() // set H-bit on digi 1
        val result = parseAx25(frame.toByteArray())

        assertTrue(result.frame.digipeaters[0].wasDigied, "first digi should have H-bit set")
        assertFalse(result.frame.digipeaters[1].wasDigied, "second digi should not have H-bit set")
        assertEquals("OH7LZB>APRS,OH7AA*,WIDE2-1:>hbit test", result.monitorLine)
    }

    @Test
    fun testDecodeAprsBodyParsed() {
        val body = "!6028.51N/02505.68E#PHG7220/testing".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB", "APRS", emptyList(), body)
        val result = parseAx25(frame)

        assertNotNull(result.packet)
        assertNull(result.parseError)
        assertEquals("OH7LZB", result.packet!!.srcCallsign)
        assertEquals("APRS", result.packet.dstCallsign)
        assertNotNull(result.packet.latitude)
        assertNotNull(result.packet.longitude)
    }

    @Test
    fun testDecodeAprsBodyParseFailure() {
        // Empty information field — parseBody() will throw PACKET_NO_BODY.
        val frame = encodeAx25Ui("OH7LZB", "APRS", emptyList(), ByteArray(0))
        val result = parseAx25(frame)

        assertNull(result.packet)
        assertNotNull(result.parseError)
        assertEquals(ParseError.PACKET_NO_BODY, result.parseError!!.code)
        // monitorLine is always available even on parse failure
        assertEquals("OH7LZB>APRS:", result.monitorLine)
    }

    // ── Structural error cases ────────────────────────────────────────────────

    @Test
    fun testDecodeFrameTooShort() {
        assertFailsWith<Ax25DecodeException> {
            parseAx25(ByteArray(15)) // minimum is 16
        }
    }

    @Test
    fun testDecodeEmptyFrame() {
        assertFailsWith<Ax25DecodeException> {
            parseAx25(ByteArray(0))
        }
    }

    @Test
    fun testDecodeNotUiFrame() {
        val body = ">status".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB", "APRS", emptyList(), body).toMutableList()
        // ctrl byte is at offset 14 for a frame with no digipeaters; set to non-UI value
        frame[14] = 0x00
        assertFailsWith<Ax25DecodeException> {
            parseAx25(frame.toByteArray())
        }
    }

    @Test
    fun testDecodeNotAprsPid() {
        val body = ">status".toByteArray(Charsets.ISO_8859_1)
        val frame = encodeAx25Ui("OH7LZB", "APRS", emptyList(), body).toMutableList()
        // PID byte is at offset 15 for a frame with no digipeaters; 0xCC is IP, not APRS
        frame[15] = 0xCC.toByte()
        assertFailsWith<Ax25DecodeException> {
            parseAx25(frame.toByteArray())
        }
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun testRoundTrip() {
        val srcCall = "OH7LZB-9"
        val dstCall = "APFII0"
        val digis = listOf("OH7AA-1", "WIDE2-1")
        val body = "!6028.51N/02505.68E#testing".toByteArray(Charsets.ISO_8859_1)

        val encoded = encodeAx25Ui(srcCall, dstCall, digis, body)
        val result = parseAx25(encoded)

        assertEquals(srcCall, result.frame.srcCall)
        assertEquals(dstCall, result.frame.dstCall)
        assertEquals(2, result.frame.digipeaters.size)
        assertEquals("OH7AA-1", result.frame.digipeaters[0].call)
        assertFalse(result.frame.digipeaters[0].wasDigied) // outgoing: H-bit = 0
        assertEquals("WIDE2-1", result.frame.digipeaters[1].call)
        assertFalse(result.frame.digipeaters[1].wasDigied)
        assertContentEquals(body, result.frame.body)
        assertEquals("$srcCall>$dstCall,OH7AA-1,WIDE2-1:${body.toString(Charsets.ISO_8859_1)}", result.monitorLine)
    }

    @Test
    fun testRoundTripNoDigi() {
        val body = ">status text".toByteArray(Charsets.ISO_8859_1)
        val encoded = encodeAx25Ui("N0CALL", "APRS", emptyList(), body)
        val result = parseAx25(encoded)

        assertEquals("N0CALL", result.frame.srcCall)
        assertEquals("APRS", result.frame.dstCall)
        assertEquals(0, result.frame.digipeaters.size)
        assertContentEquals(body, result.frame.body)
    }

    // ── rewriteAx25Path ───────────────────────────────────────────────────────

    @Test
    fun testRewritePathSubstitutesDigiWithHBit() {
        val body = "!6028.51N/02505.68E>".toByteArray(Charsets.ISO_8859_1)
        val original = encodeAx25Ui("OH7LZB-9", "APFII0", listOf("WIDE1-1", "WIDE2-1"), body)

        val out = rewriteAx25Path(original, listOf(Digipeater("OH7RDA-1", true), Digipeater("WIDE2-1", false)))

        assertEquals(original.size, out.size)
        // Digi 1 SSID byte at offset 20: H=1, ssid=1, not last
        assertEquals(0x80 or (1 shl 1), out[20].toInt() and 0xFF)
        // Digi 2 SSID byte at offset 27: H=0, ssid=1, last
        assertEquals((1 shl 1) or 1, out[27].toInt() and 0xFF)

        val result = parseAx25(out)
        assertEquals("OH7LZB-9>APFII0,OH7RDA-1*,WIDE2-1:!6028.51N/02505.68E>", result.monitorLine)
        assertTrue(result.frame.digipeaters[0].wasDigied)
        assertFalse(result.frame.digipeaters[1].wasDigied)
    }

    @Test
    fun testRewritePathInsertsDigiAndGrowsFrame() {
        val body = ">test".toByteArray(Charsets.ISO_8859_1)
        val original = encodeAx25Ui("OH7LZB", "APRS", listOf("WIDE2-2"), body)

        val out = rewriteAx25Path(original, listOf(Digipeater("OH7RDA", true), Digipeater("WIDE2-1", false)))

        assertEquals(original.size + 7, out.size)
        assertEquals("OH7LZB>APRS,OH7RDA*,WIDE2-1:>test", parseAx25(out).monitorLine)
    }

    @Test
    fun testRewritePathPreservesInfoFieldBytesBeyondCr() {
        // parseAx25 truncates the body at CR, but a digipeater must repeat the frame
        // with the information field intact.
        val body = ">status\r\u0000trailing".toByteArray(Charsets.ISO_8859_1)
        val original = encodeAx25Ui("OH7LZB", "APRS", listOf("WIDE1-1"), body)

        val out = rewriteAx25Path(original, listOf(Digipeater("OH7RDA", true)))

        // Everything after the address field (ctrl, pid, info) is identical.
        assertContentEquals(original.sliceArray(21 until original.size), out.sliceArray(21 until out.size))
        assertEquals(0x03, out[21].toInt() and 0xFF)
        assertEquals(0xF0, out[22].toInt() and 0xFF)
    }

    @Test
    fun testRewritePathPreservesDstAndSrcBytes() {
        val body = ">x".toByteArray(Charsets.ISO_8859_1)
        val original = encodeAx25Ui("OH7LZB-9", "APFII0", listOf("WIDE1-1"), body).toMutableList()
        // Set the reserved bits (0x60) on dst and src SSID bytes, like real TNCs do.
        original[6] = (original[6].toInt() or 0x60).toByte()
        original[13] = (original[13].toInt() or 0x60).toByte()

        val out = rewriteAx25Path(original.toByteArray(), listOf(Digipeater("OH7RDA", true)))

        assertContentEquals(original.toByteArray().sliceArray(0 until 14), out.sliceArray(0 until 14))
    }

    @Test
    fun testRewritePathEmptyToPathSetsSrcNotLast() {
        val body = ">x".toByteArray(Charsets.ISO_8859_1)
        val original = encodeAx25Ui("OH7LZB", "APRS", emptyList(), body)
        assertEquals(0x01, original[13].toInt() and 0x01)

        val out = rewriteAx25Path(original, listOf(Digipeater("OH7RDA", true)))

        assertEquals(0x00, out[13].toInt() and 0x01)
        assertEquals(original.size + 7, out.size)
        assertEquals("OH7LZB>APRS,OH7RDA*:>x", parseAx25(out).monitorLine)
    }

    @Test
    fun testRewritePathToEmptySetsSrcLast() {
        val body = ">x".toByteArray(Charsets.ISO_8859_1)
        val original = encodeAx25Ui("OH7LZB", "APRS", listOf("WIDE1-1", "WIDE2-1"), body)

        val out = rewriteAx25Path(original, emptyList())

        assertEquals(0x01, out[13].toInt() and 0x01)
        assertEquals(original.size - 14, out.size)
        assertEquals("OH7LZB>APRS:>x", parseAx25(out).monitorLine)
    }

    @Test
    fun testRewritePathRejectsTooManyDigipeaters() {
        val original = encodeAx25Ui("OH7LZB", "APRS", listOf("WIDE1-1"), ByteArray(0))
        val nine = (1..9).map { Digipeater("DIGI$it", true) }
        assertFailsWith<IllegalArgumentException> { rewriteAx25Path(original, nine) }
    }

    @Test
    fun testRewritePathRejectsInvalidCallsign() {
        val original = encodeAx25Ui("OH7LZB", "APRS", listOf("WIDE1-1"), ByteArray(0))
        assertFailsWith<IllegalArgumentException> {
            rewriteAx25Path(original, listOf(Digipeater("TOOLONGCALL", true)))
        }
    }

    @Test
    fun testRewritePathRejectsShortFrame() {
        assertFailsWith<Ax25DecodeException> { rewriteAx25Path(ByteArray(15), emptyList()) }
    }

    @Test
    fun testRewritePathRejectsTruncatedAddressField() {
        // src says "more addresses follow" but the frame ends right after it.
        val frame = encodeAx25Ui("OH7LZB", "APRS", emptyList(), ByteArray(0)).toMutableList()
        frame[13] = (frame[13].toInt() and 0xFE).toByte()
        assertFailsWith<Ax25DecodeException> { rewriteAx25Path(frame.toByteArray(), emptyList()) }
    }
}
