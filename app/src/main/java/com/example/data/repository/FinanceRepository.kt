package com.example.data.repository

import com.example.data.engine.BankInstitution
import com.example.data.engine.BankSyncEngine
import com.example.data.engine.CategorizationEngine
import com.example.data.engine.SyncResult
import com.example.data.local.AppDatabase
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.CategorizationRule
import com.example.data.model.CategoryItem
import com.example.data.model.SyncLog
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

class FinanceRepository(private val database: AppDatabase) {

    private val accountDao = database.bankAccountDao()
    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()
    private val ruleDao = database.ruleDao()
    private val syncLogDao = database.syncLogDao()

    val allAccounts: Flow<List<BankAccount>> = accountDao.getAllAccounts()
    val allTransactions: Flow<List<TransactionItem>> = transactionDao.getAllTransactions()
    val allCategories: Flow<List<CategoryItem>> = categoryDao.getAllCategories()
    val allRules: Flow<List<CategorizationRule>> = ruleDao.getAllRules()
    val recentSyncLogs: Flow<List<SyncLog>> = syncLogDao.getRecentLogs()
    val totalTransactionCount: Flow<Int> = transactionDao.getTransactionCount()
    val autoCategorizedCount: Flow<Int> = transactionDao.getAutoCategorizedCount()

    suspend fun syncAccount(accountId: String): SyncResult = withContext(Dispatchers.IO) {
        val account = accountDao.getAccountByIdSync(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        accountDao.updateAccountBalanceAndStatus(
            id = account.id,
            balance = account.balance,
            syncTime = System.currentTimeMillis(),
            status = SyncStatus.SYNCING
        )

        val existingTxs = transactionDao.getAllTransactionsSync().filter { it.accountId == accountId }
        val rules = ruleDao.getAllRulesSync()
        val categories = categoryDao.getAllCategories().first()

        val syncResult = BankSyncEngine.simulateLiveBankSync(account, existingTxs, rules, categories)

        if (syncResult.newTransactions.isNotEmpty()) {
            transactionDao.insertTransactions(syncResult.newTransactions)
        }

        accountDao.updateAccountBalanceAndStatus(
            id = account.id,
            balance = syncResult.updatedBalance,
            syncTime = System.currentTimeMillis(),
            status = SyncStatus.SYNCED
        )

        syncLogDao.insertLog(
            SyncLog(
                timestamp = System.currentTimeMillis(),
                institutionName = account.institutionName,
                accountName = account.accountName,
                transactionsAdded = syncResult.newTransactions.size,
                status = "SUCCESS",
                message = syncResult.message
            )
        )

        syncResult
    }

    suspend fun syncAllAccounts(): List<SyncResult> = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAllAccounts().first()
        val results = mutableListOf<SyncResult>()
        for (acc in accounts) {
            val res = syncAccount(acc.id)
            results.add(res)
        }
        results
    }

