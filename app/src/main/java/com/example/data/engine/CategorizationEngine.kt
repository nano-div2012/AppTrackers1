package com.example.data.engine

import com.example.data.model.CategorizationRule
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionType
import java.util.Locale

data class CategorizationResult(
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColorHex: String,
    val confidenceScore: Float,
    val reason: String
)

object CategorizationEngine {

    // Common merchant signatures and their category mappings
    private val MERCHANT_PATTERNS = mapOf(
        // Groceries
        "groceries" to listOf(
            "whole foods", "wholefds", "trader joe", "safeway", "kroger", "aldi", "costco",
            "wegmans", "heb grocery", "publix", "sprouts", "supermarket", "grocery", "food lion",
            "ralphs", "king soopers", "giant food", "market basket", "albertsons", "instacart"
        ),
        // Dining & Drinks
        "dining" to listOf(
            "starbucks", "mcdonald", "dunkin", "chipotle", "chick-fil-a", "doordash", "uber eats",
            "grubhub", "postmates", "shake shack", "sweetgreen", "panera", "blue bottle", "peet",
            "burger king", "taco bell", "wendy", "subway", "domino", "pizza", "cafe", "coffee",
            "bistro", "bakery", "sushi", "ramen", "restaurant", "diner", "bar & grill", "brewery",
            "tavern", "noodle", "roaster", "ice cream"
        ),
        // Shopping
        "shopping" to listOf(
            "amazon", "amzn", "target", "walmart", "best buy", "apple store", "zara", "nike",
            "nordstrom", "ebay", "etsy", "asos", "sephora", "ulta", "home depot", "lowe's",
            "ikea", "adidas", "h&m", "uniqlo", "costco whse", "macy", "bloomingdale", "clothing"
        ),
        // Transportation
        "transportation" to listOf(
            "uber trip", "uber *trip", "lyft", "shell oil", "chevron", "exxon", "mobil", "bp gas",
            "citgo", "speedway", "wawa", "sunoco", "texaco", "metro transit", "mta", "bart",
            "clipper", "transit", "train", "parking", "tolls", "ezpass", "fasstrak", "tesla supercharger",
            "chargepoint", "evgo", "gas station", "fuel"
        ),
        // Housing & Rent
        "housing" to listOf(
            "rent", "lease", "leasing", "property management", "mortgage", "hoa fee", "zillow rent",
            "avalon", "equity residential", "real estate", "landlord"
        ),
        // Utilities & Bills
        "utilities" to listOf(
            "electric", "power", "pge", "pg&e", "coned", "edison", "national grid", "water",
            "sewage", "internet", "comcast", "xfinity", "verizon", "at&t", "t-mobile", "spectrum",
            "charter", "waste management", "trash service", "gas bill"
        ),
        // Subscriptions
        "subscriptions" to listOf(
            "netflix", "spotify", "hulu", "disney+", "disney plus", "hbo max", "apple.com/bill",
            "google *storage", "youtube premium", "prime video", "paramount+", "nytimes", "wsj",
            "patreon", "substack", "audible", "github", "dropbox", "icloud"
        ),
        // Entertainment
        "entertainment" to listOf(
            "amc", "regal cinema", "cinemark", "steam", "playstation", "nintendo", "xbox",
            "ticketmaster", "live nation", "stubhub", "eventbrite", "bowling", "cinema",
            "theater", "concert", "museum"
        ),
        // Health & Wellness
        "healthcare" to listOf(
            "cvs", "walgreens", "rite aid", "pharmacy", "quest diagnostics", "labcorp",
            "planet fitness", "equinox", "anytime fitness", "gym", "hospital", "clinic",
            "dentist", "optical", "doctor", "health", "urgent care"
        ),
        // Travel & Lodging
        "travel" to listOf(
            "delta air", "united airlines", "american air", "southwest air", "jetblue", "alaska air",
            "airbnb", "marriott", "hilton", "hyatt", "expedia", "booking.com", "vrbo", "hertz",
            "enterprise rent-a-car", "avis", "flight", "hotel", "resort"
        ),
        // Personal Care
        "personal_care" to listOf(
            "hair salon", "barber", "nails", "spa", "massage", "skincare", "cosmetics"
        ),
        // Investments
        "investments" to listOf(
            "vanguard", "fidelity", "schwab", "robinhood", "coinbase", "etrade", "betterment", "wealthfront"
        ),
        // Income
        "income" to listOf(
            "payroll", "direct dep", "direct deposit", "salary", "employer", "venmo cashout",
            "interest credit", "tax refund", "dividend", "interest payment", "wire transfer credit"
        )
    )

