package com.myfinances.app.presentation.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.repository.LedgerRepository

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
        onShowCreateForm = transactionsViewModel::showCreateForm,
        onHideTransactionForm = transactionsViewModel::hideTransactionForm,
        onTypeSelected = transactionsViewModel::onTypeSelected,
        onAccountSelected = transactionsViewModel::onAccountSelected,
        onCategorySelected = transactionsViewModel::onCategorySelected,
        onAmountChange = transactionsViewModel::onAmountChange,
        onMerchantChange = transactionsViewModel::onMerchantChange,
        onNoteChange = transactionsViewModel::onNoteChange,
        onSaveTransaction = transactionsViewModel::saveTransaction,
        onShowTransactionDetails = transactionsViewModel::showTransactionDetails,
        onDismissTransactionDetails = transactionsViewModel::dismissTransactionDetails,
        onEditTransaction = transactionsViewModel::editTransaction,
        onRequestDeleteTransaction = transactionsViewModel::requestDeleteTransaction,
        onConfirmDeleteTransaction = transactionsViewModel::confirmDeleteTransaction,
        onDismissDeleteDialog = transactionsViewModel::dismissDeleteDialog,
    )
}
