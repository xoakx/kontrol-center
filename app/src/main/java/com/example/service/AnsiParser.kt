package com.example.service

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

data class AnsiSpan(
    val start: Int,
    val end: Int,
    val color: Color? = null,
    val background: Color? = null,
    val isBold: Boolean = false,
    val isUnderline: Boolean = false,
    val isInverse: Boolean = false
)

object AnsiParser {

    val STANDARD_FG = arrayOf(
        Color(0xFF1E293B), // 30: Black
        Color(0xFFEF4444), // 31: Red
        Color(0xFF10B981), // 32: Green
        Color(0xFFF59E0B), // 33: Yellow
        Color(0xFF3B82F6), // 34: Blue
        Color(0xFFA855F7), // 35: Magenta
        Color(0xFF06B6D4), // 36: Cyan
        Color(0xFFE2E8F0)  // 37: White
    )

    val BRIGHT_FG = arrayOf(
        Color(0xFF64748B), // 90: Bright Black / Gray
        Color(0xFFF87171), // 91: Bright Red
        Color(0xFF34D399), // 92: Bright Green
        Color(0xFFFDE047), // 93: Bright Yellow
        Color(0xFF60A5FA), // 94: Bright Blue
        Color(0xFFC084FC), // 95: Bright Magenta
        Color(0xFF22D3EE), // 96: Bright Cyan
        Color(0xFFFFFFFF)  // 97: Bright White
    )

    val STANDARD_BG = arrayOf(
        Color(0xFF000000), // 40: Black
        Color(0xFF7F1D1D), // 41: Red
        Color(0xFF064E3B), // 42: Green
        Color(0xFF78350F), // 43: Yellow
        Color(0xFF1E3A8A), // 44: Blue
        Color(0xFF581C87), // 45: Magenta
        Color(0xFF164E63), // 46: Cyan
        Color(0xFFCBD5E1)  // 47: White
    )

    val BRIGHT_BG = arrayOf(
        Color(0xFF334155), // 100: Bright Black
        Color(0xFF991B1B), // 101: Bright Red
        Color(0xFF065F46), // 102: Bright Green
        Color(0xFF854D0E), // 103: Bright Yellow
        Color(0xFF1E40AF), // 104: Bright Blue
        Color(0xFF6B21A8), // 105: Bright Magenta
        Color(0xFF155E75), // 106: Bright Cyan
        Color(0xFFF8FAFC)  // 107: Bright White
    )

    private val CUBE_STEPS = intArrayOf(0, 95, 135, 175, 215, 255)

    fun colorFrom256(index: Int): Color {
        return when (index) {
            in 0..7 -> STANDARD_FG[index]
            in 8..15 -> BRIGHT_FG[index - 8]
            in 16..231 -> {
                val offset = index - 16
                val r = (offset / 36) % 6
                val g = (offset / 6) % 6
                val b = offset % 6
                Color(CUBE_STEPS[r], CUBE_STEPS[g], CUBE_STEPS[b])
            }
            in 232..255 -> {
                val gray = 8 + (index - 232) * 10
                Color(gray, gray, gray)
            }
            else -> Color.Unspecified
        }
    }

    val ANSI_TOKEN_REGEX = Regex(
        """\u001B\[([0-9;?]*)m|\u001B\[[?0-9;]*[a-zA-Z]|\u001B\][^\u0007\u001B]*(?:\u0007|\u001B\\)|\u001B[A-Za-z0-9]"""
    )

    fun stripAnsi(text: String): String {
        return text.replace("\u0000", "").replace(ANSI_TOKEN_REGEX, "")
    }

    private data class StyleState(
        var fg: Color? = null,
        var bg: Color? = null,
        var isBold: Boolean = false,
        var isUnderline: Boolean = false,
        var isInverse: Boolean = false
    ) {
        fun reset() {
            fg = null
            bg = null
            isBold = false
            isUnderline = false
            isInverse = false
        }

        fun hasActiveStyle(): Boolean {
            return fg != null || bg != null || isBold || isUnderline || isInverse
        }

        fun toSpanStyle(defaultFg: Color): SpanStyle {
            val effectiveFg = if (isInverse) (bg ?: Color.Black) else (fg ?: defaultFg)
            val effectiveBg = if (isInverse) (fg ?: defaultFg) else (bg ?: Color.Transparent)
            return SpanStyle(
                color = effectiveFg,
                background = effectiveBg,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None
            )
        }
    }