    suspend fun importCsvStatement(csvText: String, accountId: String): Int = withContext(Dispatchers.IO) {
        val rules = ruleDao.getAllRulesSync()
        val categories = categoryDao.getAllCategories().first()
        val account = accountDao.getAccountByIdSync(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        val parsed = BankSyncEngine.parseBankCsv(csvText, accountId, rules, categories)
        if (parsed.isNotEmpty()) {
            val insertedIds = transactionDao.insertTransactions(parsed)
            val addedCount = insertedIds.filter { it != -1L }.size

            // Update account balance
            var balanceDelta = 0.0
            parsed.forEach {
                if (it.type == TransactionType.INCOME) balanceDelta += it.amount else balanceDelta -= it.amount
            }
            val newBalance = if (account.accountType == AccountType.CREDIT_CARD) {
                account.balance - balanceDelta
            } else {
                account.balance + balanceDelta
            }

            accountDao.updateAccountBalanceAndStatus(
                id = account.id,
                balance = kotlin.math.max(0.0, newBalance),
                syncTime = System.currentTimeMillis(),
                status = SyncStatus.SYNCED
            )

            syncLogDao.insertLog(
                SyncLog(
                    timestamp = System.currentTimeMillis(),
                    institutionName = account.institutionName,
                    accountName = account.accountName,
                    transactionsAdded = addedCount,
                    status = "SUCCESS",
                    message = "Imported $addedCount transactions via CSV bank statement"
                )
            )
            addedCount
        } else {
            0
        }
    }

    suspend fun addAccount(
        institutionName: String,
        accountName: String,
        accountType: AccountType,
        initialBalance: Double,
        mask: String,
        colorHex: String
    ): BankAccount = withContext(Dispatchers.IO) {
        val newAccount = BankAccount(
            id = "acc_${UUID.randomUUID().toString().take(8)}",
            institutionName = institutionName,
            accountName = accountName,
            accountType = accountType,
            mask = mask,
            balance = initialBalance,
            availableBalance = initialBalance,
            colorHex = colorHex,
            lastSyncTime = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED
        )
        accountDao.insertAccount(newAccount)

        // Seed 2-3 sample transactions for the new account so it is immediately active
        val rules = ruleDao.getAllRulesSync()
        val categories = categoryDao.getAllCategories().first()
        val syncRes = BankSyncEngine.simulateLiveBankSync(newAccount, emptyList(), rules, categories)
        if (syncRes.newTransactions.isNotEmpty()) {
            transactionDao.insertTransactions(syncRes.newTransactions)
        }

        newAccount
    }

    suspend fun deleteAccount(accountId: String) = withContext(Dispatchers.IO) {
        accountDao.deleteAccountById(accountId)
    }

    suspend fun addTransaction(
        accountId: String,
        amount: Double,
        type: TransactionType,
        rawDescription: String,
        categoryId: String,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val categories = categoryDao.getAllCategories().first()
        val cat = categories.firstOrNull { it.id == categoryId } ?: categories.first()
        val cleanMerchant = CategorizationEngine.cleanMerchantName(rawDescription)

        val tx = TransactionItem(
            accountId = accountId,
            amount = amount,
            type = type,
            timestamp = System.currentTimeMillis(),
            rawDescription = rawDescription,
            cleanMerchant = cleanMerchant,
            categoryId = cat.id,
            categoryName = cat.name,
            categoryIcon = cat.icon,
            categoryColorHex = cat.colorHex,
            autoCategorized = false,
            confidenceScore = 1.0f,
            ruleMatched = "Manually entered",
            notes = notes
        )
        transactionDao.insertTransaction(tx)

        // Adjust account balance
        val account = accountDao.getAccountByIdSync(accountId)
        if (account != null) {
            val delta = if (type == TransactionType.INCOME) amount else -amount
            val newBalance = if (account.accountType == AccountType.CREDIT_CARD) {
                account.balance - delta
            } else {
                account.balance + delta
            }
            accountDao.updateAccountBalanceAndStatus(
                account.id,
                kotlin.math.max(0.0, newBalance),
                System.currentTimeMillis(),
                account.syncStatus
            )
        }
    }

    suspend fun updateTransactionCategory(
        txId: String,
        newCategoryId: String,
        createRuleForMerchant: Boolean
    ) = withContext(Dispatchers.IO) {
        val tx = transactionDao.getTransactionByIdSync(txId) ?: return@withContext
        val categories = categoryDao.getAllCategories().first()
        val newCat = categories.firstOrNull { it.id == newCategoryId } ?: return@withContext

        val updated = tx.copy(
            categoryId = newCat.id,
            categoryName = newCat.name,
            categoryIcon = newCat.icon,
            categoryColorHex = newCat.colorHex,
            autoCategorized = false,
            confidenceScore = 1.0f,
            ruleMatched = "User manual override"
        )
        transactionDao.updateTransaction(updated)

        if (createRuleForMerchant && tx.cleanMerchant.isNotBlank()) {
            val newRule = CategorizationRule(
                keyword = tx.cleanMerchant.lowercase(),
                targetCategoryId = newCat.id,
                targetCategoryName = newCat.name,
                priority = 10
            )
            ruleDao.insertRule(newRule)

            // Re-evaluate other transactions from this same merchant
            recategorizeAllTransactions()
        }
    }

    suspend fun recategorizeAllTransactions(): Int = withContext(Dispatchers.IO) {
        val transactions = transactionDao.getAllTransactionsSync()
        val rules = ruleDao.getAllRulesSync()
        val categories = categoryDao.getAllCategories().first()
        var updatedCount = 0

        for (tx in transactions) {
            val result = CategorizationEngine.categorize(
                tx.rawDescription,
                tx.cleanMerchant,
                tx.amount,
                tx.type,
                rules,
                categories
            )

            if (result.categoryId != tx.categoryId) {
                val updated = tx.copy(
                    categoryId = result.categoryId,
                    categoryName = result.categoryName,
                    categoryIcon = result.categoryIcon,
                    categoryColorHex = result.categoryColorHex,
                    confidenceScore = result.confidenceScore,
                    ruleMatched = result.reason,
                    autoCategorized = true
                )
                transactionDao.updateTransaction(updated)
                updatedCount++
            }
        }
        updatedCount
    }

    suspend fun addRule(keyword: String, categoryId: String) = withContext(Dispatchers.IO) {
        val categories = categoryDao.getAllCategories().first()
        val cat = categories.firstOrNull { it.id == categoryId } ?: return@withContext
        val rule = CategorizationRule(
            keyword = keyword.lowercase().trim(),
            targetCategoryId = cat.id,
            targetCategoryName = cat.name,
            priority = 10
        )
        ruleDao.insertRule(rule)
        recategorizeAllTransactions()
    }

    suspend fun deleteRule(ruleId: String) = withContext(Dispatchers.IO) {
        ruleDao.deleteRuleById(ruleId)
    }

    suspend fun updateBudget(categoryId: String, budget: Double) = withContext(Dispatchers.IO) {
        categoryDao.updateCategoryBudget(categoryId, budget)
    }

    suspend fun deleteTransaction(tx: TransactionItem) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(tx)
    }
}
