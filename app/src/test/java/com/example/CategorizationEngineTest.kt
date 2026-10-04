package com.example

import com.example.data.engine.BankSyncEngine
import com.example.data.engine.CategorizationEngine
import com.example.data.local.AppDatabase
import com.example.data.model.CategorizationRule
import com.example.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizationEngineTest {

    @Test
    fun testMerchantCleaning() {
        val raw1 = "WHOLEFDS SFO 10243 SAN FRANCISCO CA"
        val clean1 = CategorizationEngine.cleanMerchantName(raw1)
        assertEquals("Whole Foods Market", clean1)

        val raw2 = "TST* BLUE BOTTLE COFFEE #482"
        val clean2 = CategorizationEngine.cleanMerchantName(raw2)
        assertEquals("Blue Bottle Coffee", clean2)

        val raw3 = "AMZN Mktp US*3M2K84 WA 98109"
        val clean3 = CategorizationEngine.cleanMerchantName(raw3)
        assertEquals("Amazon", clean3)
    }

    @Test
    fun testAutoCategorization() {
        val categories = AppDatabase.DEFAULT_CATEGORIES
        val rules = AppDatabase.DEFAULT_RULES

        val result1 = CategorizationEngine.categorize(
            rawDescription = "WHOLEFDS SFO 10243",
            cleanMerchant = "Whole Foods Market",
            amount = 45.20,
            type = TransactionType.EXPENSE,
            customRules = rules,
            categories = categories
        )
        assertEquals("groceries", result1.categoryId)
        assertTrue(result1.confidenceScore >= 0.90f)

        val result2 = CategorizationEngine.categorize(
            rawDescription = "NETFLIX.COM DIGITAL SUBSCRIPTION",
            cleanMerchant = "Netflix",
            amount = 19.99,
            type = TransactionType.EXPENSE,
            customRules = rules,
            categories = categories
        )
        assertEquals("subscriptions", result2.categoryId)
        assertTrue(result2.confidenceScore >= 0.95f)
    }

    @Test
    fun testCustomUserRulePrecedence() {
        val categories = AppDatabase.DEFAULT_CATEGORIES
        val customRule = CategorizationRule(
            keyword = "steamer",
            targetCategoryId = "entertainment",
            targetCategoryName = "Entertainment"
        )

        val result = CategorizationEngine.categorize(
            rawDescription = "STEAMER GAMES PURCHASE",
            cleanMerchant = "Steamer Games",
            amount = 29.99,
            type = TransactionType.EXPENSE,
            customRules = listOf(customRule),
            categories = categories
        )
        assertEquals("entertainment", result.categoryId)
        assertEquals(1.0f, result.confidenceScore, 0.01f)
    }

    @Test
    fun testBankCsvParsing() {
        val categories = AppDatabase.DEFAULT_CATEGORIES
        val rules = AppDatabase.DEFAULT_RULES
        val parsed = BankSyncEngine.parseBankCsv(BankSyncEngine.SAMPLE_BANK_CSV, "acc_test", rules, categories)

        assertTrue(parsed.isNotEmpty())
        assertEquals(8, parsed.size)
        assertEquals("groceries", parsed[0].categoryId)
        assertEquals("dining", parsed[1].categoryId)
        assertEquals("transportation", parsed[2].categoryId)
        assertEquals("subscriptions", parsed[3].categoryId)
    }
}
