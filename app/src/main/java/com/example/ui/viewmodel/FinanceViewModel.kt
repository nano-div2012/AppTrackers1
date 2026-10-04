package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.BankInstitution
import com.example.data.engine.BankSyncEngine
import com.example.data.local.AppDatabase
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.CategorizationRule
import com.example.data.model.CategoryItem
import com.example.data.model.SyncLog
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class CategorySpendInfo(
    val category: CategoryItem,
    val spentAmount: Double,
    val percentageOfBudget: Float,
    val percentageOfTotalSpend: Float,
    val isOverBudget: Boolean
)

data class FinanceUiState(
    val accounts: List<BankAccount> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val filteredTransactions: List<TransactionItem> = emptyList(),
    val categories: List<CategoryItem> = emptyList(),
    val rules: List<CategorizationRule> = emptyList(),
    val syncLogs: List<SyncLog> = emptyList(),
    val netWorth: Double = 0.0,
    val totalCash: Double = 0.0,
    val totalDebt: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val savingsRate: Double = 0.0,
    val categorySpendList: List<CategorySpendInfo> = emptyList(),
    val totalTransactionCount: Int = 0,
    val autoCategorizedCount: Int = 0,
    val autoCategorizedPercentage: Int = 100,
    val isSyncing: Boolean = false,
    val searchQuery: String = "",
    val filterAccountId: String? = null,
    val filterCategoryId: String? = null,
    val filterType: TransactionType? = null,
    val userNotification: String? = null
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = FinanceRepository(database)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterAccountId = MutableStateFlow<String?>(null)
    val filterAccountId: StateFlow<String?> = _filterAccountId.asStateFlow()

    private val _filterCategoryId = MutableStateFlow<String?>(null)
    val filterCategoryId: StateFlow<String?> = _filterCategoryId.asStateFlow()

    private val _filterType = MutableStateFlow<TransactionType?>(null)
    val filterType: StateFlow<TransactionType?> = _filterType.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _userNotification = MutableStateFlow<String?>(null)
    val userNotification: StateFlow<String?> = _userNotification.asStateFlow()

    val uiState: StateFlow<FinanceUiState> = combine(
        repository.allAccounts,
        repository.allTransactions,
        repository.allCategories,
        repository.allRules,
        repository.recentSyncLogs,
        _searchQuery,
        _filterAccountId,
        _filterCategoryId,
        _filterType,
        _isSyncing,
        _userNotification
    ) { rawArgs: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val accounts = rawArgs[0] as List<BankAccount>
        @Suppress("UNCHECKED_CAST")
        val transactions = rawArgs[1] as List<TransactionItem>
        @Suppress("UNCHECKED_CAST")
        val categories = rawArgs[2] as List<CategoryItem>
        @Suppress("UNCHECKED_CAST")
        val rules = rawArgs[3] as List<CategorizationRule>
        @Suppress("UNCHECKED_CAST")
        val syncLogs = rawArgs[4] as List<SyncLog>
        val query = rawArgs[5] as String
        val accFilter = rawArgs[6] as String?
        val catFilter = rawArgs[7] as String?
        val typeFilter = rawArgs[8] as TransactionType?
        val syncing = rawArgs[9] as Boolean
        val notification = rawArgs[10] as String?

        // Calculate Balances
        var cash = 0.0
        var debt = 0.0
        var investments = 0.0
        accounts.forEach { acc ->
            when (acc.accountType) {
                AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH -> cash += acc.balance
                AccountType.CREDIT_CARD -> debt += acc.balance
                AccountType.INVESTMENT -> investments += acc.balance
            }
        }
        val netWorth = (cash + investments) - debt

        // Calculate Monthly Totals (current month)
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startOfMonth = cal.timeInMillis

        var monthInc = 0.0
        var monthExp = 0.0
        val categorySpendMap = mutableMapOf<String, Double>()

        transactions.forEach { tx ->
            if (tx.timestamp >= startOfMonth) {
                if (tx.type == TransactionType.INCOME) {
                    monthInc += tx.amount
                } else if (tx.type == TransactionType.EXPENSE) {
                    monthExp += tx.amount
                    val curr = categorySpendMap[tx.categoryId] ?: 0.0
                    categorySpendMap[tx.categoryId] = curr + tx.amount
                }
            }
        }

        val savingsRate = if (monthInc > 0) {
            ((monthInc - monthExp) / monthInc) * 100.0
        } else 0.0

        // Build category spend infos
        val expenseCats = categories.filter { !it.isIncome }
        val categorySpendList = expenseCats.map { cat ->
            val spent = categorySpendMap[cat.id] ?: 0.0
            val pctBudget = if (cat.monthlyBudget > 0) (spent / cat.monthlyBudget).toFloat() else 0f
            val pctTotal = if (monthExp > 0) (spent / monthExp).toFloat() else 0f
            CategorySpendInfo(
                category = cat,
                spentAmount = spent,
                percentageOfBudget = pctBudget,
                percentageOfTotalSpend = pctTotal,
                isOverBudget = cat.monthlyBudget > 0 && spent > cat.monthlyBudget
            )
        }.sortedByDescending { it.spentAmount }

        // Filter transactions
        val filtered = transactions.filter { tx ->
            val matchesQuery = query.isBlank() ||
                    tx.cleanMerchant.contains(query, ignoreCase = true) ||
                    tx.rawDescription.contains(query, ignoreCase = true) ||
                    tx.categoryName.contains(query, ignoreCase = true) ||
                    tx.notes.contains(query, ignoreCase = true) ||
                    "%.2f".format(tx.amount).contains(query)

            val matchesAcc = accFilter == null || tx.accountId == accFilter
            val matchesCat = catFilter == null || tx.categoryId == catFilter
            val matchesType = typeFilter == null || tx.type == typeFilter

            matchesQuery && matchesAcc && matchesCat && matchesType
        }

        val totalTxCount = transactions.size
        val autoCatCount = transactions.count { it.autoCategorized }
        val autoPct = if (totalTxCount > 0) ((autoCatCount * 100) / totalTxCount) else 100

        FinanceUiState(
            accounts = accounts,
            transactions = transactions,
            filteredTransactions = filtered,
            categories = categories,
            rules = rules,
            syncLogs = syncLogs,
            netWorth = netWorth,
            totalCash = cash,
            totalDebt = debt,
            monthlyIncome = monthInc,
            monthlyExpenses = monthExp,
            savingsRate = savingsRate,
            categorySpendList = categorySpendList,
            totalTransactionCount = totalTxCount,
            autoCategorizedCount = autoCatCount,
            autoCategorizedPercentage = autoPct,
            isSyncing = syncing,
            searchQuery = query,
            filterAccountId = accFilter,
            filterCategoryId = catFilter,
            filterType = typeFilter,
            userNotification = notification
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterAccountId(accountId: String?) {
        _filterAccountId.value = accountId
    }

    fun setFilterCategoryId(categoryId: String?) {
        _filterCategoryId.value = categoryId
    }

    fun setFilterType(type: TransactionType?) {
        _filterType.value = type
    }

    fun clearNotification() {
        _userNotification.value = null
    }

    fun syncAllAccounts() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val results = repository.syncAllAccounts()
                val totalNew = results.sumOf { it.newTransactions.size }
                _userNotification.value = if (totalNew > 0) {
                    "Synced ${results.size} accounts: $totalNew new transactions found & categorized."
                } else {
                    "All ${results.size} accounts synced. Everything is up to date."
                }
            } catch (e: Exception) {
                _userNotification.value = "Sync error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncSingleAccount(accountId: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val res = repository.syncAccount(accountId)
                _userNotification.value = "${res.accountName}: ${res.message}"
            } catch (e: Exception) {
                _userNotification.value = "Sync failed: ${e.localizedMessage}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun connectNewBank(
        institution: BankInstitution,
        accountName: String,
        type: AccountType,
        initialBalance: Double,
        mask: String
    ) {
        viewModelScope.launch {
            try {
                val newAcc = repository.addAccount(
                    institutionName = institution.name,
                    accountName = accountName,
                    accountType = type,
                    initialBalance = initialBalance,
                    mask = mask,
                    colorHex = institution.colorHex
                )
                _userNotification.value = "Connected ${newAcc.institutionName} ${newAcc.accountName} successfully!"
            } catch (e: Exception) {
                _userNotification.value = "Failed to link account: ${e.localizedMessage}"
            }
        }
    }

    fun importCsvStatement(csvText: String, accountId: String) {
        viewModelScope.launch {
            try {
                val count = repository.importCsvStatement(csvText, accountId)
                _userNotification.value = if (count > 0) {
                    "Successfully imported $count transactions with automatic smart categorization."
                } else {
                    "No new transactions could be parsed from the provided statement."
                }
            } catch (e: Exception) {
                _userNotification.value = "Import error: ${e.localizedMessage}"
            }
        }
    }

    fun addManualTransaction(
        accountId: String,
        amount: Double,
        type: TransactionType,
        description: String,
        categoryId: String,
        notes: String
    ) {
        viewModelScope.launch {
            try {
                repository.addTransaction(accountId, amount, type, description, categoryId, notes)
                _userNotification.value = "Transaction saved."
            } catch (e: Exception) {
                _userNotification.value = "Error saving transaction: ${e.localizedMessage}"
            }
        }
    }

    fun updateTransactionCategory(
        txId: String,
        newCategoryId: String,
        createRuleForMerchant: Boolean
    ) {
        viewModelScope.launch {
            try {
                repository.updateTransactionCategory(txId, newCategoryId, createRuleForMerchant)
                _userNotification.value = if (createRuleForMerchant) {
                    "Category updated & auto-rule saved for this merchant!"
                } else {
                    "Transaction category updated."
                }
            } catch (e: Exception) {
                _userNotification.value = "Update failed: ${e.localizedMessage}"
            }
        }
    }

    fun addCategorizationRule(keyword: String, categoryId: String) {
        viewModelScope.launch {
            try {
                repository.addRule(keyword, categoryId)
                _userNotification.value = "Rule created! Matching transactions updated."
            } catch (e: Exception) {
                _userNotification.value = "Failed to add rule: ${e.localizedMessage}"
            }
        }
    }

    fun deleteCategorizationRule(ruleId: String) {
        viewModelScope.launch {
            try {
                repository.deleteRule(ruleId)
                _userNotification.value = "Rule removed."
            } catch (e: Exception) {
                _userNotification.value = "Error removing rule: ${e.localizedMessage}"
            }
        }
    }

    fun recategorizeAll() {
        viewModelScope.launch {
            try {
                val count = repository.recategorizeAllTransactions()
                _userNotification.value = "Re-evaluated all transactions: $count updated."
            } catch (e: Exception) {
                _userNotification.value = "Recategorization error: ${e.localizedMessage}"
            }
        }
    }

    fun updateBudget(categoryId: String, newBudget: Double) {
        viewModelScope.launch {
            try {
                repository.updateBudget(categoryId, newBudget)
                _userNotification.value = "Budget limit updated."
            } catch (e: Exception) {
                _userNotification.value = "Failed to update budget: ${e.localizedMessage}"
            }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                repository.deleteAccount(accountId)
                _userNotification.value = "Account unlinked."
            } catch (e: Exception) {
                _userNotification.value = "Error deleting account: ${e.localizedMessage}"
            }
        }
    }

    fun deleteTransaction(tx: TransactionItem) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(tx)
                _userNotification.value = "Transaction deleted."
            } catch (e: Exception) {
                _userNotification.value = "Error deleting transaction: ${e.localizedMessage}"
            }
        }
    }
}
