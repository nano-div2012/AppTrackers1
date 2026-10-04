package com.example.data.engine

import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.CategorizationRule
import com.example.data.model.CategoryItem
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

data class BankInstitution(
    val id: String,
    val name: String,
    val colorHex: String,
    val initial: String,
    val defaultTypes: List<AccountType>
)

data class SyncResult(
    val accountId: String,
    val accountName: String,
    val newTransactions: List<TransactionItem>,
    val updatedBalance: Double,
    val message: String
)

object BankSyncEngine {

    val SUPPORTED_INSTITUTIONS = listOf(
        BankInstitution("chase", "Chase", "#117ACA", "CH", listOf(AccountType.CHECKING, AccountType.CREDIT_CARD, AccountType.SAVINGS)),
        BankInstitution("boa", "Bank of America", "#E31837", "BA", listOf(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CREDIT_CARD)),
        BankInstitution("capital_one", "Capital One", "#004B87", "C1", listOf(AccountType.CREDIT_CARD, AccountType.CHECKING, AccountType.SAVINGS)),
        BankInstitution("wells_fargo", "Wells Fargo", "#D71E28", "WF", listOf(AccountType.CHECKING, AccountType.SAVINGS)),
        BankInstitution("citi", "Citibank", "#003B70", "CI", listOf(AccountType.CREDIT_CARD, AccountType.CHECKING)),
        BankInstitution("amex", "American Express", "#006FCF", "AX", listOf(AccountType.CREDIT_CARD)),
        BankInstitution("fidelity", "Fidelity Investments", "#3B823F", "FD", listOf(AccountType.INVESTMENT, AccountType.CASH)),
        BankInstitution("ally", "Ally Bank", "#663399", "AL", listOf(AccountType.SAVINGS, AccountType.CHECKING)),
        BankInstitution("discover", "Discover", "#FF6600", "DS", listOf(AccountType.CREDIT_CARD, AccountType.SAVINGS)),
        BankInstitution("schwab", "Charles Schwab", "#00A3E0", "CS", listOf(AccountType.INVESTMENT, AccountType.CHECKING))
    )

    private val RECENT_CANDIDATE_TRANSACTIONS = listOf(
        Triple("TARGET T-0943 REDWOOD CITY CA", 42.18, TransactionType.EXPENSE),
        Triple("SHELL OIL 57542104928 SAN JOSE CA", 48.50, TransactionType.EXPENSE),
        Triple("TRADER JOE'S #541 MENLO PARK", 73.12, TransactionType.EXPENSE),
        Triple("STARBUCKS STORE #10842 PALO ALTO", 6.85, TransactionType.EXPENSE),
        Triple("SPOTIFY USA MONTHLY SUB NEW YORK NY", 11.99, TransactionType.EXPENSE),
        Triple("AMZN Mktp US*9K12L3 WA 98109", 34.90, TransactionType.EXPENSE),
        Triple("DOORDASH*SWEETGREEN RESTAURANT", 24.30, TransactionType.EXPENSE),
        Triple("CVS/PHARMACY #09320 EL CAMINO REAL", 18.75, TransactionType.EXPENSE),
        Triple("DIRECT DEPOSIT TECH CORP PAYROLL", 1820.00, TransactionType.INCOME),
        Triple("CHEVRON 0092144 OAKLAND CA", 55.40, TransactionType.EXPENSE),
        Triple("UBER *TRIP 829A0 MISSION ST", 19.20, TransactionType.EXPENSE),
        Triple("SWEETGREEN MARKET ST SAN FRANCISCO", 16.45, TransactionType.EXPENSE)
    )

    fun simulateLiveBankSync(
        account: BankAccount,
        existingTxs: List<TransactionItem>,
        rules: List<CategorizationRule>,
        categories: List<CategoryItem>
    ): SyncResult {
        val existingHashes = existingTxs.map { "${it.rawDescription}_${it.amount.toInt()}" }.toSet()

        // Pick 1 to 3 random realistic new transactions that aren't yet in the database
        val available = RECENT_CANDIDATE_TRANSACTIONS.filter {
            !existingHashes.contains("${it.first}_${it.second.toInt()}")
        }.shuffled()

        val countToGenerate = if (available.isNotEmpty()) Random.nextInt(1, minOf(3, available.size) + 1) else 0
        val selected = available.take(countToGenerate)

        val newTransactions = mutableListOf<TransactionItem>()
        var balanceChange = 0.0
        val now = System.currentTimeMillis()

        for ((index, item) in selected.withIndex()) {
            val (rawDesc, amount, type) = item
            val cleanMerchant = CategorizationEngine.cleanMerchantName(rawDesc)
            val catResult = CategorizationEngine.categorize(rawDesc, cleanMerchant, amount, type, rules, categories)

            // Stagger timestamp in recent hours
            val txTime = now - (index * 45 * 60 * 1000L + Random.nextInt(1000, 30000))

            val tx = TransactionItem(
                id = "tx_sync_${UUID.randomUUID().toString().take(8)}",
                accountId = account.id,
                amount = amount,
                type = type,
                timestamp = txTime,
                rawDescription = rawDesc,
                cleanMerchant = cleanMerchant,
                categoryId = catResult.categoryId,
                categoryName = catResult.categoryName,
                categoryIcon = catResult.categoryIcon,
                categoryColorHex = catResult.categoryColorHex,
                autoCategorized = true,
                confidenceScore = catResult.confidenceScore,
                ruleMatched = catResult.reason,
                notes = "Auto-synced from ${account.institutionName}"
            )
            newTransactions.add(tx)

            if (type == TransactionType.INCOME) {
                balanceChange += amount
            } else {
                balanceChange -= amount
            }
        }

        val updatedBalance = if (account.accountType == AccountType.CREDIT_CARD) {
            // For credit card, expenses increase the balance owed
            account.balance - balanceChange
        } else {
            account.balance + balanceChange
        }

        val message = if (newTransactions.isEmpty()) {
            "Account is up to date. No new transactions posted."
        } else {
            "Found ${newTransactions.size} new posted transactions. All auto-categorized."
        }

        return SyncResult(
            accountId = account.id,
            accountName = "${account.institutionName} ${account.accountName}",
            newTransactions = newTransactions,
            updatedBalance = kotlin.math.max(0.0, updatedBalance),
            message = message
        )
    }

