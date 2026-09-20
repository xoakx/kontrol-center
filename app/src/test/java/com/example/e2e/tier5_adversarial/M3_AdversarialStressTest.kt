package com.example.e2e.tier5_adversarial

import com.example.data.api.ArcadeApiService
import com.example.data.api.CrowdSecBouncerItem
import com.example.data.api.CrowdSecDecisionItem
import com.example.data.api.CrowdSecDecisionsResponse
import com.example.data.api.FirewallStatusResponse
import com.example.data.api.NetSecOverviewResponse
import com.example.data.api.SuricataAlertDetails
import com.example.data.api.SuricataAlertItem
import com.example.data.api.SuricataAlertsResponse
import com.example.data.api.TetragonStatusResponse
import com.example.data.api.UnbanRequest
import com.example.data.api.UnbanResponse
import com.example.data.entity.HostEntity
import com.example.data.repository.NetSecRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.service.SshCommandResult
import com.example.viewmodel.NetSecViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class M3_AdversarialStressTest : E2eTestHarness() {

    private val targetHost = HostEntity(
        id = 1,
        name = "fml",
        address = "100.111.123.93",
        sshPort = 22,
        username = "kms",
        sshPrivateKey = "fake_rsa_key"
    )

    // Helper to generate N diverse alerts for stress testing
    private fun generateAlerts(count: Int): List<SuricataAlertItem> {
        val protocols = listOf("TCP", "UDP", "ICMP", "HTTP")
        val severities = listOf(1, 2, 3)
        return (1..count).map { i ->
            val sev = severities[i % severities.size]
            val proto = protocols[i % protocols.size]
            SuricataAlertItem(
                timestamp = "2026-09-20T01:00:${(i % 60).toString().padStart(2, '0')}.000Z",
                srcIp = "198.51.100.${i % 250 + 1}",
                destIp = "192.168.1.161",
                destPort = 8000 + (i % 1000),
                proto = proto,
                alert = SuricataAlertDetails(
                    signature = when (sev) {
                        1 -> "ET EXPLOIT Critical Vulnerability Attack Pattern #$i"
                        2 -> "ET SCAN Potential Reconnaissance SYN Scan #$i"
                        else -> "ET INFO Informational DNS Lookup Event #$i"
                    },
                    category = if (sev == 1) "Exploit" else "Recon",
                    severity = sev,
                    signatureId = (2000000 + i).toLong()
                )
            )
        }
    }

    // =========================================================================
    // SECTION 1: COROUTINE CANCELLATION & FALLBACK IMMUNITY
    // =========================================================================

    @Test(timeout = 10000)
    fun test01_unbanIp_coroutineCancelledDuringHttp_rethrowsAndNeverCallsSsh() = runBlocking {
        val sshInvocations = AtomicInteger(0)
        val httpStarted = CompletableDeferred<Unit>()
        val httpHold = CompletableDeferred<Unit>()

        val blockingApiService = object : ArcadeApiService by apiService {
            override suspend fun unbanIp(body: UnbanRequest): UnbanResponse {
                httpStarted.complete(Unit)
                httpHold.await() // suspend indefinitely until cancelled
                return UnbanResponse(success = true, ip = body.ip)
            }
        }

        val repo = NetSecRepository(
            apiService = blockingApiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, _ ->
                sshInvocations.incrementAndGet()
                SshCommandResult(exitCode = 0, stdout = "Deleted", stderr = "")
            }
        )

        // Seed initial bans and overview
        repo.getCrowdSecDecisions()
        repo.getNetSecOverview()
        val initialBanCount = repo.crowdSecDecisions.value.size
        assertTrue("Pre-condition: must have bans", initialBanCount > 0)
        val initialOverviewCount = repo.overview.value?.crowdsecBanCount ?: 0

        // Launch unban in a separate job
        var caughtCancellation = false
        val job = launch(Dispatchers.IO) {
            try {
                repo.unbanIp("209.99.190.113", targetHost)
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        // Wait until HTTP call has started
        httpStarted.await()

        // Cancel the job while it is waiting in HTTP call
        job.cancel(CancellationException("Client cancelled in-flight unban request"))
        job.join()

        assertTrue("Job must be cancelled", job.isCancelled)
        assertTrue("CancellationException must be propagated out", caughtCancellation)
        assertEquals("CRITICAL: SSH fallback must NEVER be invoked on cancellation!", 0, sshInvocations.get())

        // Verify optimistic eviction was NOT performed
        assertEquals("Ban list must remain unevicted on cancellation", initialBanCount, repo.crowdSecDecisions.value.size)
        assertEquals("Ban count in overview must remain unchanged", initialOverviewCount, repo.overview.value?.crowdsecBanCount)
    }

    @Test(timeout = 10000)
    fun test02_unbanIp_coroutineCancelledDuringSshFallback_rethrowsAndLeavesStateIntact() = runBlocking {
        val sshStarted = CompletableDeferred<Unit>()
        val sshHold = CompletableDeferred<Unit>()

        // HTTP fails immediately with 503
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 503, """{"error": "LAPI busy"}""")

        val repo = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, _ ->
                sshStarted.complete(Unit)
                sshHold.await() // suspend indefinitely inside SSH fallback
                SshCommandResult(exitCode = 0, stdout = "Deleted", stderr = "")
            }
        )

        repo.getCrowdSecDecisions()
        val initialBans = repo.crowdSecDecisions.value.size

        var caughtCancellation = false
        val job = launch(Dispatchers.IO) {
            try {
                repo.unbanIp("209.99.190.113", targetHost)
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        // Wait until SSH fallback is executing
        sshStarted.await()

        // Cancel during SSH fallback
        job.cancel(CancellationException("Cancelled during SSH fallback"))
        job.join()

        assertTrue("Job must be cancelled", job.isCancelled)
        assertTrue("CancellationException must be propagated", caughtCancellation)
        assertEquals("Bans must not be evicted", initialBans, repo.crowdSecDecisions.value.size)
    }

    @Test(timeout = 10000)
    fun test03_refreshAll_coroutineCancelled_rethrowsCancellation() = runBlocking {
        val overviewStarted = CompletableDeferred<Unit>()
        val overviewHold = CompletableDeferred<Unit>()

        val blockingApiService = object : ArcadeApiService by apiService {
            override suspend fun getNetSecOverview(): NetSecOverviewResponse {
                overviewStarted.complete(Unit)
                overviewHold.await()
                return apiService.getNetSecOverview()
            }
        }

        val repo = NetSecRepository(apiService = blockingApiService)

        var caughtCancellation = false
        val job = launch(Dispatchers.IO) {
            try {
                repo.refreshAll()
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        overviewStarted.await()
        job.cancel(CancellationException("User navigated away"))
        job.join()

        assertTrue("Job must be cancelled", job.isCancelled)
        assertTrue("CancellationException must be propagated out of refreshAll", caughtCancellation)
    }

    // =========================================================================
    // SECTION 2: DUAL-TRANSPORT FALLBACK CHALLENGES
    // =========================================================================

    @Test(timeout = 10000)
    fun test04_dualTransport_http503_executesExactCscliCommand_andEvicts() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 503, """{"error": "CrowdSec LAPI overloaded"}""")

        val executedCommands = mutableListOf<String>()
        val repo = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, cmd ->
                executedCommands.add(cmd)
                SshCommandResult(exitCode = 0, stdout = "1 decision(s) deleted", stderr = "")
            }
        )

        repo.getCrowdSecDecisions()
        repo.getNetSecOverview()
        assertEquals(2, repo.crowdSecDecisions.value.size)

        val targetIp = "209.99.190.113"
        val result = repo.unbanIp(targetIp)

        assertTrue("Fallback unban should succeed", result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response?.success == true)
        assertEquals("Decision for $targetIp deleted via SSH fallback", response?.message)
        assertEquals("1 decision(s) deleted", response?.output)

        assertEquals(1, executedCommands.size)
        assertEquals("sudo cscli decisions delete -i $targetIp", executedCommands[0])

        // Verify optimistic eviction
        assertEquals(1, repo.crowdSecDecisions.value.size)
        assertEquals("198.51.100.4", repo.crowdSecDecisions.value[0].value)
        assertEquals(1, repo.overview.value?.crowdsecBanCount)
    }

    @Test(timeout = 10000)
    fun test05_dualTransport_httpNetworkFailure_executesSshFallback() = runBlocking {
        dispatcher.overrideResponse("/api/netsec/crowdsec/unban") {
            throw IOException("Connection reset by peer (TCP RST)")
        }

        val executedCommands = mutableListOf<String>()
        val repo = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, cmd ->
                executedCommands.add(cmd)
                SshCommandResult(exitCode = 0, stdout = "1 decision(s) deleted", stderr = "")
            }
        )

        repo.getCrowdSecDecisions()
        val result = repo.unbanIp("198.51.100.4")

        assertTrue(result.isSuccess)
        assertEquals("sudo cscli decisions delete -i 198.51.100.4", executedCommands.firstOrNull())
        assertEquals(1, repo.crowdSecDecisions.value.size)
    }

    @Test(timeout = 10000)
    fun test06_dualTransport_cidrRange_unbanFallbackSucceeds() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 500, """{"error": "Internal Error"}""")

        val executedCommands = mutableListOf<String>()
        val repo = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, cmd ->
                executedCommands.add(cmd)
                SshCommandResult(exitCode = 0, stdout = "1 decision(s) deleted", stderr = "")
            }
        )

        val cidr = "198.51.100.0/24"
        val result = repo.unbanIp(cidr)

        assertTrue(result.isSuccess)
        assertEquals("sudo cscli decisions delete -i $cidr", executedCommands.firstOrNull())
    }

    @Test(timeout = 10000)
    fun test07_dualTransport_sshNonZeroExit_failsAndPreservesState() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 500, """{"error": "Internal Error"}""")

        val repo = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, _ ->
                SshCommandResult(exitCode = 127, stdout = "", stderr = "sudo: cscli: command not found")
            }
        )

        repo.getCrowdSecDecisions()
        repo.getNetSecOverview()
        val beforeDecisions = repo.crowdSecDecisions.value
        val beforeCount = repo.overview.value?.crowdsecBanCount

        val result = repo.unbanIp("209.99.190.113")

        assertFalse("Unban should fail when SSH returns non-zero", result.isSuccess)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue("Error message must capture SSH stderr: ${ex?.message}",
            ex?.message?.contains("SSH stderr: sudo: cscli: command not found") == true)

        // Decisions must NOT be evicted
        assertEquals(beforeDecisions, repo.crowdSecDecisions.value)
        assertEquals(beforeCount, repo.overview.value?.crowdsecBanCount)
    }

    @Test(timeout = 10000)
    fun test08_dualTransport_sshThrowsException_failsGracefully() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 500, """{"error": "Internal Error"}""")

        val repo = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, _ ->
                throw IOException("SSH session dropped: host key verification failed")
            }
        )

        repo.getCrowdSecDecisions()
        val beforeDecisions = repo.crowdSecDecisions.value

        val result = repo.unbanIp("209.99.190.113")

        assertFalse(result.isSuccess)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IOException)
        assertTrue(ex?.message?.contains("host key verification failed") == true)
        assertEquals(beforeDecisions, repo.crowdSecDecisions.value)
    }

    @Test(timeout = 10000)
    fun test09_adversarial_injectionPayloads_blockedClientSide_noHttpNoSsh() = runBlocking {
        val sshCalls = AtomicInteger(0)
        val httpCalls = AtomicInteger(0)

        val monitoredApiService = object : ArcadeApiService by apiService {
            override suspend fun unbanIp(body: UnbanRequest): UnbanResponse {
                httpCalls.incrementAndGet()
                return apiService.unbanIp(body)
            }
        }

        val repo = NetSecRepository(
            apiService = monitoredApiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, _ ->
                sshCalls.incrementAndGet()
                SshCommandResult(0, "", "")
            }
        )

        val maliciousInputs = listOf(
            "192.168.1.1; rm -rf /",
            "192.168.1.1 | rm -rf /",
            "100.111.123.93 && cat /etc/shadow",
            "100.111.123.93`id`",
            "$(cat /etc/passwd)",
            "10.0.0.1\nsudo reboot",
            "999.999.999.999",
            "10.0.0.1/33",
            "not-an-ip-address"
        )

        for (badInput in maliciousInputs) {
            val result = repo.unbanIp(badInput)
            assertFalse("Malicious input '$badInput' must fail validation", result.isSuccess)
            assertTrue("Expected IllegalArgumentException for '$badInput'",
                result.exceptionOrNull() is IllegalArgumentException)
        }

        assertEquals("Zero HTTP calls should have been made for malicious inputs", 0, httpCalls.get())
        assertEquals("Zero SSH calls should have been made for malicious inputs", 0, sshCalls.get())
    }

    // =========================================================================
    // SECTION 3: VIEWMODEL REACTIVITY UNDER 50+ CONCURRENT OPERATIONS
    // =========================================================================

    @Test(timeout = 25000)
    fun test10_viewModel_reactivity_under_50_concurrent_filter_and_search_operations() = runBlocking {
        // Generate 60 diverse alerts for rich filtering
        val syntheticAlerts = generateAlerts(60)
        val syntheticDecisions = (1..10).map { i ->
            CrowdSecDecisionItem(
                id = i.toLong(),
                origin = "crowdsec",
                type = "ban",
                scope = "Ip",
                value = "198.51.100.$i",
                duration = "4h",
                until = "",
                scenario = "crowdsecurity/http-bf"
            )
        }

        val customApiService = object : ArcadeApiService by apiService {
            override suspend fun getSuricataAlerts(limit: Int): SuricataAlertsResponse {
                return SuricataAlertsResponse(alerts = syntheticAlerts, count = syntheticAlerts.size)
            }
            override suspend fun getCrowdSecDecisions(): CrowdSecDecisionsResponse {
                return CrowdSecDecisionsResponse(activeDecisions = syntheticDecisions, bouncers = emptyList())
            }
            override suspend fun getNetSecOverview(): NetSecOverviewResponse {
                return NetSecOverviewResponse(
                    status = "healthy",
                    suricataActive = true,
                    crowdsecActive = true,
                    suricataAlertCount = syntheticAlerts.size,
                    crowdsecBanCount = syntheticDecisions.size,
                    totalDroppedPackets = 1000L
                )
            }
        }

        val dedicatedDispatcher = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
        val customScope = CoroutineScope(dedicatedDispatcher)

        val repo = NetSecRepository(apiService = customApiService, ioDispatcher = dedicatedDispatcher)
        val vm = NetSecViewModel(repository = repo, scope = customScope)

        // Wait for initial load
        vm.refresh().join()
        assertEquals(60, vm.uiState.value.suricataAlerts.size)

        // Launch 60 concurrent worker coroutines
        val errors = ConcurrentLinkedQueue<Throwable>()
        val jobs = mutableListOf<Job>()

        val queries = listOf("Critical", "SYN Scan", "DNS Lookup", "198.51.100.", "192.168", "NonExistent")
        val severities = listOf(1, 2, 3, null)

        for (i in 1..60) {
            val job = customScope.launch {
                try {
                    when (i % 4) {
                        0 -> {
                            val sev = severities[i % severities.size]
                            vm.setSeverityFilter(sev)
                        }
                        1 -> {
                            val q = queries[i % queries.size]
                            vm.setSearchQuery(q)
                        }
                        2 -> {
                            val alert = if (i % 2 == 0) syntheticAlerts[i % syntheticAlerts.size] else null
                            vm.selectAlert(alert)
                        }
                        3 -> {
                            vm.refresh().join()
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
            jobs.add(job)
        }

        // Await all concurrent tasks
        jobs.joinAll()

        assertTrue("Zero exceptions must occur during concurrent mutations: ${errors.firstOrNull()?.message}",
            errors.isEmpty())

        // Validate final state consistency: filteredAlerts must accurately reflect final filters
        val finalState = vm.uiState.value
        assertFalse("ViewModel should not be permanently stuck in loading", finalState.isLoading)
        assertNull("ViewModel should not have unexpected errors", finalState.errorMessage)

        val expectedFiltered = finalState.suricataAlerts.filter { alert ->
            val matchSev = finalState.selectedSeverityFilter == null || alert.alert.severity == finalState.selectedSeverityFilter
            val q = finalState.searchQuery.trim()
            val matchQuery = q.isEmpty() || (
                alert.srcIp.contains(q, ignoreCase = true) ||
                alert.destIp.contains(q, ignoreCase = true) ||
                alert.alert.signature.contains(q, ignoreCase = true) ||
                alert.alert.category.contains(q, ignoreCase = true)
            )
            matchSev && matchQuery
        }

        assertEquals("Filtered alerts must strictly match current filter and search predicates",
            expectedFiltered.size, finalState.filteredAlerts.size)

        // Verify health evaluation integrity
        val expectedHealth = NetSecViewModel.evaluateOverallHealth(finalState.overview, finalState.suricataAlerts)
        assertEquals(expectedHealth, finalState.overallHealth)
    }

    @Test(timeout = 25000)
    fun test11_viewModel_concurrent_unban_operations_consistency() = runBlocking {
        val initialDecisions = (1..50).map { i ->
            CrowdSecDecisionItem(
                id = i.toLong(),
                origin = "crowdsec",
                type = "ban",
                scope = "Ip",
                value = "10.0.0.$i",
                duration = "4h",
                until = "",
                scenario = "ssh-bf"
            )
        }

        val unbannedIps = ConcurrentLinkedQueue<String>()
        val customApiService = object : ArcadeApiService by apiService {
            override suspend fun getCrowdSecDecisions(): CrowdSecDecisionsResponse {
                return CrowdSecDecisionsResponse(activeDecisions = initialDecisions, bouncers = emptyList())
            }
            override suspend fun unbanIp(body: UnbanRequest): UnbanResponse {
                unbannedIps.add(body.ip)
                return UnbanResponse(success = true, ip = body.ip, message = "Decision for ${body.ip} deleted")
            }
            override suspend fun getNetSecOverview(): NetSecOverviewResponse {
                return NetSecOverviewResponse(
                    status = "healthy",
                    suricataActive = true,
                    crowdsecActive = true,
                    suricataAlertCount = 0,
                    crowdsecBanCount = initialDecisions.size,
                    totalDroppedPackets = 0L
                )
            }
        }

        val testPool = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
        val customScope = CoroutineScope(testPool)

        val repo = NetSecRepository(apiService = customApiService, ioDispatcher = testPool)
        val vm = NetSecViewModel(repository = repo, scope = customScope)

        vm.refresh().join()
        assertEquals(50, repo.crowdSecDecisions.value.size)

        // Dispatch 50 concurrent unbans
        val jobs = (1..50).map { i ->
            vm.unbanIp("10.0.0.$i")
        }

        jobs.joinAll()

        assertEquals("All 50 unbans should have reached the API", 50, unbannedIps.size)
        assertEquals("All 50 bans should have been optimistically evicted", 0, repo.crowdSecDecisions.value.size)
        assertEquals("Overview ban count should be decremented to 0", 0, repo.overview.value?.crowdsecBanCount)
    }

    @Test(timeout = 10000)
    fun test12_viewModel_rapidTypingSimulation_noStalePredicates() = runBlocking {
        val syntheticAlerts = generateAlerts(30)
        val customApiService = object : ArcadeApiService by apiService {
            override suspend fun getSuricataAlerts(limit: Int): SuricataAlertsResponse {
                return SuricataAlertsResponse(alerts = syntheticAlerts, count = syntheticAlerts.size)
            }
        }

        val repo = NetSecRepository(apiService = customApiService)
        val vm = NetSecViewModel(repository = repo, scope = CoroutineScope(Dispatchers.IO))

        vm.refresh().join()

        // Simulate rapid keystrokes: "C", "Cr", "Cri", "Crit", "Criti", "Critic", "Critical"
        val keystrokes = listOf("C", "Cr", "Cri", "Crit", "Criti", "Critic", "Critical")
        for (stroke in keystrokes) {
            vm.setSearchQuery(stroke)
        }

        val finalState = vm.uiState.value
        assertEquals("Critical", finalState.searchQuery)
        assertTrue(finalState.filteredAlerts.all {
            it.alert.signature.contains("Critical", ignoreCase = true)
        })
    }

    @Test(timeout = 10000)
    fun test13_refreshAll_cancelledDuringTetragonStatus_rethrowsCancellation() = runBlocking {
        val tetragonStarted = CompletableDeferred<Unit>()
        val tetragonHold = CompletableDeferred<Unit>()

        val blockingApiService = object : ArcadeApiService by apiService {
            override suspend fun getTetragonStatus(): TetragonStatusResponse {
                tetragonStarted.complete(Unit)
                tetragonHold.await()
                return TetragonStatusResponse()
            }
        }

        val repo = NetSecRepository(apiService = blockingApiService)

        var caughtCancellation = false
        val job = launch(Dispatchers.IO) {
            try {
                repo.refreshAll()
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        tetragonStarted.await()
        job.cancel(CancellationException("Cancelled during Tetragon fetch"))
        job.join()

        assertTrue("Job must be cancelled", job.isCancelled)
        assertTrue("CancellationException must be propagated out of refreshAll when cancelled during Tetragon fetch", caughtCancellation)
    }

    @Test(timeout = 10000)
    fun test14_refreshAll_cancelledDuringFirewallStatus_rethrowsCancellation() = runBlocking {
        val fwStarted = CompletableDeferred<Unit>()
        val fwHold = CompletableDeferred<Unit>()

        val blockingApiService = object : ArcadeApiService by apiService {
            override suspend fun getFirewallStatus(): FirewallStatusResponse {
                fwStarted.complete(Unit)
                fwHold.await()
                return FirewallStatusResponse()
            }
        }

        val repo = NetSecRepository(apiService = blockingApiService)

        var caughtCancellation = false
        val job = launch(Dispatchers.IO) {
            try {
                repo.refreshAll()
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        fwStarted.await()
        job.cancel(CancellationException("Cancelled during Firewall fetch"))
        job.join()

        assertTrue("Job must be cancelled", job.isCancelled)
        assertTrue("CancellationException must be propagated out of refreshAll when cancelled during Firewall fetch", caughtCancellation)
    }
}
