package com.example.service

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnsiParserTest {

    @Test
    fun testStandardForegroundColors_30to37() {
        val input = "\u001B[30mBlack\u001B[31mRed\u001B[32mGreen\u001B[33mYellow\u001B[34mBlue\u001B[35mMagenta\u001B[36mCyan\u001B[37mWhite\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("BlackRedGreenYellowBlueMagentaCyanWhite", cleanText)
        assertEquals(8, spans.size)

        assertEquals(AnsiParser.STANDARD_FG[0], spans[0].color) // Black
        assertEquals(AnsiParser.STANDARD_FG[1], spans[1].color) // Red
        assertEquals(AnsiParser.STANDARD_FG[2], spans[2].color) // Green
        assertEquals(AnsiParser.STANDARD_FG[3], spans[3].color) // Yellow
        assertEquals(AnsiParser.STANDARD_FG[4], spans[4].color) // Blue
        assertEquals(AnsiParser.STANDARD_FG[5], spans[5].color) // Magenta
        assertEquals(AnsiParser.STANDARD_FG[6], spans[6].color) // Cyan
        assertEquals(AnsiParser.STANDARD_FG[7], spans[7].color) // White

        val annotated = AnsiParser.parseAnsiToAnnotatedString(input)
        assertEquals("BlackRedGreenYellowBlueMagentaCyanWhite", annotated.text)
        assertEquals(8, annotated.spanStyles.size)
    }

    @Test
    fun testBrightForegroundColors_90to97() {
        val input = "\u001B[90mGray\u001B[91mBRed\u001B[92mBGreen\u001B[93mBYellow\u001B[94mBBlue\u001B[95mBMagenta\u001B[96mBCyan\u001B[97mBWhite\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("GrayBRedBGreenBYellowBBlueBMagentaBCyanBWhite", cleanText)
        assertEquals(8, spans.size)

        assertEquals(AnsiParser.BRIGHT_FG[0], spans[0].color)
        assertEquals(AnsiParser.BRIGHT_FG[1], spans[1].color)
        assertEquals(AnsiParser.BRIGHT_FG[2], spans[2].color)
        assertEquals(AnsiParser.BRIGHT_FG[3], spans[3].color)
        assertEquals(AnsiParser.BRIGHT_FG[4], spans[4].color)
        assertEquals(AnsiParser.BRIGHT_FG[5], spans[5].color)
        assertEquals(AnsiParser.BRIGHT_FG[6], spans[6].color)
        assertEquals(AnsiParser.BRIGHT_FG[7], spans[7].color)
    }

    @Test
    fun testBackgroundColors_40to47_and_100to107() {
        val input = "\u001B[41mRedBg\u001B[104mBrightBlueBg\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("RedBgBrightBlueBg", cleanText)
        assertEquals(2, spans.size)

        assertEquals(AnsiParser.STANDARD_BG[1], spans[0].background)
        assertEquals(AnsiParser.BRIGHT_BG[4], spans[1].background)

        val annotated = AnsiParser.parseAnsiToAnnotatedString(input)
        assertEquals(2, annotated.spanStyles.size)
        assertEquals(AnsiParser.STANDARD_BG[1], annotated.spanStyles[0].item.background)
        assertEquals(AnsiParser.BRIGHT_BG[4], annotated.spanStyles[1].item.background)
    }

    @Test
    fun testTextStyle_BoldAndReset() {
        val input = "Normal \u001B[1mBold Text\u001B[0m Normal Again"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("Normal Bold Text Normal Again", cleanText)
        assertEquals(1, spans.size)
        assertEquals(7, spans[0].start)
        assertEquals(16, spans[0].end)
        assertTrue(spans[0].isBold)

        val annotated = AnsiParser.parseAnsiToAnnotatedString(input)
        assertEquals(1, annotated.spanStyles.size)
        val span = annotated.spanStyles[0]
        assertEquals(7, span.start)
        assertEquals(16, span.end)
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test
    fun testTextStyle_UnderlineAndOff() {
        val input = "\u001B[4mUnderlined URL\u001B[24m Rest"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("Underlined URL Rest", cleanText)
        assertEquals(1, spans.size)
        assertEquals(0, spans[0].start)
        assertEquals(14, spans[0].end)
        assertTrue(spans[0].isUnderline)

        val annotated = AnsiParser.parseAnsiToAnnotatedString(input)
        assertEquals(1, annotated.spanStyles.size)
        assertEquals(TextDecoration.Underline, annotated.spanStyles[0].item.textDecoration)
    }

    @Test
    fun test256ColorPalette_Foreground_38_5_n() {
        // Standard (1 -> Red), Cube (196 -> R=255, G=0, B=0), Grayscale (232 -> 8, 8, 8)
        val input = "\u001B[38;5;1mStd\u001B[38;5;196mCubeRed\u001B[38;5;232mGray\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("StdCubeRedGray", cleanText)
        assertEquals(3, spans.size)

        assertEquals(AnsiParser.STANDARD_FG[1], spans[0].color)
        assertEquals(Color(255, 0, 0), spans[1].color)
        assertEquals(Color(8, 8, 8), spans[2].color)

        // Test boundary 255 -> 8 + (255 - 232) * 10 = 238
        val whiteGray = AnsiParser.colorFrom256(255)
        assertEquals(Color(238, 238, 238), whiteGray)
    }

    @Test
    fun test256ColorPalette_Background_48_5_n() {
        val input = "\u001B[48;5;235mDark Gray Background\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("Dark Gray Background", cleanText)
        assertEquals(1, spans.size)
        // 8 + (235 - 232) * 10 = 38
        assertEquals(Color(38, 38, 38), spans[0].background)
    }

    @Test
    fun testTrueColor_24bit_ForegroundAndBackground() {
        val input = "\u001B[38;2;123;45;67m\u001B[48;2;10;20;30mTrueColor\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("TrueColor", cleanText)
        assertEquals(1, spans.size)
        assertEquals(Color(123, 45, 67), spans[0].color)
        assertEquals(Color(10, 20, 30), spans[0].background)
    }

    @Test
    fun testCombinedStyles_SingleSequence() {
        val input = "\u001B[1;31;44mBold Red on Blue\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("Bold Red on Blue", cleanText)
        assertEquals(1, spans.size)
        assertTrue(spans[0].isBold)
        assertEquals(AnsiParser.STANDARD_FG[1], spans[0].color)
        assertEquals(AnsiParser.STANDARD_BG[4], spans[0].background)
    }

    @Test
    fun testCombinedStyles_OrderInvariance() {
        val input1 = "\u001B[31;1mTextA\u001B[0m"
        val input2 = "\u001B[1;31mTextB\u001B[0m"

        val (_, spans1) = AnsiParser.parseAnsiSpans(input1)
        val (_, spans2) = AnsiParser.parseAnsiSpans(input2)

        assertEquals(spans1[0].isBold, spans2[0].isBold)
        assertEquals(spans1[0].color, spans2[0].color)
    }

    @Test
    fun testResetStyles_Code0_and_EmptyCSI() {
        val input = "\u001B[32mGreen\u001B[mNormal"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("GreenNormal", cleanText)
        assertEquals(1, spans.size)
        assertEquals(0, spans[0].start)
        assertEquals(5, spans[0].end)
    }

    @Test
    fun testInverseVideo_SwapForegroundAndBackground() {
        val input = "\u001B[31;42;7mInverted\u001B[27mRestored\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("InvertedRestored", cleanText)
        assertEquals(2, spans.size)

        // Inverted: FG should be original BG (Green), BG should be original FG (Red)
        assertEquals(AnsiParser.STANDARD_BG[2], spans[0].color)
        assertEquals(AnsiParser.STANDARD_FG[1], spans[0].background)

        // Restored: FG is Red, BG is Green
        assertEquals(AnsiParser.STANDARD_FG[1], spans[1].color)
        assertEquals(AnsiParser.STANDARD_BG[2], spans[1].background)
    }

    @Test
    fun testMalformed_TruncatedAtChunkBoundary() {
        val input = "output \u001B["
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("output ", cleanText)
        assertTrue(spans.isEmpty())

        val annotated = AnsiParser.parseAnsiToAnnotatedString(input)
        assertEquals("output ", annotated.text)
    }

    @Test
    fun testNonSgrCsiSequences_Ignored() {
        val input = "Line1\u001B[2K\u001B[1GLine2\u001B[?25h\u001B]0;Terminal Title\u0007"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("Line1Line2", cleanText)
        assertTrue(spans.isEmpty())
    }

    @Test
    fun testPlainTextPreservation_ZeroEscapeCodes() {
        val raw = "System uptime: 12:45:00 up 3 days, 2 users, load average: 0.15, 0.20, 0.25"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(raw)

        assertEquals(raw, cleanText)
        assertTrue(spans.isEmpty())

        val annotated = AnsiParser.parseAnsiToAnnotatedString(raw)
        assertEquals(raw, annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun testUnicodeAndEmojiPreservation() {
        val input = "\u001B[32m✔ Success: 🚀 System operational\u001B[0m"
        val (cleanText, spans) = AnsiParser.parseAnsiSpans(input)

        assertEquals("✔ Success: 🚀 System operational", cleanText)
        assertEquals(1, spans.size)
        assertEquals(0, spans[0].start)
        assertEquals(cleanText.length, spans[0].end)
    }

    @Test
    fun testStripAnsi_RemovesCodesAndNulls() {
        val input = "Hello\u0000 \u001B[1;31mWorld\u001B[0m\u001B[2K!"
        val clean = AnsiParser.stripAnsi(input)
        assertEquals("Hello World!", clean)
    }

    @Test
    fun testColorFrom256_OutOfRange() {
        assertEquals(Color.Unspecified, AnsiParser.colorFrom256(-1))
        assertEquals(Color.Unspecified, AnsiParser.colorFrom256(256))
    }
}
