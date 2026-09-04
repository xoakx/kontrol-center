package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rfc_items")
data class RfcItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hostId: Int,
    val rfcNumber: String,
    val title: String,
    val description: String,
    val proposedCommands: String,
    val rollbackScript: String,
    val impact: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val status: String, // "PENDING_APPROVAL", "APPROVED", "REJECTED", "EXECUTED", "FAILED"
    val executionLog: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val executedAt: Long? = null
)
