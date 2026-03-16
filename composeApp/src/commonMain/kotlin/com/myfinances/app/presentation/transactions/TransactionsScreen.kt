package com.myfinances.app.presentation.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

@Composable
fun TransactionsRoute(
    ledgerRepository: LedgerRepository,
    transactionsViewModel: TransactionsViewModel = viewModel {
        TransactionsViewModel(ledgerRepository)
    },
) {
    val uiState by transactionsViewModel.uiState.collectAsState()
    TransactionsScreen(
        uiState = uiState,
        onTypeSelected = transactionsViewModel::onTypeSelected,
        onAccountSelected = transactionsViewModel::onAccountSelected,
        onCategorySelected = transactionsViewModel::onCategorySelected,
        onAmountChange = transactionsViewModel::onAmountChange,
        onMerchantChange = transactionsViewModel::onMerchantChange,
        onNoteChange = transactionsViewModel::onNoteChange,
        onCreateTransaction = transactionsViewModel::createTransaction,
    )
}

@Composable
fun TransactionsScreen(
    uiState: TransactionsUiState,
    onTypeSelected: (TransactionType) -> Unit,
    onAccountSelected: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCreateTransaction: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Transactions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Text(
                text = "This first transaction flow is manual and local-first. Later, imported bank transactions can land in the same ledger with provider metadata and reconciliation rules.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            CreateTransactionCard(
                uiState = uiState,
                onTypeSelected = onTypeSelected,
                onAccountSelected = onAccountSelected,
                onCategorySelected = onCategorySelected,
                onAmountChange = onAmountChange,
                onMerchantChange = onMerchantChange,
                onNoteChange = onNoteChange,
                onCreateTransaction = onCreateTransaction,
            )
        }

        item {
            Text(
                text = "Recent transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (uiState.recentTransactions.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "No transactions yet. Create one above and it will appear here immediately.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(uiState.recentTransactions) { transaction ->
                TransactionCard(transaction = transaction)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTransactionCard(
    uiState: TransactionsUiState,
    onTypeSelected: (TransactionType) -> Unit,
    onAccountSelected: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCreateTransaction: () -> Unit,
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Create transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = !typeMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedType.label,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    enabled = !uiState.isSaving,
                    label = { Text("Transaction type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    manualTransactionTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                onTypeSelected(type)
                                typeMenuExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = accountMenuExpanded,
                onExpandedChange = { accountMenuExpanded = !accountMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedAccountName ?: "Select account",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    enabled = !uiState.isSaving && uiState.accounts.isNotEmpty(),
                    label = { Text("Account") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false },
                ) {
                    uiState.accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                onAccountSelected(account.id)
                                accountMenuExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedCategoryName ?: "Select category",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    enabled = !uiState.isSaving && uiState.availableCategories.isNotEmpty(),
                    label = { Text("Category") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                ) {
                    uiState.availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onCategorySelected(category.id)
                                categoryMenuExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.draftAmount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") },
                supportingText = { Text("Examples: 18.75 or 1200.00") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                singleLine = true,
                enabled = !uiState.isSaving,
            )

            OutlinedTextField(
                value = uiState.draftMerchant,
                onValueChange = onMerchantChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Merchant or source") },
                singleLine = true,
                enabled = !uiState.isSaving,
            )

            OutlinedTextField(
                value = uiState.draftNote,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 2,
                enabled = !uiState.isSaving,
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = onCreateTransaction,
                enabled = !uiState.isSaving && uiState.accounts.isNotEmpty(),
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Create transaction")
            }
        }
    }
}

@Composable
private fun TransactionCard(transaction: TransactionCardUiModel) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${transaction.accountName} · ${transaction.categoryName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = transaction.amountLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (transaction.isExpense) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = transaction.dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class TransactionsUiState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recentTransactions: List<TransactionCardUiModel> = emptyList(),
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val draftAmount: String = "",
    val draftMerchant: String = "",
    val draftNote: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val availableCategories: List<Category>
        get() = categories.filter { category -> category.kind == selectedType.categoryKind }

    val selectedAccountName: String?
        get() = accounts.firstOrNull { account -> account.id == selectedAccountId }?.name

    val selectedCategoryName: String?
        get() = categories.firstOrNull { category -> category.id == selectedCategoryId }?.name
}

data class TransactionCardUiModel(
    val id: String,
    val title: String,
    val accountName: String,
    val categoryName: String,
    val amountLabel: String,
    val dateLabel: String,
    val isExpense: Boolean,
)

class TransactionsViewModel(
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                ledgerRepository.observeAccounts(),
                ledgerRepository.observeCategories(),
                ledgerRepository.observeRecentTransactions(limit = 20),
            ) { accounts, categories, transactions ->
                Triple(accounts, categories, transactions)
            }.collect { (accounts, categories, transactions) ->
                _uiState.update { currentState ->
                    val nextSelectedAccountId = currentState.selectedAccountId
                        ?.takeIf { selectedId -> accounts.any { account -> account.id == selectedId } }
                        ?: accounts.firstOrNull()?.id

                    val availableCategories = categories.filter { category ->
                        category.kind == currentState.selectedType.categoryKind
                    }
                    val nextSelectedCategoryId = currentState.selectedCategoryId
                        ?.takeIf { selectedId ->
                            availableCategories.any { category -> category.id == selectedId }
                        }
                        ?: availableCategories.firstOrNull()?.id

                    currentState.copy(
                        accounts = accounts,
                        categories = categories,
                        recentTransactions = transactions.map { transaction ->
                            transaction.toCardUiModel(accounts, categories)
                        },
                        selectedAccountId = nextSelectedAccountId,
                        selectedCategoryId = nextSelectedCategoryId,
                    )
                }
            }
        }
    }

    fun onTypeSelected(value: TransactionType) {
        _uiState.update { currentState ->
            val availableCategories = currentState.categories.filter { category ->
                category.kind == value.categoryKind
            }
            currentState.copy(
                selectedType = value,
                selectedCategoryId = availableCategories.firstOrNull()?.id,
                errorMessage = null,
            )
        }
    }

    fun onAccountSelected(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedAccountId = value,
                errorMessage = null,
            )
        }
    }

    fun onCategorySelected(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedCategoryId = value,
                errorMessage = null,
            )
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftAmount = value,
                errorMessage = null,
            )
        }
    }

    fun onMerchantChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftMerchant = value,
                errorMessage = null,
            )
        }
    }

    fun onNoteChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftNote = value,
                errorMessage = null,
            )
        }
    }

    fun createTransaction() {
        val snapshot = uiState.value
        val selectedAccount = snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }
        val selectedCategoryId = snapshot.selectedCategoryId
        val amountMinor = parseTransactionAmountToMinor(snapshot.draftAmount)

        val error = when {
            selectedAccount == null -> "Select an account first."
            selectedCategoryId == null -> "Select a category first."
            amountMinor == null || amountMinor <= 0L ->
                "Amount must be a positive number with up to 2 decimals."
            else -> null
        }

        if (error != null) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = error)
            }
            return
        }

        val validatedAccount = selectedAccount ?: return
        val validatedAmountMinor = amountMinor ?: return

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val transaction = FinanceTransaction(
                id = generateTransactionId(snapshot.selectedType, now),
                accountId = validatedAccount.id,
                categoryId = selectedCategoryId,
                type = snapshot.selectedType,
                amountMinor = validatedAmountMinor,
                currencyCode = validatedAccount.currencyCode,
                merchantName = snapshot.draftMerchant.trim().ifBlank { null },
                note = snapshot.draftNote.trim().ifBlank { null },
                sourceProvider = null,
                externalTransactionId = null,
                postedAtEpochMs = now,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )

            ledgerRepository.upsertTransaction(transaction)

            _uiState.update { currentState ->
                currentState.copy(
                    draftAmount = "",
                    draftMerchant = "",
                    draftNote = "",
                    isSaving = false,
                    errorMessage = null,
                )
            }
        }
    }
}

