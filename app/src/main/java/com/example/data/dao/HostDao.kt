package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.HostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY id ASC")
    fun getAllHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id LIMIT 1")
    fun getHostById(id: Int): Flow<HostEntity?>

    @Query("SELECT * FROM hosts WHERE id = :id LIMIT 1")
    suspend fun getHostByIdDirect(id: Int): HostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHost(host: HostEntity): Long

    @Update
    suspend fun updateHost(host: HostEntity)

    @Delete
    suspend fun deleteHost(host: HostEntity)

    @Query("UPDATE hosts SET isProvisioned = :provisioned WHERE id = :hostId")
    suspend fun updateProvisionedStatus(hostId: Int, provisioned: Boolean)

    @Query("UPDATE hosts SET cpuUsage = :cpu, memoryUsage = :ram, diskUsage = :disk, temperatureC = :temp WHERE id = :hostId")
    suspend fun updateHostTelemetry(hostId: Int, cpu: Int, ram: Int, disk: Int, temp: Int)

    @Query("""
        UPDATE hosts 
        SET connectionState = :state, 
            activeEndpoint = :activeEndpoint, 
            lastLatencyMs = :latencyMs, 
            isOnline = :isOnline, 
            lastConnected = :timestamp 
        WHERE id = :hostId
    """)
    suspend fun updateConnectionStatus(
        hostId: Int,
        state: String,
        activeEndpoint: String,
        latencyMs: Long,
        isOnline: Boolean,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE hosts 
        SET tailscaleAddress = :tailscale, 
            lanAddress = :lan, 
            arcadePort = :arcadePort, 
            eveboxPort = :eveboxPort 
        WHERE id = :hostId
    """)
    suspend fun updateEndpoints(
        hostId: Int,
        tailscale: String,
        lan: String,
        arcadePort: Int = 8899,
        eveboxPort: Int = 5636
    )

    @Query("UPDATE hosts SET vaultAlias = :alias, apiKey = :apiKey WHERE id = :hostId")
    suspend fun updateVaultCredentials(hostId: Int, alias: String, apiKey: String)

    @Query("UPDATE hosts SET connectionMode = :mode WHERE id = :hostId")
    suspend fun updateConnectionMode(hostId: Int, mode: String)
}
