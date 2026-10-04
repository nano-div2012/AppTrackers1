package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = BankAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("accountId"),
        Index("timestamp"),
        Index("categoryId")
    ]
)
data class TransactionItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val amount: Double,
    val type: TransactionType = TransactionType.EXPENSE,
    val timestamp: Long,
    val rawDescription: String,
    val cleanMerchant: String,
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String = "category",
    val categoryColorHex: String = "#64748B",
    val autoCategorized: Boolean = true,
    val confidenceScore: Float = 0.95f,
    val ruleMatched: String? = null,
    val isPending: Boolean = false,
    val notes: String = ""
)