private fun FinanceTransaction.toCardUiModel(
    accounts: List<Account>,
    categories: List<Category>,
): TransactionCardUiModel {
    val accountName = accounts.firstOrNull { account -> account.id == accountId }?.name ?: "Unknown account"
    val categoryName = categories.firstOrNull { category -> category.id == categoryId }?.name ?: "Uncategorized"
    val isExpense = type == TransactionType.EXPENSE

    return TransactionCardUiModel(
        id = id,
        title = merchantName ?: note ?: type.label,
        accountName = accountName,
        categoryName = categoryName,
        amountLabel = buildSignedAmountLabel(amountMinor, currencyCode, isExpense),
        dateLabel = "Today",
        isExpense = isExpense,
    )
}

private fun buildSignedAmountLabel(
    amountMinor: Long,
    currencyCode: String,
    isExpense: Boolean,
): String {
    val sign = if (isExpense) "-" else "+"
    return sign + formatTransactionMoney(amountMinor, currencyCode)
}

internal val manualTransactionTypes: List<TransactionType> = listOf(
    TransactionType.EXPENSE,
    TransactionType.INCOME,
)

internal val TransactionType.label: String
    get() = name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }

internal val TransactionType.categoryKind: CategoryKind
    get() = when (this) {
        TransactionType.INCOME -> CategoryKind.INCOME
        TransactionType.EXPENSE -> CategoryKind.EXPENSE
        TransactionType.TRANSFER -> CategoryKind.TRANSFER
        TransactionType.ADJUSTMENT -> CategoryKind.EXPENSE
    }

internal fun parseTransactionAmountToMinor(rawValue: String): Long? {
    val normalized = rawValue.trim().replace(',', '.')
    if (normalized.isBlank()) return null

    val amountPattern = Regex("^\\d+(\\.\\d{0,2})?$")
    if (!amountPattern.matches(normalized)) return null

    val parts = normalized.split('.')
    val wholePart = parts.firstOrNull()?.toLongOrNull() ?: return null
    val decimalPart = parts.getOrNull(1)?.padEnd(2, '0') ?: "00"
    return (wholePart * 100) + decimalPart.toLong()
}

internal fun generateTransactionId(type: TransactionType, timestampMs: Long): String =
    "txn-${type.name.lowercase()}-$timestampMs-${Random.nextInt(1000, 9999)}"

internal fun formatTransactionMoney(amountMinor: Long, currencyCode: String): String {
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    return "$major.${minor.toString().padStart(2, '0')} $currencyCode"
}
