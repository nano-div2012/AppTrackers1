package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.CategorizationRule
import com.example.data.model.CategoryItem
import com.example.data.model.SyncLog
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY isPrimary DESC, balance DESC")
    fun getAllAccounts(): Flow<List<BankAccount>>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    fun getAccountById(id: String): Flow<BankAccount?>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    suspend fun getAccountByIdSync(id: String): BankAccount?

    @Query("SELECT COUNT(*) FROM bank_accounts")
    suspend fun getAccountCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: BankAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<BankAccount>)

    @Update
    suspend fun updateAccount(account: BankAccount)

    @Query("UPDATE bank_accounts SET balance = :balance, lastSyncTime = :syncTime, syncStatus = :status WHERE id = :id")
    suspend fun updateAccountBalanceAndStatus(id: String, balance: Double, syncTime: Long, status: SyncStatus)

    @Delete
    suspend fun deleteAccount(account: BankAccount)

    @Query("DELETE FROM bank_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsByAccount(accountId: String): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getTransactionById(id: String): Flow<TransactionItem?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionByIdSync(id: String): TransactionItem?

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsSync(): List<TransactionItem>

    @Query("SELECT COUNT(*) FROM transactions")
    fun getTransactionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE autoCategorized = 1")
    fun getAutoCategorizedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionItem)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionItem>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionItem)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionItem)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isIncome ASC, orderIndex ASC")
    fun getAllCategories(): Flow<List<CategoryItem>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryItem?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryItem>)

    @Update
    suspend fun updateCategory(category: CategoryItem)

    @Query("UPDATE categories SET monthlyBudget = :budget WHERE id = :id")
    suspend fun updateCategoryBudget(id: String, budget: Double)
}

@Dao
interface CategorizationRuleDao {
    @Query("SELECT * FROM categorization_rules ORDER BY priority DESC, createdAt DESC")
    fun getAllRules(): Flow<List<CategorizationRule>>

    @Query("SELECT * FROM categorization_rules ORDER BY priority DESC")
    suspend fun getAllRulesSync(): List<CategorizationRule>

    @Query("SELECT COUNT(*) FROM categorization_rules")
    suspend fun getRuleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CategorizationRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<CategorizationRule>)

    @Delete
    suspend fun deleteRule(rule: CategorizationRule)

    @Query("DELETE FROM categorization_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 20): Flow<List<SyncLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLog)
}
