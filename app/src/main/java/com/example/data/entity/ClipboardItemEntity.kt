package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_items")
data class ClipboardItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hostId: Int,
    val content: String,
    val direction: String, // "HOST_TO_PHONE" or "PHONE_TO_HOST"
    val timestamp: Long = System.currentTimeMillis()
)
