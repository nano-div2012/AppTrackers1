package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryItem(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val isIncome: Boolean = false,
    val monthlyBudget: Double = 0.0,
    val orderIndex: Int = 0
)
