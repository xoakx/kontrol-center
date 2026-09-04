package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.RfcItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RfcDao {
    @Query("SELECT * FROM rfc_items WHERE hostId = :hostId ORDER BY id DESC")
    fun getRfcsForHost(hostId: Int): Flow<List<RfcItemEntity>>

    @Query("SELECT * FROM rfc_items ORDER BY id DESC")
    fun getAllRfcs(): Flow<List<RfcItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRfc(rfc: RfcItemEntity): Long

    @Update
    suspend fun updateRfc(rfc: RfcItemEntity)

    @Delete
    suspend fun deleteRfc(rfc: RfcItemEntity)

    @Query("UPDATE rfc_items SET status = :status, executionLog = :log, executedAt = :executedAt WHERE id = :rfcId")
    suspend fun updateRfcStatus(rfcId: Int, status: String, log: String, executedAt: Long?)
}
