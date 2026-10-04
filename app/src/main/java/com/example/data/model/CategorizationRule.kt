package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categorization_rules")
data class CategorizationRule(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val targetCategoryId: String,
    val targetCategoryName: String,
    val isRegex: Boolean = false,
    val priority: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