    fun parseAnsiSpans(rawText: String): Pair<String, List<AnsiSpan>> {
        if (rawText.isEmpty()) return Pair("", emptyList())

        val sanitized = rawText.replace("\u0000", "").replace(Regex("""\u001B\[[0-9;?]*$"""), "")
        val cleanBuilder = StringBuilder()
        val spans = mutableListOf<AnsiSpan>()

        val state = StyleState()
        var currentIndex = 0
        var spanStartIndex = 0

        for (match in ANSI_TOKEN_REGEX.findAll(sanitized)) {
            val plainSegment = sanitized.substring(currentIndex, match.range.first)
            if (plainSegment.isNotEmpty()) {
                cleanBuilder.append(plainSegment)
            }

            val sgrParam = match.groups[1]
            if (sgrParam != null) {
                // If there was active styled text before this SGR code, record the span
                val currentTextLength = cleanBuilder.length
                if (currentTextLength > spanStartIndex && state.hasActiveStyle()) {
                    val effectiveFg = if (state.isInverse) (state.bg ?: Color.Black) else state.fg
                    val effectiveBg = if (state.isInverse) (state.fg ?: Color.White) else state.bg
                    spans.add(
                        AnsiSpan(
                            start = spanStartIndex,
                            end = currentTextLength,
                            color = effectiveFg,
                            background = effectiveBg,
                            isBold = state.isBold,
                            isUnderline = state.isUnderline,
                            isInverse = state.isInverse
                        )
                    )
                }
                spanStartIndex = currentTextLength
                applySgrCodes(sgrParam.value, state)
            }

            currentIndex = match.range.last + 1
        }

        if (currentIndex < sanitized.length) {
            cleanBuilder.append(sanitized.substring(currentIndex))
        }

        val finalTextLength = cleanBuilder.length
        if (finalTextLength > spanStartIndex && state.hasActiveStyle()) {
            val effectiveFg = if (state.isInverse) (state.bg ?: Color.Black) else state.fg
            val effectiveBg = if (state.isInverse) (state.fg ?: Color.White) else state.bg
            spans.add(
                AnsiSpan(
                    start = spanStartIndex,
                    end = finalTextLength,
                    color = effectiveFg,
                    background = effectiveBg,
                    isBold = state.isBold,
                    isUnderline = state.isUnderline,
                    isInverse = state.isInverse
                )
            )
        }

        return Pair(cleanBuilder.toString(), spans)
    }

    fun parseAnsiToAnnotatedString(
        rawText: String,
        defaultColor: Color = Color(0xFFE2E8F0)
    ): AnnotatedString {
        if (rawText.isEmpty()) return AnnotatedString("")

        val sanitized = rawText.replace("\u0000", "").replace(Regex("""\u001B\[[0-9;?]*$"""), "")

        return buildAnnotatedString {
            val state = StyleState()
            var currentIndex = 0
            var spanStartIndex = 0
            var hasStyleApplied = false

            for (match in ANSI_TOKEN_REGEX.findAll(sanitized)) {
                val plainLength = match.range.first - currentIndex
                if (plainLength > 0) {
                    append(sanitized.substring(currentIndex, match.range.first))
                }

                val sgrParam = match.groups[1]
                if (sgrParam != null) {
                    if (length > spanStartIndex && hasStyleApplied) {
                        addStyle(state.toSpanStyle(defaultColor), spanStartIndex, length)
                    }
                    spanStartIndex = length
                    applySgrCodes(sgrParam.value, state)
                    hasStyleApplied = state.hasActiveStyle()
                }

                currentIndex = match.range.last + 1
            }

            if (currentIndex < sanitized.length) {
                append(sanitized.substring(currentIndex))
            }

            if (length > spanStartIndex && hasStyleApplied) {
                addStyle(state.toSpanStyle(defaultColor), spanStartIndex, length)
            }
        }
    }

    private fun applySgrCodes(paramStr: String, state: StyleState) {
        if (paramStr.isEmpty()) {
            state.reset()
            return
        }

        val tokens = paramStr.split(";").mapNotNull { it.toIntOrNull() }
        if (tokens.isEmpty()) {
            state.reset()
            return
        }

        var i = 0
        while (i < tokens.size) {
            when (val code = tokens[i]) {
                0 -> state.reset()
                1 -> state.isBold = true
                2 -> {} // Dim/faint
                4 -> state.isUnderline = true
                7 -> state.isInverse = true
                22 -> state.isBold = false
                24 -> state.isUnderline = false
                27 -> state.isInverse = false
                in 30..37 -> state.fg = STANDARD_FG[code - 30]
                39 -> state.fg = null
                in 40..47 -> state.bg = STANDARD_BG[code - 40]
                49 -> state.bg = null
                in 90..97 -> state.fg = BRIGHT_FG[code - 90]
                in 100..107 -> state.bg = BRIGHT_BG[code - 100]
                38 -> {
                    // Extended foreground: 38;5;n or 38;2;r;g;b
                    if (i + 2 < tokens.size && tokens[i + 1] == 5) {
                        state.fg = colorFrom256(tokens[i + 2])
                        i += 2
                    } else if (i + 4 < tokens.size && tokens[i + 1] == 2) {
                        state.fg = Color(
                            tokens[i + 2].coerceIn(0, 255),
                            tokens[i + 3].coerceIn(0, 255),
                            tokens[i + 4].coerceIn(0, 255)
                        )
                        i += 4
                    }
                }
                48 -> {
                    // Extended background: 48;5;n or 48;2;r;g;b
                    if (i + 2 < tokens.size && tokens[i + 1] == 5) {
                        state.bg = colorFrom256(tokens[i + 2])
                        i += 2
                    } else if (i + 4 < tokens.size && tokens[i + 1] == 2) {
                        state.bg = Color(
                            tokens[i + 2].coerceIn(0, 255),
                            tokens[i + 3].coerceIn(0, 255),
                            tokens[i + 4].coerceIn(0, 255)
                        )
                        i += 4
                    }
                }
            }
            i++
        }
    }
}
