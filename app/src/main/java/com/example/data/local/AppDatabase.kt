package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.CategorizationRule
import com.example.data.model.CategoryItem
import com.example.data.model.SyncLog
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

@Database(
    entities = [
        BankAccount::class,
        TransactionItem::class,
        CategoryItem::class,
        CategorizationRule::class,
        SyncLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bankAccountDao(): BankAccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): CategorizationRuleDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledgersync_finance.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val DEFAULT_CATEGORIES = listOf(
            CategoryItem("groceries", "Groceries", "shopping_cart", "#10B981", false, 650.0, 1),
            CategoryItem("dining", "Dining & Drinks", "restaurant", "#F59E0B", false, 450.0, 2),
            CategoryItem("shopping", "Shopping", "shopping_bag", "#EC4899", false, 350.0, 3),
            CategoryItem("transportation", "Transportation", "directions_car", "#3B82F6", false, 250.0, 4),
            CategoryItem("housing", "Housing & Rent", "home", "#8B5CF6", false, 1850.0, 5),
            CategoryItem("utilities", "Utilities & Bills", "bolt", "#6366F1", false, 280.0, 6),
            CategoryItem("entertainment", "Entertainment", "movie", "#14B8A6", false, 150.0, 7),
            CategoryItem("healthcare", "Health & Medical", "local_hospital", "#EF4444", false, 180.0, 8),
            CategoryItem("travel", "Travel & Lodging", "flight", "#F97316", false, 250.0, 9),
            CategoryItem("subscriptions", "Subscriptions", "autorenew", "#A855F7", false, 85.0, 10),
            CategoryItem("investments", "Investments", "trending_up", "#0284C7", false, 500.0, 11),
            CategoryItem("personal_care", "Personal Care", "spa", "#D946EF", false, 120.0, 12),
            CategoryItem("income", "Income & Salary", "attach_money", "#059669", true, 0.0, 0),
            CategoryItem("other", "Other Expenses", "category", "#64748B", false, 100.0, 13)
        )

        val DEFAULT_RULES = listOf(
            CategorizationRule(keyword = "whole foods", targetCategoryId = "groceries", targetCategoryName = "Groceries", priority = 10),
            CategorizationRule(keyword = "trader joe", targetCategoryId = "groceries", targetCategoryName = "Groceries", priority = 10),
            CategorizationRule(keyword = "kroger", targetCategoryId = "groceries", targetCategoryName = "Groceries", priority = 10),
            CategorizationRule(keyword = "safeway", targetCategoryId = "groceries", targetCategoryName = "Groceries", priority = 10),
            CategorizationRule(keyword = "costco", targetCategoryId = "groceries", targetCategoryName = "Groceries", priority = 10),
            CategorizationRule(keyword = "starbucks", targetCategoryId = "dining", targetCategoryName = "Dining & Drinks", priority = 10),
            CategorizationRule(keyword = "chipotle", targetCategoryId = "dining", targetCategoryName = "Dining & Drinks", priority = 10),
            CategorizationRule(keyword = "doordash", targetCategoryId = "dining", targetCategoryName = "Dining & Drinks", priority = 10),
            CategorizationRule(keyword = "uber eats", targetCategoryId = "dining", targetCategoryName = "Dining & Drinks", priority = 10),
            CategorizationRule(keyword = "mcdonald", targetCategoryId = "dining", targetCategoryName = "Dining & Drinks", priority = 10),
            CategorizationRule(keyword = "amazon", targetCategoryId = "shopping", targetCategoryName = "Shopping", priority = 10),
            CategorizationRule(keyword = "target", targetCategoryId = "shopping", targetCategoryName = "Shopping", priority = 10),
            CategorizationRule(keyword = "apple store", targetCategoryId = "shopping", targetCategoryName = "Shopping", priority = 10),
            CategorizationRule(keyword = "uber", targetCategoryId = "transportation", targetCategoryName = "Transportation", priority = 9),
            CategorizationRule(keyword = "lyft", targetCategoryId = "transportation", targetCategoryName = "Transportation", priority = 10),
            CategorizationRule(keyword = "shell oil", targetCategoryId = "transportation", targetCategoryName = "Transportation", priority = 10),
            CategorizationRule(keyword = "chevron", targetCategoryId = "transportation", targetCategoryName = "Transportation", priority = 10),
            CategorizationRule(keyword = "netflix", targetCategoryId = "subscriptions", targetCategoryName = "Subscriptions", priority = 10),
            CategorizationRule(keyword = "spotify", targetCategoryId = "subscriptions", targetCategoryName = "Subscriptions", priority = 10),
            CategorizationRule(keyword = "disney+", targetCategoryId = "subscriptions", targetCategoryName = "Subscriptions", priority = 10),
            CategorizationRule(keyword = "pge electric", targetCategoryId = "utilities", targetCategoryName = "Utilities & Bills", priority = 10),
            CategorizationRule(keyword = "coned", targetCategoryId = "utilities", targetCategoryName = "Utilities & Bills", priority = 10),
            CategorizationRule(keyword = "payroll", targetCategoryId = "income", targetCategoryName = "Income & Salary", priority = 10),
            CategorizationRule(keyword = "direct dep", targetCategoryId = "income", targetCategoryName = "Income & Salary", priority = 10)
        )
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database)
                }
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            val categoryDao = db.categoryDao()
            val ruleDao = db.ruleDao()
            val accountDao = db.bankAccountDao()
            val txDao = db.transactionDao()
            val syncLogDao = db.syncLogDao()

            categoryDao.insertCategories(DEFAULT_CATEGORIES)
            ruleDao.insertRules(DEFAULT_RULES)

            // Seed initial connected bank accounts
            val chaseChecking = BankAccount(
                id = "acc_chase_chk",
                institutionName = "Chase",
                accountName = "Total Checking",
                accountType = AccountType.CHECKING,
                mask = "...4892",
                balance = 4850.75,
                availableBalance = 4850.75,
                colorHex = "#117ACA",
                lastSyncTime = System.currentTimeMillis() - 15 * 60 * 1000,
                syncStatus = SyncStatus.SYNCED,
                isPrimary = true
            )
            val sapphireCard = BankAccount(
                id = "acc_chase_cc",
                institutionName = "Chase",
                accountName = "Sapphire Preferred",
                accountType = AccountType.CREDIT_CARD,
                mask = "...8821",
                balance = 1245.30,
                availableBalance = 13754.70,
                colorHex = "#0A2F64",
                lastSyncTime = System.currentTimeMillis() - 15 * 60 * 1000,
                syncStatus = SyncStatus.SYNCED
            )
            val allySavings = BankAccount(
                id = "acc_ally_sav",
                institutionName = "Ally Bank",
                accountName = "High Yield Savings",
                accountType = AccountType.SAVINGS,
                mask = "...3104",
                balance = 24650.00,
                availableBalance = 24650.00,
                colorHex = "#663399",
                lastSyncTime = System.currentTimeMillis() - 45 * 60 * 1000,
                syncStatus = SyncStatus.SYNCED
            )

            accountDao.insertAccounts(listOf(chaseChecking, sapphireCard, allySavings))

            // Seed initial realistic transactions (past 14 days)
            val now = System.currentTimeMillis()
            val dayMs = 24 * 60 * 60 * 1000L

            val initialTxs = listOf(
                TransactionItem(
                    id = "tx_seed_1",
                    accountId = "acc_chase_chk",
                    amount = 3450.00,
                    type = TransactionType.INCOME,
                    timestamp = now - (1 * dayMs + 3600000 * 4),
                    rawDescription = "DIRECT DEP PAYROLL TECH CORP ACCT *4892",
                    cleanMerchant = "Payroll - Tech Corp",
                    categoryId = "income",
                    categoryName = "Income & Salary",
                    categoryIcon = "attach_money",
                    categoryColorHex = "#059669",
                    autoCategorized = true,
                    confidenceScore = 0.99f,
                    ruleMatched = "Matched pattern 'payroll'",
                    notes = "Bi-weekly paycheck"
                ),
                TransactionItem(
                    id = "tx_seed_2",
                    accountId = "acc_chase_cc",
                    amount = 86.42,
                    type = TransactionType.EXPENSE,
                    timestamp = now - (1 * dayMs + 3600000 * 2),
                    rawDescription = "WHOLEFDS SFO 10243 SAN FRANCISCO CA",
                    cleanMerchant = "Whole Foods Market",
                    categoryId = "groceries",
                    categoryName = "Groceries",
                    categoryIcon = "shopping_cart",
                    categoryColorHex = "#10B981",
                    autoCategorized = true,
                    confidenceScore = 0.97f,
                    ruleMatched = "Matched rule 'whole foods'",
                    notes = "Weekly fresh produce"
                ),
                TransactionItem(
                    id = "tx_seed_3",
                    accountId = "acc_chase_cc",
                    amount = 14.85,
                    type = TransactionType.EXPENSE,
                    timestamp = now - (2 * dayMs + 3600000),
                    rawDescription = "SQ *BLUE BOTTLE COFFEE FERRY BUILDING",
                    cleanMerchant = "Blue Bottle Coffee",
                    categoryId = "dining",
                    categoryName = "Dining & Drinks",
                    categoryIcon = "restaurant",
                    categoryColorHex = "#F59E0B",
                    autoCategorized = true,
                    confidenceScore = 0.95f,
                    ruleMatched = "Matched keyword 'coffee'",
                    notes = "Iced oat latte & pastry"
                ),
                TransactionItem(
                    id = "tx_seed_4",
                    accountId = "acc_chase_chk",
                    amount = 1750.00,
                    type = TransactionType.EXPENSE,
                    timestamp = now - 3 * dayMs,
                    rawDescription = "ACH WITHDRAWAL RESIDENTIAL LEASING RENT",
                    cleanMerchant = "Residential Leasing Rent",
                    categoryId = "housing",
                    categoryName = "Housing & Rent",
                    categoryIcon = "home",
                    categoryColorHex = "#8B5CF6",
                    autoCategorized = true,
                    confidenceScore = 0.98f,
                    ruleMatched = "Matched keyword 'rent'",
                    notes = "Monthly apartment rent"
                ),
                TransactionItem(
                    id = "tx_seed_5",
                    accountId = "acc_chase_cc",
                    amount = 68.30,
                    type = TransactionType.EXPENSE,
                    timestamp = now - 4 * dayMs,
                    rawDescription = "AMZN Mktp US*3M2K84 WA 98109",
                    cleanMerchant = "Amazon Marketplace",
                    categoryId = "shopping",
                    categoryName = "Shopping",
                    categoryIcon = "shopping_bag",
                    categoryColorHex = "#EC4899",
                    autoCategorized = true,
                    confidenceScore = 0.99f,
                    ruleMatched = "Matched rule 'amazon'",
                    notes = "Home supplies"
                ),
                TransactionItem(
                    id = "tx_seed_6",
                    accountId = "acc_chase_cc",
                    amount = 26.50,
                    type = TransactionType.EXPENSE,
                    timestamp = now - 5 * dayMs,
                    rawDescription = "UBER *TRIP RIDE SAN FRANCISCO",
                    cleanMerchant = "Uber Trip",
                    categoryId = "transportation",
                    categoryName = "Transportation",
                    categoryIcon = "directions_car",
                    categoryColorHex = "#3B82F6",
                    autoCategorized = true,
                    confidenceScore = 0.98f,
                    ruleMatched = "Matched rule 'uber'",
                    notes = "Ride home from downtown"
                ),
                TransactionItem(
                    id = "tx_seed_7",
                    accountId = "acc_chase_cc",
                    amount = 19.99,
                    type = TransactionType.EXPENSE,
                    timestamp = now - 6 * dayMs,
                    rawDescription = "NETFLIX.COM DIGITAL SUBSCRIPTION LOS GATOS",
                    cleanMerchant = "Netflix",
                    categoryId = "subscriptions",
                    categoryName = "Subscriptions",
                    categoryIcon = "autorenew",
                    categoryColorHex = "#A855F7",
                    autoCategorized = true,
                    confidenceScore = 0.99f,
                    ruleMatched = "Matched rule 'netflix'",
                    notes = "Monthly standard 4k plan"
                ),
                TransactionItem(
                    id = "tx_seed_8",
                    accountId = "acc_chase_chk",
                    amount = 89.20,
                    type = TransactionType.EXPENSE,
                    timestamp = now - 7 * dayMs,
                    rawDescription = "PG&E WEB UTILITY PAYMENT ELECTR",
                    cleanMerchant = "PG&E Electric & Gas",
                    categoryId = "utilities",
                    categoryName = "Utilities & Bills",
                    categoryIcon = "bolt",
                    categoryColorHex = "#6366F1",
                    autoCategorized = true,
                    confidenceScore = 0.96f,
                    ruleMatched = "Matched keyword 'pge'",
                    notes = "Utility bill"
                ),
                TransactionItem(
                    id = "tx_seed_9",
                    accountId = "acc_chase_cc",
                    amount = 54.12,
                    type = TransactionType.EXPENSE,
                    timestamp = now - 8 * dayMs,
                    rawDescription = "CHIPOTLE 1284 MARKET ST SAN FRANCISCO",
                    cleanMerchant = "Chipotle Mexican Grill",
                    categoryId = "dining",
                    categoryName = "Dining & Drinks",
                    categoryIcon = "restaurant",
                    categoryColorHex = "#F59E0B",
                    autoCategorized = true,
                    confidenceScore = 0.98f,
                    ruleMatched = "Matched rule 'chipotle'",
                    notes = "Team lunch burritos"
                ),
                TransactionItem(
                    id = "tx_seed_10",
                    accountId = "acc_chase_cc",
                    amount = 112.45,
                    type = TransactionType.EXPENSE,
                    timestamp = now - 9 * dayMs,
                    rawDescription = "TRADER JOE'S #544 STEVENS CREEK",
                    cleanMerchant = "Trader Joe's",
                    categoryId = "groceries",
                    categoryName = "Groceries",
                    categoryIcon = "shopping_cart",
                    categoryColorHex = "#10B981",
                    autoCategorized = true,
                    confidenceScore = 0.99f,
                    ruleMatched = "Matched rule 'trader joe'",
                    notes = "Snacks & pantry staples"
                ),
                TransactionItem(
                    id = "tx_seed_11",
                    accountId = "acc_ally_sav",
                    amount = 45.80,
                    type = TransactionType.INCOME,
                    timestamp = now - 10 * dayMs,
                    rawDescription = "ALLY BANK MONTHLY INTEREST PAYMENT CR",
                    cleanMerchant = "Ally Interest Credit",
                    categoryId = "income",
                    categoryName = "Income & Salary",
                    categoryIcon = "attach_money",
                    categoryColorHex = "#059669",
                    autoCategorized = true,
                    confidenceScore = 0.96f,
                    ruleMatched = "Matched keyword 'interest'",
                    notes = "4.25% APY High-Yield Savings interest"
                )
            )

            txDao.insertTransactions(initialTxs)

            syncLogDao.insertLog(
                SyncLog(
                    timestamp = now - 15 * 60 * 1000,
                    institutionName = "Chase",
                    accountName = "Total Checking & Sapphire Card",
                    transactionsAdded = 9,
                    status = "SUCCESS",
                    message = "Auto-synced via Open Banking. 9 transactions matched & categorized."
                )
            )
        }
    }
}
