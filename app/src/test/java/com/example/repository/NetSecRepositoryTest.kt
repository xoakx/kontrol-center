package com.example.repository

import com.example.data.api.ArcadeApiService
import com.example.data.api.FirewallStatusResponse
import com.example.data.api.NetSecOverviewResponse
import com.example.data.api.TetragonStatusResponse
import com.example.data.api.UnbanRequest
import com.example.data.api.UnbanResponse
import com.example.data.entity.HostEntity
import com.example.data.repository.NetSecRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.service.SshCommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetSecRepositoryTest : E2eTestHarness() {

    private lateinit var netSecRepository: NetSecRepository

    @Before
    override fun setUp() {
        super.setUp()
        netSecRepository = NetSecRepository(apiService = apiService)
    }

    @Test
    fun testGetOverview_success() = runBlocking {
        val result = netSecRepository.getNetSecOverview()
        assertTrue(result.isSuccess)
        val overview = result.getOrNull()
        assertNotNull(overview)
        assertEquals("healthy", overview?.status)
        assertEquals(5, overview?.suricataAlertCount)
        assertEquals(2, overview?.crowdsecBanCount)
        assertEquals(14820L, overview?.totalDroppedPackets)
        assertTrue(overview?.suricataActive == true)
        assertTrue(overview?.crowdsecActive == true)

        assertEquals(overview, netSecRepository.overview.value)
    }

    @Test
    fun testGetOverview_httpError() = runBlocking {
        dispatcher.setResponse("/api/netsec/overview", 500, """{"error": "Internal Server Error"}""")

        val result = netSecRepository.getNetSecOverview()
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
    }

    @Test
    fun testGetAlerts_success() = runBlocking {
        val result = netSecRepository.getSuricataAlerts(50)
        assertTrue(result.isSuccess)
        val alerts = result.getOrNull()
        assertNotNull(alerts)
        assertEquals(3, alerts?.size)

        val alert1 = alerts?.get(0)
        assertEquals("203.0.113.195", alert1?.srcIp)
        assertEquals(1, alert1?.alert?.severity)
        assertEquals("ET EXPLOIT Remote Command Execution Attempt", alert1?.alert?.signature)

        assertEquals(3, netSecRepository.suricataAlerts.value.size)
    }

    @Test
    fun testGetDecisions_success() = runBlocking {
        val result = netSecRepository.getCrowdSecDecisions()
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response?.activeDecisions?.size)
        assertEquals(2, response?.bouncers?.size)

        assertEquals(2, netSecRepository.crowdSecDecisions.value.size)
        assertEquals(2, netSecRepository.bouncers.value.size)
    }

    @Test
    fun testUnbanIp_success_evictsFromDecisionsFlow() = runBlocking {
        netSecRepository.getNetSecOverview()
        netSecRepository.getCrowdSecDecisions()
        assertEquals(2, netSecRepository.crowdSecDecisions.value.size)
        assertEquals(2, netSecRepository.overview.value?.crowdsecBanCount)

        val targetIp = "209.99.190.113"
        val result = netSecRepository.unbanIp(targetIp)
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response?.success == true)
        assertEquals(targetIp, response?.ip)

        val remaining = netSecRepository.crowdSecDecisions.value
        assertEquals(1, remaining.size)
        assertEquals("198.51.100.4", remaining[0].value)

        assertEquals(1, netSecRepository.overview.value?.crowdsecBanCount)
    }

    @Test
    fun testUnbanIp_invalidIp_failsClientSide() = runBlocking {
        val injectionAttack = "192.168.1.1; rm -rf /"
        val result = netSecRepository.unbanIp(injectionAttack)
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex?.message?.contains("Invalid IPv4 address or CIDR format") == true)
    }

    @Test
    fun testUnbanIp_httpFails_sshFallbackSucceeds() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 503, """{"error": "CrowdSec LAPI busy"}""")

        val targetHost = HostEntity(
            id = 1,
            name = "fml",
            address = "100.111.123.93",
            sshPort = 22,
            username = "kms",
            sshPrivateKey = "fake_rsa_key"
        )

        var executedSshCmd: String? = null
        val repoWithSsh = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, cmd ->
                executedSshCmd = cmd
                SshCommandResult(exitCode = 0, stdout = "1 decision(s) deleted", stderr = "")
            }
        )

        repoWithSsh.getCrowdSecDecisions()
        assertEquals(2, repoWithSsh.crowdSecDecisions.value.size)

        val ipToUnban = "209.99.190.113"
        val result = repoWithSsh.unbanIp(ipToUnban)
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response?.success == true)
        assertTrue(response?.message?.contains("SSH fallback") == true)
        assertEquals("sudo cscli decisions delete -i $ipToUnban", executedSshCmd)

        // Verify evicted
        assertEquals(1, repoWithSsh.crowdSecDecisions.value.size)
        assertEquals("198.51.100.4", repoWithSsh.crowdSecDecisions.value[0].value)
    }

    @Test
    fun testUnbanIp_httpAndSshBothFail_returnsFailure() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 503, """{"error": "CrowdSec LAPI busy"}""")

        val targetHost = HostEntity(
            id = 1,
            name = "fml",
            address = "100.111.123.93",
            sshPort = 22,
            username = "kms",
            sshPrivateKey = "fake_rsa_key"
        )

        val repoWithFailingSsh = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, _ ->
                SshCommandResult(exitCode = 1, stdout = "", stderr = "Permission denied executing cscli")
            }
        )

        repoWithFailingSsh.getCrowdSecDecisions()
        assertEquals(2, repoWithFailingSsh.crowdSecDecisions.value.size)

        val result = repoWithFailingSsh.unbanIp("209.99.190.113")
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("SSH stderr: Permission denied") == true)

        // Decisions list uncorrupted
        assertEquals(2, repoWithFailingSsh.crowdSecDecisions.value.size)
    }

    @Test
    fun testUnbanIp_cancellationException_rethrows() {
        var caughtCancellation = false
        try {
            runBlocking {
                val cancellingService = object : ArcadeApiService by apiService {
                    override suspend fun unbanIp(body: UnbanRequest): UnbanResponse {
                        throw CancellationException("Simulated coroutine cancellation")
                    }
                }
                val repo = NetSecRepository(apiService = cancellingService)
                repo.unbanIp("209.99.190.113")
            }
        } catch (e: CancellationException) {
            caughtCancellation = true
            assertTrue(e.message?.contains("Simulated") == true)
        }
        assertTrue("CancellationException should have been thrown out of repo", caughtCancellation)
    }

    @Test
    fun testGetOverview_cancellationException_rethrows() {
        var caughtCancellation = false
        try {
            runBlocking {
                val cancellingService = object : ArcadeApiService by apiService {
                    override suspend fun getNetSecOverview(): NetSecOverviewResponse {
                        throw CancellationException("Simulated coroutine cancellation")
                    }
                }
                val repo = NetSecRepository(apiService = cancellingService)
                repo.getNetSecOverview()
            }
        } catch (e: CancellationException) {
            caughtCancellation = true
            assertTrue(e.message?.contains("Simulated") == true)
        }
        assertTrue("CancellationException should have been thrown out of repo", caughtCancellation)
    }

    @Test
    fun testFilterAlertsBySeverity() = runBlocking {
        netSecRepository.getSuricataAlerts(50)
        assertEquals(3, netSecRepository.suricataAlerts.value.size)

        val critAlerts = netSecRepository.getAlertsBySeverity(1)
        assertEquals(1, critAlerts.size)
        assertEquals("ET EXPLOIT Remote Command Execution Attempt", critAlerts[0].alert.signature)

        val warnAlerts = netSecRepository.getAlertsBySeverity(2)
        assertEquals(1, warnAlerts.size)
        assertEquals("ET SCAN Nmap SYN Scan", warnAlerts[0].alert.signature)

        val infoAlerts = netSecRepository.getAlertsBySeverity(3)
        assertEquals(1, infoAlerts.size)
        assertEquals("ET INFO DNS Query for Known Domain", infoAlerts[0].alert.signature)
    }

    @Test
    fun testSearchAlerts() = runBlocking {
        netSecRepository.getSuricataAlerts(50)

        val byIp = netSecRepository.searchAlerts("203.0.113.195")
        assertEquals(1, byIp.size)
        assertEquals("203.0.113.195", byIp[0].srcIp)

        val bySignature = netSecRepository.searchAlerts("SYN Scan")
        assertEquals(1, bySignature.size)
        assertEquals("ET SCAN Nmap SYN Scan", bySignature[0].alert.signature)

        val notFound = netSecRepository.searchAlerts("non_existent_payload_12345")
        assertEquals(0, notFound.size)
    }

    @Test
    fun testUnbanIp_apiReturnsSuccessFalse_triggersSshFallback() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 200, """{"success": false, "message": "Decision not found"}""")

        val targetHost = HostEntity(
            id = 1,
            name = "fml",
            address = "100.111.123.93",
            sshPort = 22,
            username = "kms",
            sshPrivateKey = "fake_rsa_key"
        )

        var executedSshCmd: String? = null
        val repoWithSsh = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, cmd ->
                executedSshCmd = cmd
                SshCommandResult(exitCode = 0, stdout = "1 decision(s) deleted", stderr = "")
            }
        )

        repoWithSsh.getCrowdSecDecisions()
        assertEquals(2, repoWithSsh.crowdSecDecisions.value.size)

        val ipToUnban = "209.99.190.113"
        val result = repoWithSsh.unbanIp(ipToUnban)
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response?.success == true)
        assertTrue(response?.message?.contains("SSH fallback") == true)
        assertEquals("sudo cscli decisions delete -i $ipToUnban", executedSshCmd)

        // Verify evicted
        assertEquals(1, repoWithSsh.crowdSecDecisions.value.size)
    }

    @Test
    fun testUnbanIp_apiReturnsSuccessFalse_noSshHost_returnsFailure() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 200, """{"success": false, "message": "Decision not found"}""")

        val result = netSecRepository.unbanIp("209.99.190.113")
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IOException)
        assertTrue(ex?.message?.contains("Decision not found") == true)
    }

    @Test
    fun testUnbanIp_cidrRange_usesRangeFlagInSshFallback() = runBlocking {
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 503, """{"error": "CrowdSec LAPI busy"}""")

        val targetHost = HostEntity(
            id = 1,
            name = "fml",
            address = "100.111.123.93",
            sshPort = 22,
            username = "kms",
            sshPrivateKey = "fake_rsa_key"
        )

        var executedSshCmd: String? = null
        val repoWithSsh = NetSecRepository(
            apiService = apiService,
            hostProvider = { targetHost },
            sshCommandExecutor = { _, cmd ->
                executedSshCmd = cmd
                SshCommandResult(exitCode = 0, stdout = "1 decision(s) deleted", stderr = "")
            }
        )

        val cidrToUnban = "198.51.100.0/24"
        val result = repoWithSsh.unbanIp(cidrToUnban)
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response?.success == true)
        assertEquals("sudo cscli decisions delete -r $cidrToUnban", executedSshCmd)
    }

    @Test
    fun testRefreshAll_cancellationExceptionInTetragon_rethrows() {
        var caughtCancellation = false
        try {
            runBlocking {
                val cancellingService = object : ArcadeApiService by apiService {
                    override suspend fun getTetragonStatus(): TetragonStatusResponse {
                        throw CancellationException("Simulated coroutine cancellation in Tetragon")
                    }
                }
                val repo = NetSecRepository(apiService = cancellingService)
                repo.refreshAll()
            }
        } catch (e: CancellationException) {
            caughtCancellation = true
            assertTrue(e.message?.contains("Simulated") == true)
        }
        assertTrue("CancellationException from Tetragon should have been rethrown out of refreshAll", caughtCancellation)
    }

    @Test
    fun testRefreshAll_cancellationExceptionInFirewall_rethrows() {
        var caughtCancellation = false
        try {
            runBlocking {
                val cancellingService = object : ArcadeApiService by apiService {
                    override suspend fun getFirewallStatus(): FirewallStatusResponse {
                        throw CancellationException("Simulated coroutine cancellation in Firewall")
                    }
                }
                val repo = NetSecRepository(apiService = cancellingService)
                repo.refreshAll()
            }
        } catch (e: CancellationException) {
            caughtCancellation = true
            assertTrue(e.message?.contains("Simulated") == true)
        }
        assertTrue("CancellationException from Firewall should have been rethrown out of refreshAll", caughtCancellation)
    }
}
