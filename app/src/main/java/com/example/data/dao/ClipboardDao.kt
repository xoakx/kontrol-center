package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ClipboardItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_items WHERE hostId = :hostId ORDER BY timestamp DESC LIMIT 30")
    fun getClipboardForHost(hostId: Int): Flow<List<ClipboardItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClipboard(item: ClipboardItemEntity): Long

    @Delete
    suspend fun deleteClipboard(item: ClipboardItemEntity)

    @Query("DELETE FROM clipboard_items WHERE hostId = :hostId")
    suspend fun clearClipboardForHost(hostId: Int)
}
