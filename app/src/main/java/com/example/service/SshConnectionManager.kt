package com.example.service

import android.util.Log
import com.example.data.entity.HostEntity
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

data class SshCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isSuccess: Boolean = (exitCode == 0)
)

class InteractiveShellSession(
    val channel: ChannelShell,
    val inputStream: InputStream,
    val outputStream: OutputStream
) {
    val isConnected: Boolean
        get() = channel.isConnected

    fun sendInput(input: String) {
        try {
            outputStream.write(input.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
        } catch (e: Exception) {
            Log.e("InteractiveShell", "Failed to write to SSH shell: ${e.message}")
        }
    }

    fun close() {
        try {
            inputStream.close()
            outputStream.close()
            channel.disconnect()
        } catch (e: Exception) {
            Log.w("InteractiveShell", "Error closing shell: ${e.message}")
        }
    }
}

object SshConnectionManager {
    private const val TAG = "SshConnectionManager"
    private val jsch = JSch()
    private val sessionCache = ConcurrentHashMap<String, Session>()

    /**
     * Obtains an active, connected JSch Session for the given host.
     * Uses session pooling to reuse persistent connections.
     */
    suspend fun getOrCreateSession(
        host: HostEntity,
        overridePassword: String? = null,
        timeoutMs: Int = 8000
    ): Session = withContext(Dispatchers.IO) {
        val cacheKey = "${host.username}@${host.address}:${host.sshPort}"
        val existingSession = sessionCache[cacheKey]

        if (existingSession != null && existingSession.isConnected) {
            return@withContext existingSession
        }

        try {
            existingSession?.disconnect()
        } catch (_: Exception) {}

        val session = jsch.getSession(host.username, host.address, host.sshPort)

        if (!overridePassword.isNullOrBlank()) {
            session.setPassword(overridePassword)
        } else if (host.sshPrivateKey.isNotBlank()) {
            val identityName = "host_${host.id}_${System.currentTimeMillis()}"
            jsch.addIdentity(identityName, host.sshPrivateKey.toByteArray(StandardCharsets.UTF_8), null, null)
        }

        val config = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey,password,keyboard-interactive")
            put("ServerAliveInterval", "15")
            put("ServerAliveCountMax", "3")
        }
        session.setConfig(config)
        session.timeout = timeoutMs
        session.connect(timeoutMs)

        sessionCache[cacheKey] = session
        Log.i(TAG, "Established fresh SSH session to $cacheKey")
        session
    }

    /**
     * Executes a single remote command over an 'exec' channel and returns the output.
     */
    suspend fun executeCommand(
        host: HostEntity,
        command: String,
        overridePassword: String? = null,
        timeoutMs: Int = 15000
    ): SshCommandResult = withContext(Dispatchers.IO) {
        val session = getOrCreateSession(host, overridePassword)
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)

        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()
        channel.outputStream = stdoutStream
        channel.setErrStream(stderrStream)

        channel.connect(timeoutMs)

        val startTime = System.currentTimeMillis()
        while (!channel.isClosed) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                channel.disconnect()
                return@withContext SshCommandResult(
                    exitCode = -1,
                    stdout = stdoutStream.toString(StandardCharsets.UTF_8.name()),
                    stderr = "SSH Command timed out after ${timeoutMs}ms"
                )
            }
            kotlinx.coroutines.delay(50)
        }

        val exitCode = channel.exitStatus
        val stdout = stdoutStream.toString(StandardCharsets.UTF_8.name())
        val stderr = stderrStream.toString(StandardCharsets.UTF_8.name())
        channel.disconnect()

        SshCommandResult(
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr
        )
    }

    /**
     * Opens an interactive PTY shell channel for real-time terminal streaming.
     */
    suspend fun openInteractiveShell(
        host: HostEntity,
        cols: Int = 80,
        rows: Int = 24,
        overridePassword: String? = null
    ): InteractiveShellSession = withContext(Dispatchers.IO) {
        val session = getOrCreateSession(host, overridePassword)
        val channel = session.openChannel("shell") as ChannelShell

        channel.setPtyType("xterm-256color", cols, rows, 640, 480)
        val inputStream = channel.inputStream
        val outputStream = channel.outputStream

        channel.connect(10000)
        Log.i(TAG, "Opened interactive shell channel to ${host.address}")

        InteractiveShellSession(
            channel = channel,
            inputStream = inputStream,
            outputStream = outputStream
        )
    }

    /**
     * Lists files and directories on remote host using SFTP.
     */
    suspend fun listRemoteFiles(
        host: HostEntity,
        directoryPath: String
    ): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val session = getOrCreateSession(host)
        val channel = session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp
        channel.connect(10000)

        val resultList = mutableListOf<Map<String, Any>>()
        try {
            val vector = channel.ls(directoryPath)
            for (obj in vector) {
                if (obj is com.jcraft.jsch.ChannelSftp.LsEntry) {
                    val name = obj.filename
                    if (name == "." || name == "..") continue
                    val attrs = obj.attrs
                    resultList.add(
                        mapOf(
                            "name" to name,
                            "path" to "$directoryPath/$name".replace("//", "/"),
                            "isDirectory" to attrs.isDir,
                            "sizeBytes" to attrs.size,
                            "permissions" to attrs.permissionsString,
                            "modifiedTime" to obj.attrs.mtimeString
                        )
                    )
                }
            }
        } finally {
            channel.disconnect()
        }
        resultList.sortedWith(compareBy({ !(it["isDirectory"] as Boolean) }, { it["name"] as String }))
    }

    /**
     * Uploads an input stream to remote path via SFTP.
     */
    suspend fun uploadFile(
        host: HostEntity,
        inputStream: InputStream,
        remotePath: String
    ) = withContext(Dispatchers.IO) {
        val session = getOrCreateSession(host)
        val channel = session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp
        channel.connect(10000)
        try {
            channel.put(inputStream, remotePath, com.jcraft.jsch.ChannelSftp.OVERWRITE)
        } finally {
            channel.disconnect()
        }
    }

    /**
     * Disconnects and invalidates all cached sessions.
     */
    fun disconnectAll() {
        sessionCache.values.forEach { session ->
            try {
                if (session.isConnected) session.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting session: ${e.message}")
            }
        }
        sessionCache.clear()
    }
}
