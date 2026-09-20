package com.example.e2e.harness

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * In-memory PTY streaming test double with ANSI escape sequence emission.
 * Simulates an interactive SSH shell session without live network or JSch sockets.
 */
class FakeSshSession {
    val capturedOutput = ByteArrayOutputStream()
    private var staticInputBytes = byteArrayOf()

    /**
     * Feeds raw string data into the session's simulated input stream.
     */
    fun feedShellOutput(rawOutput: String) {
        staticInputBytes = rawOutput.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Emits ANSI SGR escape sequence with specified color code and optional bold style.
     */
    fun emitAnsiColor(text: String, colorCode: Int, isBold: Boolean = false) {
        val boldPrefix = if (isBold) "1;" else ""
        val sequence = "\u001B[${boldPrefix}${colorCode}m$text\u001B[0m"
        feedShellOutput(sequence)
    }

    /**
     * Emits standard shell prompt sequence.
     */
    fun emitPrompt(prompt: String = "$ ") {
        feedShellOutput(prompt)
    }

    /**
     * Returns an InputStream reading from the currently fed data.
     */
    fun getInputStream(): InputStream {
        return ByteArrayInputStream(staticInputBytes)
    }

    /**
     * Returns the OutputStream where outbound bytes are captured.
     */
    fun getOutputStream(): OutputStream {
        return capturedOutput
    }

    /**
     * Retrieves outbound characters sent to the shell session.
     */
    fun getCapturedInput(): String {
        return capturedOutput.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * Clears all stream buffers.
     */
    fun clear() {
        capturedOutput.reset()
        staticInputBytes = byteArrayOf()
    }
}
