package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionItem
import com.example.ui.screens.AccountsSyncScreen
import com.example.ui.screens.AddTransactionDialog
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.ConnectBankSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditBudgetDialog
import com.example.ui.screens.ImportStatementDialog
import com.example.ui.screens.RulesScreen
import com.example.ui.screens.TransactionDetailSheet
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.LedgerSyncTheme
import com.example.ui.viewmodel.FinanceViewModel

enum class MainNavigationTab(val title: String, val icon: ImageVector, val tag: String) {
    OVERVIEW("Overview", Icons.Default.Dashboard, "tab_overview"),
    TRANSACTIONS("Transactions", Icons.Default.ReceiptLong, "tab_transactions"),
    ACCOUNTS("Accounts", Icons.Default.AccountBalance, "tab_accounts"),
    BUDGETS("Budgets", Icons.Default.PieChart, "tab_budgets"),
    RULES("Rules & AI", Icons.Default.AutoAwesome, "tab_rules")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LedgerSyncTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: FinanceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by remember { mutableStateOf(MainNavigationTab.OVERVIEW) }

    // Bottom sheet states
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionItem?>(null) }
    var showConnectBankSheet by remember { mutableStateOf(false) }
    var showImportStatementSheet by remember { mutableStateOf(false) }
    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var selectedCategoryForBudgetEdit by remember { mutableStateOf<CategoryItem?>(null) }

    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val connectSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val importSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addTxSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val budgetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle back button: if not on Overview tab, go to Overview first
    BackHandler(enabled = currentTab != MainNavigationTab.OVERVIEW) {
        currentTab = MainNavigationTab.OVERVIEW
    }

    // Show notifications from ViewModel in snackbar
    LaunchedEffect(uiState.userNotification) {
        uiState.userNotification?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearNotification()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LedgerSync",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncAllAccounts() },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.testTag("topbar_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync All Bank Accounts",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                MainNavigationTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == MainNavigationTab.OVERVIEW || currentTab == MainNavigationTab.TRANSACTIONS) {
                FloatingActionButton(
                    onClick = { showAddTransactionSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("main_fab_add_transaction")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainNavigationTab.OVERVIEW -> {
                    DashboardScreen(
                        uiState = uiState,
                        onSyncAllClick = { viewModel.syncAllAccounts() },
                        onConnectBankClick = { showConnectBankSheet = true },
                        onImportStatementClick = { showImportStatementSheet = true },
                        onAddTransactionClick = { showAddTransactionSheet = true },
                        onTransactionClick = { tx -> selectedTransactionForDetail = tx },
                        onViewAllTransactionsClick = { currentTab = MainNavigationTab.TRANSACTIONS }
                    )
                }
                MainNavigationTab.TRANSACTIONS -> {
                    TransactionsScreen(
                        uiState = uiState,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onAccountFilterChange = { viewModel.setFilterAccountId(it) },
                        onCategoryFilterChange = { viewModel.setFilterCategoryId(it) },
                        onTypeFilterChange = { viewModel.setFilterType(it) },
                        onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                    )
                }
                MainNavigationTab.ACCOUNTS -> {
                    AccountsSyncScreen(
                        uiState = uiState,
                        onSyncAllClick = { viewModel.syncAllAccounts() },
                        onSyncSingleAccountClick = { accId -> viewModel.syncSingleAccount(accId) },
                        onDeleteAccountClick = { accId -> viewModel.deleteAccount(accId) },
                        onConnectBankClick = { showConnectBankSheet = true },
                        onImportStatementClick = { showImportStatementSheet = true }
                    )
                }
                MainNavigationTab.BUDGETS -> {
                    BudgetsScreen(
                        uiState = uiState,
                        onEditBudgetClick = { cat -> selectedCategoryForBudgetEdit = cat }
                    )
                }
                MainNavigationTab.RULES -> {
                    RulesScreen(
                        uiState = uiState,
                        onAddRule = { keyword, catId -> viewModel.addCategorizationRule(keyword, catId) },
                        onDeleteRule = { ruleId -> viewModel.deleteCategorizationRule(ruleId) },
                        onRecategorizeAll = { viewModel.recategorizeAll() }
                    )
                }
            }
        }
    }

    // Transaction Details Sheet
    selectedTransactionForDetail?.let { tx ->
        val account = uiState.accounts.firstOrNull { it.id == tx.accountId }
        TransactionDetailSheet(
            transaction = tx,
            account = account,
            categories = uiState.categories,
            sheetState = detailSheetState,
            onDismiss = { selectedTransactionForDetail = null },
            onCategoryChanged = { newCatId, createRule ->
                viewModel.updateTransactionCategory(tx.id, newCatId, createRule)
                selectedTransactionForDetail = null
            },
            onDeleteTransaction = {
                viewModel.deleteTransaction(tx)
                selectedTransactionForDetail = null
            }
        )
    }

    // Link Bank Account Sheet
    if (showConnectBankSheet) {
        ConnectBankSheet(
            sheetState = connectSheetState,
            onDismiss = { showConnectBankSheet = false },
            onConnect = { inst, name, type, balance, mask ->
                viewModel.connectNewBank(inst, name, type, balance, mask)
                showConnectBankSheet = false
            }
        )
    }

    // Import Bank Statement CSV Sheet
    if (showImportStatementSheet) {
        ImportStatementDialog(
            accounts = uiState.accounts,
            sheetState = importSheetState,
            onDismiss = { showImportStatementSheet = false },
            onImport = { csvText, accountId ->
                viewModel.importCsvStatement(csvText, accountId)
                showImportStatementSheet = false
            }
        )
    }

    // Add Manual Transaction Sheet
    if (showAddTransactionSheet) {
        AddTransactionDialog(
            accounts = uiState.accounts,
            categories = uiState.categories,
            sheetState = addTxSheetState,
            onDismiss = { showAddTransactionSheet = false },
            onSave = { accId, amount, type, desc, catId, notes ->
                viewModel.addManualTransaction(accId, amount, type, desc, catId, notes)
                showAddTransactionSheet = false
            }
        )
    }

    // Edit Budget Sheet
    selectedCategoryForBudgetEdit?.let { cat ->
        EditBudgetDialog(
            category = cat,
            sheetState = budgetSheetState,
            onDismiss = { selectedCategoryForBudgetEdit = null },
            onSaveBudget = { newBudget ->
                viewModel.updateBudget(cat.id, newBudget)
                selectedCategoryForBudgetEdit = null
            }
        )
    }
}
