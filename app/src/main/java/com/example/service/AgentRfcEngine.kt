package com.example.service

import android.util.Log
import com.example.BuildConfig
import com.example.data.entity.HostEntity
import com.example.data.entity.RfcItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class AgentChatMsg(
    val id: Long = System.nanoTime(),
    val sender: String, // "USER" or "AGENT"
    val text: String,
    val rfcAttachment: RfcItemEntity? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentState(
    val isThinking: Boolean = false,
    val chatHistory: List<AgentChatMsg> = emptyList(),
    val suggestedPrompts: List<String> = listOf(
        "Audit GPU memory and prune VRAM contexts",
        "Check systemd services and journal logs",
        "Optimize network buffers and PipeWire audio stream",
        "Check disk storage bottlenecks on root filesystem"
    )
)

class AgentRfcEngine {
    companion object {
        private const val TAG = "AgentRfcEngine"
    }

    private val _state = MutableStateFlow(AgentState())
    val state = _state.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        _state.value = _state.value.copy(
            chatHistory = listOf(
                AgentChatMsg(
                    sender = "AGENT",
                    text = "Hello! I am your Autonomous Host Engineering Agent. I connect to your local workstation LLM or Gemini API to diagnose system issues, generate formal RFCs, and execute approved changes safely."
                )
            )
        )
    }

    suspend fun submitUserPrompt(
        prompt: String,
        host: HostEntity,
        onRfcGenerated: suspend (RfcItemEntity) -> Unit
    ) {
        val userMsg = AgentChatMsg(sender = "USER", text = prompt)
        _state.value = _state.value.copy(
            chatHistory = _state.value.chatHistory + userMsg,
            isThinking = true
        )

        val rfc = generateRfcProposal(prompt, host)
        onRfcGenerated(rfc)

        val agentResponse = AgentChatMsg(
            sender = "AGENT",
            text = "I have drafted ${rfc.rfcNumber}: '${rfc.title}' with ${rfc.impact} impact. Review the proposed commands below, then approve to execute them directly over SSH.",
            rfcAttachment = rfc
        )

        _state.value = _state.value.copy(
            chatHistory = _state.value.chatHistory + agentResponse,
            isThinking = false
        )
    }

    /**
     * Executes an approved RFC directly on the remote host via SSH.
     */
    suspend fun executeApprovedRfc(
        rfc: RfcItemEntity,
        host: HostEntity
    ): SshCommandResult = withContext(Dispatchers.IO) {
        val startMsg = AgentChatMsg(
            sender = "AGENT",
            text = "Executing ${rfc.rfcNumber} on ${host.name} (${host.address})..."
        )
        _state.value = _state.value.copy(
            chatHistory = _state.value.chatHistory + startMsg,
            isThinking = true
        )

        val result = SshConnectionManager.executeCommand(
            host = host,
            command = rfc.proposedCommands,
            timeoutMs = 30000
        )

        val completionText = if (result.isSuccess) {
            "✅ ${rfc.rfcNumber} executed successfully (Exit Code 0)!\n\nOutput:\n${result.stdout.trim().ifEmpty { "(Command produced no stdout)" }}"
        } else {
            "❌ ${rfc.rfcNumber} execution failed (Exit Code ${result.exitCode}):\n\n${result.stderr.trim().ifEmpty { result.stdout.trim() }}"
        }

        val finishMsg = AgentChatMsg(
            sender = "AGENT",
            text = completionText
        )

        _state.value = _state.value.copy(
            chatHistory = _state.value.chatHistory + finishMsg,
            isThinking = false
        )

        result
    }

    private suspend fun generateRfcProposal(
        prompt: String,
        host: HostEntity
    ): RfcItemEntity = withContext(Dispatchers.IO) {
        // 1. Try local workstation llama-server (Port 8000 or 8001)
        val localLlmRfc = callLocalLlmForRfc(prompt, host)
        if (localLlmRfc != null) return@withContext localLlmRfc

        // 2. Try Gemini API if key is available
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val apiRfc = callGeminiForRfc(prompt, apiKey, host.id)
                if (apiRfc != null) return@withContext apiRfc
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API error: ${e.message}")
            }
        }

        // 3. Intelligent rule-based fallback
        generateFallbackRfc(prompt, host.id)
    }

    private fun callLocalLlmForRfc(prompt: String, host: HostEntity): RfcItemEntity? {
        val candidatePorts = listOf(8000, 8001)
        val systemPrompt = "You are an autonomous Linux SRE agent. Given a user task, output JSON with fields: 'title', 'description', 'proposedCommands', 'rollbackScript', 'impact' (LOW, MEDIUM, HIGH)."

        for (port in candidatePorts) {
            try {
                val url = "http://${host.address}:$port/v1/chat/completions"
                val jsonPayload = JSONObject().apply {
                    put("model", "qwen")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.3)
                    put("max_tokens", 512)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val root = JSONObject(body)
                    val content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    val cleaned = content.substringAfter("{").substringBeforeLast("}")
                    val parsed = JSONObject("{$cleaned}")

                    return RfcItemEntity(
                        hostId = host.id,
                        rfcNumber = "RFC-${Random.nextInt(100, 999)}",
                        title = parsed.optString("title", "Local LLM System Change"),
                        description = parsed.optString("description", "Generated via Workstation Local Qwen LLM"),
                        proposedCommands = parsed.optString("proposedCommands", "uptime"),
                        rollbackScript = parsed.optString("rollbackScript", "# No rollback"),
                        impact = parsed.optString("impact", "LOW"),
                        status = "PENDING_APPROVAL"
                    )
                }
            } catch (_: Exception) {
                // Try next port or fallback
            }
        }
        return null
    }

    private fun callGeminiForRfc(prompt: String, apiKey: String, hostId: Int): RfcItemEntity? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val systemPrompt = "You are an autonomous Linux server administration agent. When the user requests a server change or problem fix, respond strictly in JSON with fields: 'title', 'description', 'proposedCommands', 'rollbackScript', 'impact' (LOW, MEDIUM, HIGH)."

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "System Instruction: $systemPrompt\nUser Request: $prompt")
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string() ?: return null

        val rootJson = JSONObject(bodyString)
        val candidate = rootJson.getJSONArray("candidates").getJSONObject(0)
        val text = candidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")

        val cleaned = text.substringAfter("{").substringBeforeLast("}")
        val parsed = JSONObject("{$cleaned}")

        return RfcItemEntity(
            hostId = hostId,
            rfcNumber = "RFC-${Random.nextInt(100, 999)}",
            title = parsed.optString("title", "AI Requested Server Change"),
            description = parsed.optString("description", "Automated proposal from Gemini agent"),
            proposedCommands = parsed.optString("proposedCommands", "uptime && df -h"),
            rollbackScript = parsed.optString("rollbackScript", "# Rollback script"),
            impact = parsed.optString("impact", "LOW"),
            status = "PENDING_APPROVAL"
        )
    }

    private fun generateFallbackRfc(prompt: String, hostId: Int): RfcItemEntity {
        val rfcNum = "RFC-${Random.nextInt(50, 99)}"
        val lower = prompt.lowercase()

        return when {
            lower.contains("memory") || lower.contains("ram") || lower.contains("cache") -> {
                RfcItemEntity(
                    hostId = hostId,
                    rfcNumber = rfcNum,
                    title = "System Memory Defragmentation & Page Cache Drop",
                    description = "Flush file system buffers and drop clean page caches, dentries and inodes.",
                    proposedCommands = "sudo sync && echo 3 | sudo tee /proc/sys/vm/drop_caches && free -h",
                    rollbackScript = "# Transient kernel cache drop; no rollback needed",
                    impact = "LOW",
                    status = "PENDING_APPROVAL"
                )
            }
            lower.contains("gpu") || lower.contains("nvidia") || lower.contains("vram") -> {
                RfcItemEntity(
                    hostId = hostId,
                    rfcNumber = rfcNum,
                    title = "Audit NVIDIA GPU Telemetry & VRAM Allocation",
                    description = "Query active GPU compute processes, power usage, and memory headroom.",
                    proposedCommands = "nvidia-smi --query-gpu=name,temperature.gpu,utilization.gpu,memory.used,memory.total --format=csv",
                    rollbackScript = "# Read-only query; no rollback needed",
                    impact = "LOW",
                    status = "PENDING_APPROVAL"
                )
            }
            else -> {
                RfcItemEntity(
                    hostId = hostId,
                    rfcNumber = rfcNum,
                    title = "System Inspection & Diagnostics",
                    description = "Gather system uptime, disk usage, and top resource-consuming processes.",
                    proposedCommands = "uptime && df -h / && ps aux --sort=-%mem | head -n 10",
                    rollbackScript = "# Read-only diagnostic task",
                    impact = "LOW",
                    status = "PENDING_APPROVAL"
                )
            }
        }
    }
}
