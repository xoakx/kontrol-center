package com.example.data.repository

import android.util.Log
import com.example.data.api.ArcadeApiService
import com.example.data.api.RfcItem
import com.example.data.api.RfcVoteRequest
import com.example.data.api.RfcVoteResponse
import com.example.data.dao.RfcDao
import com.example.data.entity.HostEntity
import com.example.data.entity.RfcItemEntity
import com.example.service.SshConnectionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * RfcRepository coordinates autonomous RFC proposal ingestion, local Room DB v3 persistence,
 * optimistic voting state transitions with automatic rollback, and offline caching.
 */
class RfcRepository(
    private val apiService: ArcadeApiService,
    private val rfcDao: RfcDao? = null,
    private val hostProvider: (() -> HostEntity?)? = null,
    private val sshConnectionManager: SshConnectionManager? = SshConnectionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "RfcRepository"

        fun rfcItemToEntity(item: RfcItem, hostId: Int = 1): RfcItemEntity {
            return RfcItemEntity(
                id = 0,
                hostId = hostId,
                rfcNumber = item.id,
                title = item.title,
                description = item.description,
                proposedCommands = item.proposedSteps.joinToString("\n"),
                rollbackScript = "",
                impact = item.riskLevel,
                status = item.status,
                executionLog = item.taskId ?: "",
                createdAt = System.currentTimeMillis(),
                executedAt = if (item.status.equals("APPROVED", ignoreCase = true) || item.status.equals("EXECUTED", ignoreCase = true)) {
                    System.currentTimeMillis()
                } else null
            )
        }

        fun entityToRfcItem(entity: RfcItemEntity): RfcItem {
            return RfcItem(
                id = entity.rfcNumber,
                source = "Workstation Daemon / Arcade",
                title = entity.title,
                category = "OPTIMIZATION",
                description = entity.description,
                proposedSteps = if (entity.proposedCommands.isBlank()) emptyList() else entity.proposedCommands.split("\n"),
                riskLevel = entity.impact,
                status = entity.status,
                taskId = entity.executionLog.ifEmpty { null },
                createdAt = java.util.Date(entity.createdAt).toString(),
                updatedAt = java.util.Date(entity.executedAt ?: entity.createdAt).toString(),
                resolvedBy = null
            )
        }
    }

    constructor(apiService: ArcadeApiService) : this(
        apiService = apiService,
        rfcDao = null,
        hostProvider = null,
        sshConnectionManager = SshConnectionManager,
        ioDispatcher = Dispatchers.IO
    )

    constructor(apiService: ArcadeApiService, rfcDao: RfcDao?) : this(
        apiService = apiService,
        rfcDao = rfcDao,
        hostProvider = null,
        sshConnectionManager = SshConnectionManager,
        ioDispatcher = Dispatchers.IO
    )

    private val _rfcs = MutableStateFlow<List<RfcItem>>(emptyList())
    val rfcs: StateFlow<List<RfcItem>> = _rfcs.asStateFlow()

    private val _pendingRfcs = MutableStateFlow<List<RfcItem>>(emptyList())
    val pendingRfcs: StateFlow<List<RfcItem>> = _pendingRfcs.asStateFlow()

    private val _historyRfcs = MutableStateFlow<List<RfcItem>>(emptyList())
    val historyRfcs: StateFlow<List<RfcItem>> = _historyRfcs.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private fun updateInternalState(items: List<RfcItem>) {
        _rfcs.value = items
        _pendingRfcs.value = items.filter {
            it.status.equals("PROPOSED", ignoreCase = true) || it.status.equals("PENDING_APPROVAL", ignoreCase = true)
        }
        _historyRfcs.value = items.filter {
            !it.status.equals("PROPOSED", ignoreCase = true) && !it.status.equals("PENDING_APPROVAL", ignoreCase = true)
        }
    }

    suspend fun loadCachedRfcs(hostId: Int = 1): List<RfcItem> = withContext(ioDispatcher) {
        if (rfcDao == null) return@withContext emptyList()
        val cached = rfcDao.getRfcsForHost(hostId).firstOrNull() ?: emptyList()
        val mapped = cached.map { entityToRfcItem(it) }
        if (mapped.isNotEmpty() && _rfcs.value.isEmpty()) {
            updateInternalState(mapped)
        }
        mapped
    }

    suspend fun getRfcs(hostId: Int = 1, forceRefresh: Boolean = false): Result<List<RfcItem>> = withContext(ioDispatcher) {
        if (!forceRefresh) {
            if (_rfcs.value.isNotEmpty()) {
                return@withContext Result.success(_rfcs.value)
            }
            val cached = loadCachedRfcs(hostId)
            if (cached.isNotEmpty()) {
                return@withContext Result.success(cached)
            }
        }
        fetchRfcs(hostId)
    }

    suspend fun refreshRfcs(hostId: Int = 1): Result<List<RfcItem>> = fetchRfcs(hostId)

    suspend fun fetchRfcs(hostId: Int = 1): Result<List<RfcItem>> = withContext(ioDispatcher) {
        try {
            val response = apiService.getRfcs()
            val items = response.rfcs
            updateInternalState(items)
            _isOffline.value = false

            if (rfcDao != null && items.isNotEmpty()) {
                val entities = items.map { rfcItemToEntity(it, hostId) }
                rfcDao.insertRfcs(entities)
            }

            Result.success(items)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch RFCs from network: ${e.message}")
            _isOffline.value = true

            // Fallback to Room DB cache if available
            if (rfcDao != null) {
                try {
                    val cachedEntities = rfcDao.getRfcsForHost(hostId).firstOrNull() ?: emptyList()
                    if (cachedEntities.isNotEmpty()) {
                        val mapped = cachedEntities.map { entityToRfcItem(it) }
                        updateInternalState(mapped)
                    }
                } catch (dbEx: Exception) {
                    Log.w(TAG, "Failed to load cached RFCs from Room: ${dbEx.message}")
                }
            }

            Result.failure(e)
        }
    }

    suspend fun voteRfc(
        rfcId: String,
        decision: String,
        hostId: Int = 1,
        reason: String? = null
    ): Result<RfcVoteResponse> = withContext(ioDispatcher) {
        val existing = _rfcs.value.find { it.id == rfcId }
            ?: rfcDao?.getRfcByNumberSync(rfcId)?.let { entityToRfcItem(it) }
        val originalStatus = existing?.status ?: "PROPOSED"

        val isApproval = decision.equals("approve", ignoreCase = true)
        val optimisticStatus = if (isApproval) "APPROVED" else "REJECTED"

        // Optimistic update in StateFlow
        val updatedList = _rfcs.value.map { item ->
            if (item.id == rfcId) item.copy(status = optimisticStatus) else item
        }
        updateInternalState(updatedList)

        // Optimistic update in Room DB
        try {
            rfcDao?.updateRfcStatusByNumber(rfcId, optimisticStatus, "", null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed optimistic Room update for $rfcId: ${e.message}")
        }

        try {
            val voteReq = RfcVoteRequest(decision = if (isApproval) "approve" else "deny")
            val response = apiService.voteRfc(rfcId, voteReq)

            val confirmedStatus = response.status
            val confirmedList = _rfcs.value.map { item ->
                if (item.id == rfcId) item.copy(status = confirmedStatus, taskId = response.taskId) else item
            }
            updateInternalState(confirmedList)

            try {
                rfcDao?.updateRfcStatusByNumber(
                    number = rfcId,
                    status = confirmedStatus,
                    log = response.taskId ?: "",
                    executedAt = if (isApproval) System.currentTimeMillis() else null
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist confirmed vote state for $rfcId: ${e.message}")
            }

            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error voting on RFC $rfcId: ${e.message}", e)

            // Rollback optimistic update
            val rolledBackList = _rfcs.value.map { item ->
                if (item.id == rfcId) item.copy(status = originalStatus) else item
            }
            updateInternalState(rolledBackList)

            try {
                rfcDao?.updateRfcStatusByNumber(
                    number = rfcId,
                    status = originalStatus,
                    log = "",
                    executedAt = null
                )
            } catch (dbEx: Exception) {
                Log.w(TAG, "Failed to rollback Room status for $rfcId: ${dbEx.message}")
            }

            Result.failure(e)
        }
    }

    suspend fun voteRfc(
        hostId: Int,
        rfcNumber: String,
        decision: String,
        reason: String? = null
    ): Result<RfcVoteResponse> = voteRfc(rfcNumber, decision, hostId, reason)
}
