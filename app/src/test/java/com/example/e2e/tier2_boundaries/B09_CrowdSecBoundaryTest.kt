package com.example.e2e.tier2_boundaries

import com.example.data.api.UnbanRequest
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketException

/**
 * Tier 2 Boundary Tests: B09 CrowdSec Boundary.
 * Covers 5 boundary conditions:
 * - T2_B09_01: Invalid IP format and injection payload rejection
 * - T2_B09_02: Already-expired / non-existent decision unban handling (404)
 * - T2_B09_03: Network drop during unban dispatch (SocketException)
 * - T2_B09_04: CIDR subnet range unban handling (e.g. 198.51.100.0/24)
 * - T2_B09_05: CrowdSec bouncer offline / invalid status detection
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B09_CrowdSecBoundaryTest : E2eTestHarness() {

    private val ipRegex = Regex("""^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\/([0-9]|[1-2][0-9]|3[0-2]))?$""")

    @Test
    fun T2_B09_01_invalid_ip_format_rejection() {
        val invalidIps = listOf(
            "",
            "   ",
            "not_an_ip",
            "999.999.999.999",
            "192.168.1.1.1",
            "192.168.1.1/33",
            "192.168.1.1; rm -rf /",
            "100.111.123.93' OR '1'='1"
        )

        for (invalid in invalidIps) {
            assertFalse("Expected IP '$invalid' to fail validation", invalid.matches(ipRegex))
        }
    }

    @Test
    fun T2_B09_02_already_expired_decision_unban() = runBlocking {
        dispatcher.setResponse(
            "/api/netsec/crowdsec/unban",
            404,
            """{"detail": "Decision for IP 192.0.2.99 not found or already expired"}"""
        )

        try {
            apiService.unbanIp(UnbanRequest(ip = "192.0.2.99"))
            fail("Expected HttpException(404)")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
            val body = e.response()?.errorBody()?.string()
            assertTrue(body?.contains("not found or already expired") == true)
        }
    }

    @Test
    fun T2_B09_03_network_drop_during_unban_dispatch() = runBlocking {
        dispatcher.overrideResponse("/api/netsec/crowdsec/unban") {
            throw SocketException("Connection aborted by peer mid-request")
        }

        try {
            apiService.unbanIp(UnbanRequest(ip = "209.99.190.113"))
            fail("Expected SocketException")
        } catch (e: IOException) {
            assertTrue(e is SocketException)
            assertTrue(e.message?.contains("Connection aborted") == true)
        }
    }

    @Test
    fun T2_B09_04_cidr_range_unban_handling() = runBlocking {
        val subnetCidr = "198.51.100.0/24"
        assertTrue(subnetCidr.matches(ipRegex))

        val response = apiService.unbanIp(UnbanRequest(ip = subnetCidr))
        assertNotNull(response)
        assertTrue(response.success)
        assertEquals(subnetCidr, response.ip)

        val lastReq = dispatcher.lastRecordedRequest
        assertNotNull(lastReq)
        val body = dispatcher.extractRequestBody(lastReq!!)
        assertTrue(body.contains("198.51.100.0/24"))
    }

    @Test
    fun T2_B09_05_bouncer_offline_status() = runBlocking {
        val bouncerOfflinePayload = """
        {
          "activeDecisions": [],
          "bouncers": [
            {
              "name": "FirewallBouncer",
              "type": "nftables",
              "ipAddress": "127.0.0.1",
              "valid": false,
              "lastPull": "1970-01-01T00:00:00Z"
            }
          ]
        }
        """.trimIndent()

        dispatcher.setResponse("/api/netsec/crowdsec/decisions", 200, bouncerOfflinePayload)

        val response = apiService.getCrowdSecDecisions()
        assertEquals(1, response.bouncers.size)

        val bouncer = response.bouncers[0]
        assertEquals("FirewallBouncer", bouncer.name)
        assertFalse(bouncer.valid)
    }
}
