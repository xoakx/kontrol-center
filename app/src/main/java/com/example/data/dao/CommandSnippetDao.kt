package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.CommandSnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandSnippetDao {
    @Query("SELECT * FROM command_snippets ORDER BY isFavorite DESC, id ASC")
    fun getAllSnippets(): Flow<List<CommandSnippetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: CommandSnippetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippets(snippets: List<CommandSnippetEntity>)

    @Update
    suspend fun updateSnippet(snippet: CommandSnippetEntity)

    @Delete
    suspend fun deleteSnippet(snippet: CommandSnippetEntity)
}