    fun cleanMerchantName(raw: String): String {
        var clean = raw.trim()

        // Strip common bank transaction prefixes
        val prefixRegex = Regex(
            "^(TST\\*|SQ \\*|PAYPAL \\*|ACH DEPOSIT|CHECKCARD \\d+|PURCHASE AUTHORIZED ON \\d+/\\d+|POS DEBIT|RECURRING PAYMENT|ONLINE PAYMENT|WIRE|TRANSFER FROM|DIRECT DEP|DEBIT CARD PURCHASE -|IN-STORE PURCHASE -)\\s*",
            RegexOption.IGNORE_CASE
        )
        clean = prefixRegex.replace(clean, "")

        // Strip dates like 10/24 or 2026-10-04 inside text
        clean = Regex("\\b\\d{1,2}/\\d{1,2}(/\\d{2,4})?\\b").replace(clean, "")

        // Strip phone numbers or transaction confirmation IDs
        clean = Regex("\\b\\d{3}-\\d{3}-\\d{4}\\b").replace(clean, "")
        clean = Regex("\\b(ACCT|REF|AUTH|CONF|ID)\\s*#?[0-9A-Z]+\\b", RegexOption.IGNORE_CASE).replace(clean, "")

        // Strip trailing postal code and 2-letter state: e.g. "CA 94103" or "NY"
        clean = Regex("\\b[A-Z]{2}\\s+\\d{5}(-\\d{4})?\\b").replace(clean, "")
        clean = Regex("\\s+[A-Z]{2}\\b").replace(clean, "")

        // Strip store numbers like #1043 or STORE 523
        clean = Regex("#\\d+|STORE\\s+\\d+", RegexOption.IGNORE_CASE).replace(clean, "")

        // Normalize multiple spaces
        clean = clean.replace(Regex("\\s+"), " ").trim()

        // Friendly name substitutions for known giants
        val lower = clean.lowercase(Locale.ROOT)
        return when {
            lower.contains("whole foods") || lower.contains("wholefds") -> "Whole Foods Market"
            lower.contains("trader joe") -> "Trader Joe's"
            lower.contains("starbucks") -> "Starbucks"
            lower.contains("blue bottle") -> "Blue Bottle Coffee"
            lower.contains("chipotle") -> "Chipotle Mexican Grill"
            lower.contains("amazon") || lower.contains("amzn") -> "Amazon"
            lower.contains("target") -> "Target"
            lower.contains("walmart") -> "Walmart"
            lower.contains("uber *trip") || lower.contains("uber trip") -> "Uber Ride"
            lower.contains("uber eats") -> "Uber Eats"
            lower.contains("doordash") -> "DoorDash"
            lower.contains("netflix") -> "Netflix"
            lower.contains("spotify") -> "Spotify"
            lower.contains("shell") -> "Shell Oil"
            lower.contains("chevron") -> "Chevron"
            lower.contains("pge") || lower.contains("pg&e") -> "PG&E Utility"
            lower.contains("payroll") || lower.contains("direct dep") -> "Direct Deposit - Payroll"
            clean.length > 25 -> clean.take(24).trim()
            clean.isBlank() -> "Bank Transaction"
            else -> clean.split(" ").joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }
    }

    fun categorize(
        rawDescription: String,
        cleanMerchant: String,
        amount: Double,
        type: TransactionType,
        customRules: List<CategorizationRule>,
        categories: List<CategoryItem>
    ): CategorizationResult {
        val catMap = categories.associateBy { it.id }
        val searchText = "${rawDescription.lowercase(Locale.ROOT)} ${cleanMerchant.lowercase(Locale.ROOT)}"

        // 1. If explicit INCOME type, check if it matches income patterns
        if (type == TransactionType.INCOME) {
            val incomeCat = catMap["income"] ?: categories.firstOrNull { it.isIncome }
            if (incomeCat != null) {
                return CategorizationResult(
                    categoryId = incomeCat.id,
                    categoryName = incomeCat.name,
                    categoryIcon = incomeCat.icon,
                    categoryColorHex = incomeCat.colorHex,
                    confidenceScore = 0.98f,
                    reason = "Income transaction type match"
                )
            }
        }

        // 2. Custom User Rules have highest precedence
        for (rule in customRules) {
            val keyword = rule.keyword.lowercase(Locale.ROOT).trim()
            val matched = if (rule.isRegex) {
                try {
                    Regex(keyword, RegexOption.IGNORE_CASE).containsMatchIn(searchText)
                } catch (e: Exception) {
                    false
                }
            } else {
                searchText.contains(keyword)
            }

            if (matched) {
                val cat = catMap[rule.targetCategoryId]
                if (cat != null) {
                    return CategorizationResult(
                        categoryId = cat.id,
                        categoryName = cat.name,
                        categoryIcon = cat.icon,
                        categoryColorHex = cat.colorHex,
                        confidenceScore = 1.0f,
                        reason = "Custom user rule matched: '${rule.keyword}'"
                    )
                }
            }
        }

        // 3. Built-in merchant patterns
        for ((catId, patterns) in MERCHANT_PATTERNS) {
            for (pattern in patterns) {
                if (searchText.contains(pattern)) {
                    val cat = catMap[catId]
                    if (cat != null) {
                        val confidence = if (cleanMerchant.lowercase(Locale.ROOT).contains(pattern)) 0.97f else 0.88f
                        return CategorizationResult(
                            categoryId = cat.id,
                            categoryName = cat.name,
                            categoryIcon = cat.icon,
                            categoryColorHex = cat.colorHex,
                            confidenceScore = confidence,
                            reason = "Automated pattern match: '$pattern'"
                        )
                    }
                }
            }
        }

        // 4. Default Fallback
        val defaultCat = catMap["other"] ?: categories.firstOrNull { !it.isIncome } ?: categories.first()
        return CategorizationResult(
            categoryId = defaultCat.id,
            categoryName = defaultCat.name,
            categoryIcon = defaultCat.icon,
            categoryColorHex = defaultCat.colorHex,
            confidenceScore = 0.50f,
            reason = "Default fallback (Uncategorized)"
        )
    }
}
