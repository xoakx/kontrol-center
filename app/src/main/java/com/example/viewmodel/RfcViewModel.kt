package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RfcItem
import com.example.data.entity.RfcItemEntity
import com.example.data.repository.RfcRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RfcFilter {
    ALL,
    PENDING,
    APPROVED,
    REJECTED
}

data class RfcUiState(
    val isLoading: Boolean = true,
    val rfcs: List<RfcItem> = emptyList(),
    val pendingRfcs: List<RfcItem> = emptyList(),
    val historyRfcs: List<RfcItem> = emptyList(),
    val filteredRfcs: List<RfcItem> = emptyList(),
    val selectedTab: Int = 0, // 0: Pending, 1: History, 2: Agent Chat / Generator
    val selectedFilter: RfcFilter = RfcFilter.PENDING,
    val selectedRiskFilter: String? = null, // null = ALL, "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val searchQuery: String = "",
    val selectedRfc: RfcItem? = null,
    val votingInProgressId: String? = null, // Debouncing & indicator
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val userNotice: String? = null
)

class RfcViewModel(
    val rfcRepository: RfcRepository,
    private val defaultHostId: Int = 1,
    private val scope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val currentScope: kotlinx.coroutines.CoroutineScope
        get() = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(RfcUiState(isLoading = true))
    val uiState: StateFlow<RfcUiState> = _uiState.asStateFlow()

    init {
        currentScope.launch {
            rfcRepository.rfcs.collect { list ->
                _uiState.update { state ->
                    val pending = list.filter {
                        it.status.equals("PROPOSED", ignoreCase = true) || it.status.equals("PENDING_APPROVAL", ignoreCase = true)
                    }
                    val history = list.filter {
                        !it.status.equals("PROPOSED", ignoreCase = true) && !it.status.equals("PENDING_APPROVAL", ignoreCase = true)
                    }
                    computeFilteredState(
                        state.copy(
                            rfcs = list,
                            pendingRfcs = pending,
                            historyRfcs = history,
                            isLoading = false
                        )
                    )
                }
            }
        }

        currentScope.launch {
            rfcRepository.isOffline.collect { offline ->
                _uiState.update { it.copy(isOffline = offline) }
            }
        }

        refresh(defaultHostId)
    }

    private fun computeFilteredState(state: RfcUiState): RfcUiState {
        val baseList = when (state.selectedFilter) {
            RfcFilter.PENDING -> state.pendingRfcs
            RfcFilter.APPROVED -> state.rfcs.filter {
                it.status.equals("APPROVED", ignoreCase = true) || it.status.equals("EXECUTED", ignoreCase = true)
            }
            RfcFilter.REJECTED -> state.rfcs.filter {
                it.status.equals("REJECTED", ignoreCase = true) || it.status.equals("FAILED", ignoreCase = true)
            }
            RfcFilter.ALL -> {
                if (state.selectedTab == 1) state.historyRfcs else state.rfcs
            }
        }

        val riskFiltered = if (state.selectedRiskFilter.isNullOrBlank()) {
            baseList
        } else {
            baseList.filter { it.riskLevel.equals(state.selectedRiskFilter, ignoreCase = true) }
        }

        val searchFiltered = if (state.searchQuery.isBlank()) {
            riskFiltered
        } else {
            val q = state.searchQuery.trim()
            riskFiltered.filter {
                it.id.contains(q, ignoreCase = true) ||
                it.title.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true) ||
                it.proposedSteps.any { step -> step.contains(q, ignoreCase = true) }
            }
        }

        return state.copy(filteredRfcs = searchFiltered)
    }

    fun refresh(hostId: Int = defaultHostId): Job = currentScope.launch {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val result = rfcRepository.getRfcs(hostId, forceRefresh = true)
        if (result.isFailure) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to refresh RFCs"
                )
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun approveRfc(rfcId: String, hostId: Int = defaultHostId): Job = currentScope.launch {
        if (_uiState.value.votingInProgressId != null) return@launch // Debounce in-flight vote

        _uiState.update { it.copy(votingInProgressId = rfcId, errorMessage = null) }
        val result = rfcRepository.voteRfc(rfcId = rfcId, decision = "approve", hostId = hostId)
        _uiState.update { it.copy(votingInProgressId = null) }

        if (result.isSuccess) {
            _uiState.update {
                it.copy(
                    userNotice = "$rfcId Approved",
                    selectedRfc = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to approve $rfcId"
                )
            }
        }
    }

    fun approveRfc(rfc: RfcItem, hostId: Int = defaultHostId): Job = approveRfc(rfc.id, hostId)
    fun approveRfc(rfc: RfcItemEntity, hostId: Int = defaultHostId): Job = approveRfc(rfc.rfcNumber, hostId)

    fun rejectRfc(rfcId: String, hostId: Int = defaultHostId): Job = currentScope.launch {
        if (_uiState.value.votingInProgressId != null) return@launch // Debounce in-flight vote

        _uiState.update { it.copy(votingInProgressId = rfcId, errorMessage = null) }
        val result = rfcRepository.voteRfc(rfcId = rfcId, decision = "deny", hostId = hostId)
        _uiState.update { it.copy(votingInProgressId = null) }

        if (result.isSuccess) {
            _uiState.update {
                it.copy(
                    userNotice = "$rfcId Rejected",
                    selectedRfc = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to reject $rfcId"
                )
            }
        }
    }

    fun rejectRfc(rfc: RfcItem, hostId: Int = defaultHostId): Job = rejectRfc(rfc.id, hostId)
    fun rejectRfc(rfc: RfcItemEntity, hostId: Int = defaultHostId): Job = rejectRfc(rfc.rfcNumber, hostId)

    fun selectRfc(rfc: RfcItem?) {
        _uiState.update { it.copy(selectedRfc = rfc) }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { state ->
            val filter = when (tab) {
                0 -> RfcFilter.PENDING
                1 -> RfcFilter.ALL
                else -> state.selectedFilter
            }
            computeFilteredState(state.copy(selectedTab = tab, selectedFilter = filter))
        }
    }

    fun setFilter(filter: RfcFilter) {
        _uiState.update { state ->
            val tab = when (filter) {
                RfcFilter.PENDING -> 0
                RfcFilter.ALL, RfcFilter.APPROVED, RfcFilter.REJECTED -> 1
            }
            computeFilteredState(state.copy(selectedFilter = filter, selectedTab = tab))
        }
    }

    fun setRiskFilter(risk: String?) {
        _uiState.update { state ->
            computeFilteredState(state.copy(selectedRiskFilter = risk))
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            computeFilteredState(state.copy(searchQuery = query))
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
