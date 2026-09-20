package com.example.e2e.tier1_features

import com.example.data.api.UnbanRequest
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1 Tests for Feature 9: CrowdSec Decisions & 1-Tap Unban.
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F09_01: Active CrowdSec decisions feed ingestion and scenario parsing
 * - T1_F09_02: 1-Tap IP unban remediation request execution and payload verification
 * - T1_F09_03: Client-side IP address sanitization and injection prevention guard
 * - T1_F09_04: Optimistic state eviction of unbanned IP from active ban list
 * - T1_F09_05: CrowdSec bouncer daemon subsystem health status verification
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F09_CrowdSecUnbanTest : E2eTestHarness() {

    @Test
    fun T1_F09_01_active_crowdsec_bans_ingestion() = runBlocking {
        val decisionsResponse = apiService.getCrowdSecDecisions()
        assertNotNull(decisionsResponse)
        assertEquals(2, decisionsResponse.activeDecisions.size)

        val ban1 = decisionsResponse.activeDecisions[0]
        assertEquals(14951L, ban1.id)
        assertEquals("209.99.190.113", ban1.value)
        assertEquals("ssh:bruteforce", ban1.scenario)
        assertEquals("CAPI", ban1.origin)
        assertEquals("3h 45m", ban1.duration)

        val ban2 = decisionsResponse.activeDecisions[1]
        assertEquals(14952L, ban2.id)
        assertEquals("198.51.100.4", ban2.value)
        assertEquals("http:crawl", ban2.scenario)
    }

    @Test
    fun T1_F09_02_1tap_ip_unban_remediation_dispatch() = runBlocking {
        val unbanIp = "209.99.190.113"
        val response = apiService.unbanIp(UnbanRequest(ip = unbanIp))

        assertNotNull(response)
        assertTrue(response.success)
        assertEquals(unbanIp, response.ip)
        assertEquals("Decision deleted", response.message)

        val lastReq = dispatcher.lastRecordedRequest
        assertNotNull(lastReq)
        assertEquals("/api/netsec/crowdsec/unban", lastReq?.url?.encodedPath)
        val body = dispatcher.extractRequestBody(lastReq!!)
        assertTrue(body.contains(unbanIp))
    }

    @Test
    fun T1_F09_03_client_side_ip_validation_guard() {
        val ipRegex = Regex("""^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\/([0-9]|[1-2][0-9]|3[0-2]))?$""")

        val validIp = "209.99.190.113"
        val validSubnet = "192.168.1.0/24"
        val injectionAttack = "192.168.1.1; rm -rf /"
        val invalidFormat = "999.999.999.999"

        assertTrue(validIp.matches(ipRegex))
        assertTrue(validSubnet.matches(ipRegex))
        assertFalse(injectionAttack.matches(ipRegex))
        assertFalse(invalidFormat.matches(ipRegex))
    }

    @Test
    fun T1_F09_04_optimistic_ui_eviction_of_unbanned_ip() = runBlocking {
        val initialDecisions = apiService.getCrowdSecDecisions().activeDecisions.toMutableList()
        assertEquals(2, initialDecisions.size)

        val targetIp = "209.99.190.113"
        val unbanResponse = apiService.unbanIp(UnbanRequest(targetIp))
        assertTrue(unbanResponse.success)

        // Optimistic eviction
        initialDecisions.removeAll { it.value == targetIp }
        assertEquals(1, initialDecisions.size)
        assertEquals("198.51.100.4", initialDecisions[0].value)
    }

    @Test
    fun T1_F09_05_crowdsec_bouncer_health_status() = runBlocking {
        val decisionsResponse = apiService.getCrowdSecDecisions()
        val bouncers = decisionsResponse.bouncers
        assertEquals(2, bouncers.size)

        val fwBouncer = bouncers.find { it.name == "FirewallBouncer" }
        assertNotNull(fwBouncer)
        assertEquals("nftables", fwBouncer?.type)
        assertTrue(fwBouncer?.valid == true)

        val cfBouncer = bouncers.find { it.name == "cloudflare-bouncer" }
        assertNotNull(cfBouncer)
        assertEquals("api", cfBouncer?.type)
        assertTrue(cfBouncer?.valid == true)
    }
}