    /**
     * Parses standard bank CSV text.
     * Supports formats:
     * Date,Description,Amount
     * Date,Merchant,Amount,Type
     * Posting Date,Description,Amount,Balance
     */
    fun parseBankCsv(
        csvContent: String,
        accountId: String,
        rules: List<CategorizationRule>,
        categories: List<CategoryItem>
    ): List<TransactionItem> {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val parsedTransactions = mutableListOf<TransactionItem>()
        val dateFormats = listOf(
            SimpleDateFormat("MM/dd/yyyy", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("MM-dd-yyyy", Locale.US)
        )

        // Find header column indices
        var dateIdx = 0
        var descIdx = 1
        var amountIdx = 2

        var startLine = 0
        val header = lines.first().lowercase(Locale.ROOT)
        if (header.contains("date") || header.contains("description") || header.contains("amount")) {
            startLine = 1
            val cols = lines.first().split(",").map { it.trim().lowercase(Locale.ROOT).replace("\"", "") }
            cols.forEachIndexed { i, col ->
                if (col.contains("date")) dateIdx = i
                if (col.contains("desc") || col.contains("merchant") || col.contains("name") || col.contains("payee")) descIdx = i
                if (col.contains("amount")) amountIdx = i
            }
        }

        for (i in startLine until lines.size) {
            val line = lines[i]
            val cols = line.split(",").map { it.trim().replace("\"", "") }
            if (cols.size <= maxOf(dateIdx, descIdx, amountIdx)) continue

            val rawDate = cols[dateIdx]
            val rawDesc = cols[descIdx]
            val rawAmountStr = cols[amountIdx].replace("$", "").replace(",", "")

            var txTime = System.currentTimeMillis()
            for (df in dateFormats) {
                try {
                    val parsed = df.parse(rawDate)
                    if (parsed != null) {
                        txTime = parsed.time
                        break
                    }
                } catch (_: Exception) {}
            }

            val amountVal = rawAmountStr.toDoubleOrNull() ?: continue
            val type = if (amountVal < 0 || rawDesc.lowercase(Locale.ROOT).contains("deposit") || rawDesc.lowercase(Locale.ROOT).contains("payroll")) {
                if (amountVal > 0 && !rawDesc.lowercase(Locale.ROOT).contains("deposit")) TransactionType.EXPENSE else TransactionType.INCOME
            } else {
                TransactionType.EXPENSE
            }
            val absAmount = kotlin.math.abs(amountVal)

            val cleanMerchant = CategorizationEngine.cleanMerchantName(rawDesc)
            val catResult = CategorizationEngine.categorize(rawDesc, cleanMerchant, absAmount, type, rules, categories)

            parsedTransactions.add(
                TransactionItem(
                    id = "tx_csv_${UUID.randomUUID().toString().take(8)}",
                    accountId = accountId,
                    amount = absAmount,
                    type = type,
                    timestamp = txTime,
                    rawDescription = rawDesc,
                    cleanMerchant = cleanMerchant,
                    categoryId = catResult.categoryId,
                    categoryName = catResult.categoryName,
                    categoryIcon = catResult.categoryIcon,
                    categoryColorHex = catResult.categoryColorHex,
                    autoCategorized = true,
                    confidenceScore = catResult.confidenceScore,
                    ruleMatched = catResult.reason,
                    notes = "Imported via CSV bank statement"
                )
            )
        }

        return parsedTransactions
    }

    const val SAMPLE_BANK_CSV = """Date,Description,Amount
10/01/2026,WHOLE FOODS MARKET SFO,54.20
10/01/2026,STARBUCKS STORE #04821,5.75
10/02/2026,CHEVRON GAS STATION,45.00
10/02/2026,NETFLIX DIGITAL SUBSCRIPTION,19.99
10/03/2026,AMAZON.COM*2K83J8 RETAIL,68.40
10/03/2026,UBER TRIP SAN FRANCISCO,22.15
10/04/2026,CHIPOTLE MEXICAN GRILL,14.80
10/04/2026,DIRECT DEPOSIT PAYROLL EMPLOYER,1950.00"""
}
