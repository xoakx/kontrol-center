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
}
