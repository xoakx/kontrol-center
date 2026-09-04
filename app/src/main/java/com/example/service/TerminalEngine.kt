package com.example.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TerminalLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT
)

enum class TerminalLineType {
    INPUT,
    OUTPUT,
    SYSTEM,
    ERROR,
    SUCCESS
}

data class TerminalState(
    val lines: List<TerminalLine> = emptyList(),
    val currentInput: String = "",
    val isExecuting: Boolean = false,
    val workingDirectory: String = "~/projects",
    val promptString: String = "andrew@workstation:~/projects$ ",
    val commandHistory: List<String> = emptyList()
)

class TerminalEngine(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(TerminalState())
    val state = _state.asStateFlow()

    init {
        val initialBanner = listOf(
            TerminalLine(text = "Linux workstation 6.8.0-45-generic x86_64 GNU/Linux", type = TerminalLineType.SYSTEM),
            TerminalLine(text = "Authenticated with Mobile Key [hostmanager@android]", type = TerminalLineType.SUCCESS),
            TerminalLine(text = "Welcome to Termux/SSH Mobile Console. Type 'help' or tap quick actions.", type = TerminalLineType.SYSTEM),
            TerminalLine(text = "andrew@workstation:~/projects$ ", type = TerminalLineType.INPUT)
        )
        _state.value = _state.value.copy(lines = initialBanner)
    }

    fun updateInput(input: String) {
        _state.value = _state.value.copy(currentInput = input)
    }

    fun appendQuickKey(key: String) {
        when (key) {
            "CLEAR" -> clearConsole()
            "TAB" -> {
                // Auto complete simulation
                val cur = _state.value.currentInput
                if (cur.isNotEmpty()) {
                    _state.value = _state.value.copy(currentInput = "$cur/")
                }
            }
            "ESC" -> {
                _state.value = _state.value.copy(currentInput = "")
            }
            "CTRL+C" -> {
                val line = TerminalLine(text = "${_state.value.promptString}${_state.value.currentInput}^C", type = TerminalLineType.ERROR)
                _state.value = _state.value.copy(
                    lines = _state.value.lines + line,
                    currentInput = ""
                )
            }
            "UP" -> {
                val history = _state.value.commandHistory
                if (history.isNotEmpty()) {
                    _state.value = _state.value.copy(currentInput = history.last())
                }
            }
            "DOWN" -> {
                _state.value = _state.value.copy(currentInput = "")
            }
            else -> {
                _state.value = _state.value.copy(currentInput = _state.value.currentInput + key)
            }
        }
    }

    fun executeCommand(rawCommand: String? = null) {
        val cmd = (rawCommand ?: _state.value.currentInput).trim()
        if (cmd.isEmpty()) return

        val inputLine = TerminalLine(text = "${_state.value.promptString}$cmd", type = TerminalLineType.INPUT)
        _state.value = _state.value.copy(
            lines = _state.value.lines + inputLine,
            currentInput = "",
            isExecuting = true,
            commandHistory = _state.value.commandHistory + cmd
        )

        scope.launch {
            delay(350)
            val outputLines = simulateCommandOutput(cmd)
            _state.value = _state.value.copy(
                lines = _state.value.lines + outputLines,
                isExecuting = false
            )
        }
    }

    fun clearConsole() {
        _state.value = _state.value.copy(
            lines = listOf(
                TerminalLine(text = "Console buffer cleared.", type = TerminalLineType.SYSTEM),
                TerminalLine(text = _state.value.promptString, type = TerminalLineType.INPUT)
            )
        )
    }

    private fun simulateCommandOutput(command: String): List<TerminalLine> {
        val parts = command.split(" ")
        val main = parts.firstOrNull() ?: ""

        return when {
            command.startsWith("uptime") -> listOf(
                TerminalLine(text = " 17:55:12 up 12 days, 8:15,  2 users,  load average: 0.35, 0.42, 0.38", type = TerminalLineType.OUTPUT)
            )
            command.startsWith("free") -> listOf(
                TerminalLine(text = "               total        used        free      shared  buff/cache   available", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "Mem:            31Gi       11Gi        14Gi       412Mi       5.8Gi        19Gi", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "Swap:          8.0Gi       1.2Gi       6.8Gi", type = TerminalLineType.OUTPUT)
            )
            command.startsWith("df") -> listOf(
                TerminalLine(text = "Filesystem      Size  Used Avail Use% Mounted on", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "/dev/nvme0n1p2  938G  488G  403G  55% /", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "/dev/sda1       3.6T  1.8T  1.7T  52% /media/storage", type = TerminalLineType.OUTPUT)
            )
            command.startsWith("docker ps") -> listOf(
                TerminalLine(text = "CONTAINER ID   IMAGE                 COMMAND                  PORTS                    NAMES", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "3f82a901e8bc   cockpit/ws:latest     \"/container/atomic-r…\"   0.0.0.0:9090->9090/tcp   cockpit-service", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "e12a912bb01c   postgres:16-alpine    \"docker-entrypoint.s…\"   0.0.0.0:5432->5432/tcp   app-postgres", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "c78f0923e11a   redis:7-alpine        \"docker-entrypoint.s…\"   0.0.0.0:6379->6379/tcp   cache-redis", type = TerminalLineType.OUTPUT)
            )
            command.startsWith("nvidia-smi") -> listOf(
                TerminalLine(text = "+-----------------------------------------------------------------------------------------+", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "| NVIDIA-SMI 550.90.07              Driver Version: 550.90.07      CUDA Version: 12.4     |", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "| GPU  Name                 Persistence-M | Bus-Id          Disp.A | Volatile Uncorr. ECC |", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "| 0    NVIDIA RTX 4070 Ti             Off | 00000000:01:00.0   On |                  N/A |", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "| 42%   48C    P2              72W / 285W |   2412MiB / 12282MiB |      8%      Default |", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "+-----------------------------------------------------------------------------------------+", type = TerminalLineType.OUTPUT)
            )
            command.startsWith("systemctl status") -> listOf(
                TerminalLine(text = "● ssh.service - OpenBSD Secure Shell server", type = TerminalLineType.SUCCESS),
                TerminalLine(text = "     Loaded: loaded (/usr/lib/systemd/system/ssh.service; enabled)", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "     Active: active (running) since Tue 2026-08-22 09:30:11 UTC; 12 days ago", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "   Main PID: 1042 (sshd)", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "      Tasks: 3 (limit: 38291)", type = TerminalLineType.OUTPUT)
            )
            command.startsWith("ss") || command.startsWith("netstat") -> listOf(
                TerminalLine(text = "Netid State  Recv-Q Send-Q Local Address:Port  Peer Address:Port", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "tcp   LISTEN 0      128    0.0.0.0:22          0.0.0.0:*      (sshd)", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "tcp   LISTEN 0      128    0.0.0.0:9090        0.0.0.0:*      (cockpit-tls)", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "tcp   LISTEN 0      128    0.0.0.0:4713        0.0.0.0:*      (pipewire-pulse)", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "tcp   LISTEN 0      128    0.0.0.0:5900        0.0.0.0:*      (wayvnc)", type = TerminalLineType.OUTPUT)
            )
            command.startsWith("ls") -> listOf(
                TerminalLine(text = "Desktop  Documents  Downloads  Music  Pictures  Videos  projects  .ssh", type = TerminalLineType.OUTPUT)
            )
            command == "help" -> listOf(
                TerminalLine(text = "Built-in Remote Commands:", type = TerminalLineType.SYSTEM),
                TerminalLine(text = "  uptime, free -h, df -h, docker ps, nvidia-smi, systemctl status, ss -tulpn", type = TerminalLineType.OUTPUT),
                TerminalLine(text = "  clear, pactl, wayvnc, cockpit, whoami, uname -a", type = TerminalLineType.OUTPUT)
            )
            else -> listOf(
                TerminalLine(text = "[Executed: $command] Exit Code: 0 (Command sent via SSH channel)", type = TerminalLineType.SUCCESS)
            )
        }
    }
}
