package com.example.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ArcadeApiService {

    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("/api/telemetry")
    suspend fun getTelemetry(): TelemetryResponse

    @GET("/api/fleet/status")
    suspend fun getFleetStatus(): FleetStatusResponse

    @POST("/api/agents/control")
    suspend fun controlAgent(@Body body: AgentControlRequest): AgentControlResponse

    @GET("/api/smarthome")
    suspend fun getSmartHome(): SmartHomeResponse

    @GET("/api/rfcs")
    suspend fun getRfcs(): RfcsResponse

    @POST("/api/rfcs/{rfcId}/vote")
    suspend fun voteRfc(
        @Path("rfcId") rfcId: String,
        @Body body: RfcVoteRequest
    ): RfcVoteResponse

    @GET("/api/netsec/overview")
    suspend fun getNetSecOverview(): NetSecOverviewResponse

    @GET("/api/netsec/suricata/alerts")
    suspend fun getSuricataAlerts(
        @Query("limit") limit: Int = 50
    ): SuricataAlertsResponse

    @GET("/api/netsec/crowdsec/decisions")
    suspend fun getCrowdSecDecisions(): CrowdSecDecisionsResponse

    @POST("/api/netsec/crowdsec/unban")
    suspend fun unbanIp(@Body body: UnbanRequest): UnbanResponse

    @GET("/api/netsec/tetragon/status")
    suspend fun getTetragonStatus(): TetragonStatusResponse

    @GET("/api/netsec/firewall/status")
    suspend fun getFirewallStatus(): FirewallStatusResponse

    @POST("/api/dispatch")
    suspend fun dispatchAction(@Body body: DispatchRequest): DispatchResponse

    @POST("/api/smarthome/control")
    suspend fun controlSmartHome(@Body body: SmartHomeControlRequest): SmartHomeControlResponse
}
