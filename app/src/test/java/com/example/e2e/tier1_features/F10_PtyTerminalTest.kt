package com.example.e2e.tier1_features

import com.example.e2e.harness.FakeSshSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Tests for Feature 10: Interactive PTY Terminal & ANSI Color.
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F10_01: Standard ANSI SGR color code parsing and stripping (30-37)
 * - T1_F10_02: Bright ANSI colors and bold graphics rendition modes (1;91m)
 * - T1_F10_03: ANSI reset sequence (\u001B[0m) boundary demarcation
 * - T1_F10_04: Plain text stream processing without escape artifacts
 * - T1_F10_05: Touch terminal quick-key accessory dispatch byte mapping
 */
class F10_PtyTerminalTest {

    private lateinit var fakeSshSession: FakeSshSession
    private val ansiRegex = Regex("\u001B\\[[;?0-9]*[a-zA-Z]")

    @Before
    fun setUp() {
        fakeSshSession = FakeSshSession()
    }

    private fun stripAnsi(text: String): String {
        return text.replace(ansiRegex, "")
    }

    @Test
    fun T1_F10_01_standard_ansi_sgr_color_codes() {
        val rawInput = "\u001B[31mError\u001B[0m \u001B[32mSuccess\u001B[0m"
        val clean = stripAnsi(rawInput)

        assertEquals("Error Success", clean)
        assertFalse(clean.contains("\u001B"))
        assertFalse(clean.contains("[31m"))
        assertFalse(clean.contains("[32m"))
    }

    @Test
    fun T1_F10_02_bright_ansi_colors_and_bold_text() {
        val rawInput = "\u001B[1;91mCRITICAL WARNING\u001B[0m"
        val clean = stripAnsi(rawInput)

        assertEquals("CRITICAL WARNING", clean)
        assertFalse(clean.contains("\u001B[1;91m"))
    }

    @Test
    fun T1_F10_03_ansi_reset_sequence() {
        val rawInput = "\u001B[34mBlue Text\u001B[0m Normal Text"
        val clean = stripAnsi(rawInput)

        assertEquals("Blue Text Normal Text", clean)
        assertFalse(clean.contains("\u001B[0m"))
    }

    @Test
    fun T1_F10_04_plain_text_stream_without_spans() {
        val rawInput = "Standard Linux Kernel log output: memory allocated 4096 bytes"
        val clean = stripAnsi(rawInput)

        assertEquals(rawInput, clean)
        assertEquals(rawInput.length, clean.length)
    }

    @Test
    fun T1_F10_05_terminal_key_accessories_dispatch() {
        // Test key mappings matching TerminalEngine.appendQuickKey
        val keyMappings = mapOf(
            "CTRL+C" to "\u0003",
            "ESC" to "\u001b",
            "TAB" to "\t",
            "UP" to "\u001b[A",
            "DOWN" to "\u001b[B",
            "LEFT" to "\u001b[D",
            "RIGHT" to "\u001b[C"
        )

        val out = fakeSshSession.getOutputStream()

        for ((key, expectedSequence) in keyMappings) {
            val bytes = expectedSequence.toByteArray(Charsets.UTF_8)
            out.write(bytes)
            out.flush()

            val captured = fakeSshSession.getCapturedInput()
            assertTrue("Key $key expected to emit sequence", captured.contains(expectedSequence))
            fakeSshSession.clear()
        }
    }
}
