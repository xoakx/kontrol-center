package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_snippets")
data class CommandSnippetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val command: String,
    val category: String, // "System", "Media", "Containers", "Network", "Safety"
    val isFavorite: Boolean = false
)
