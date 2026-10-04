package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val institutionName: String,
    val accountName: String,
    val transactionsAdded: Int,
    val status: String = "SUCCESS",
    val message: String
)
