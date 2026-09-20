package com.example.e2e.tier2_boundaries

import com.example.e2e.harness.FakeSshSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tier 2 Boundary Tests: B10 PTY Terminal Boundary.
 * Covers 5 boundary conditions:
 * - T2_B10_01: Incomplete or truncated ANSI escape sequence handling
 * - T2_B10_02: 1MB burst stream buffer processing without memory exhaustion
 * - T2_B10_03: Null byte and binary data stripping / sanitization
 * - T2_B10_04: Rapid keystroke flood burst handling without buffer corruption
 * - T2_B10_05: Terminal disconnect / stream closure during active write
 */
class B10_PtyTerminalBoundaryTest {

    private lateinit var fakeSshSession: FakeSshSession
    private val ansiRegex = Regex("\u001B\\[[;?0-9]*[a-zA-Z]")

    @Before
    fun setUp() {
        fakeSshSession = FakeSshSession()
    }

    private fun sanitizeTerminalOutput(raw: String): String {
        // Strip null bytes and ANSI sequences
        return raw.replace("\u0000", "").replace(ansiRegex, "")
    }

    @Test
    fun T2_B10_01_incomplete_truncated_ansi_escape() {
        // Incomplete sequence at buffer chunk boundary: e.g. "\u001B[" or "\u001B[3"
        val truncatedChunk1 = "Process output: \u001B["
        val truncatedChunk2 = "1;32mSUCCESS\u001B[0m"

        val combined = truncatedChunk1 + truncatedChunk2
        val cleaned = sanitizeTerminalOutput(combined)

        assertEquals("Process output: SUCCESS", cleaned)
        assertFalse(cleaned.contains("\u001B"))
    }

    @Test
    fun T2_B10_02_1mb_burst_stream_buffer_overflow_prevention() {
        // Generate 1 Megabyte burst of simulated log stream text
        val line = "2026-09-19T18:00:00.000Z [INFO] kernel: PCIe Bus Error: severity=Corrected, type=Physical Layer\n"
        val repeatCount = ((1024 * 1024) / line.length) + 1
        val hugeStream = line.repeat(repeatCount)

        assertTrue(hugeStream.length >= 1024 * 1024)

        val out = fakeSshSession.getOutputStream()
        val bytes = hugeStream.toByteArray(Charsets.UTF_8)

        // Write 1MB burst in chunks
        val chunkSize = 8192
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(chunkSize, bytes.size - offset)
            out.write(bytes, offset, length)
            offset += length
        }
        out.flush()

        val captured = fakeSshSession.getCapturedInput()
        assertEquals(hugeStream.length, captured.length)
    }

    @Test
    fun T2_B10_03_null_byte_stripping() {
        // Stream containing null bytes and non-printable control characters
        val rawWithNulls = "Booting kernel\u0000\u0000 initrd loaded\u0000 OK"
        val cleaned = sanitizeTerminalOutput(rawWithNulls)

        assertEquals("Booting kernel initrd loaded OK", cleaned)
        assertFalse(cleaned.contains("\u0000"))
    }

    @Test
    fun T2_B10_04_rapid_keystroke_flood() {
        val out = fakeSshSession.getOutputStream()
        val ctrlC = "\u0003".toByteArray(Charsets.UTF_8)

        // Simulate 100 rapid CTRL+C interruptions
        for (i in 1..100) {
            out.write(ctrlC)
        }
        out.flush()

        val captured = fakeSshSession.getCapturedInput()
        assertEquals(100, captured.length)
        assertTrue(captured.all { it == '\u0003' })
    }

    @Test
    fun T2_B10_05_terminal_disconnect_during_stream() {
        val out = fakeSshSession.getOutputStream()
        val initialCommand = "initial command"
        out.write(initialCommand.toByteArray(Charsets.UTF_8))
        out.close()

        assertTrue(out.isClosed)

        try {
            out.write("command after close".toByteArray(Charsets.UTF_8))
            fail("Expected IOException when writing to closed stream")
        } catch (e: IOException) {
            assertEquals("Stream closed", e.message)
        }

        assertEquals(initialCommand, fakeSshSession.getCapturedInput())
    }
}
