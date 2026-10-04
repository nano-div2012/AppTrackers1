package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AccountType(val displayName: String) {
    CHECKING("Checking"),
    SAVINGS("Savings"),
    CREDIT_CARD("Credit Card"),
    INVESTMENT("Investment"),
    CASH("Cash Wallet")
}

enum class SyncStatus(val displayName: String) {
    SYNCED("Up to date"),
    SYNCING("Syncing..."),
    ATTENTION("Re-auth required"),
    DISCONNECTED("Disconnected")
}

@Entity(tableName = "bank_accounts")
data class BankAccount(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val institutionName: String,
    val accountName: String,
    val accountType: AccountType,
    val mask: String,
    val balance: Double,
    val availableBalance: Double = balance,
    val currency: String = "USD",
    val colorHex: String,
    val lastSyncTime: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val connectionType: String = "OPEN_BANKING",
    val isPrimary: Boolean = false
)
