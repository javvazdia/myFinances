package com.myfinances.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.CategoryKind

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSelectConnection: (String) -> Unit,
    onIndexaTokenChange: (String) -> Unit,
    onTestIndexaConnection: () -> Unit,
    onConnectIndexa: () -> Unit,
    onRunIndexaSync: () -> Unit,
    onRequestDisconnectConnection: (String) -> Unit,
    onConfirmDisconnectConnection: () -> Unit,
    onDismissDisconnectDialog: () -> Unit,
    onNameChange: (String) -> Unit,
    onKindSelected: (CategoryKind) -> Unit,
    onSaveCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onRequestDeleteCategory: (String) -> Unit,
    onConfirmDeleteCategory: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.editingCategoryId) {
        if (uiState.editingCategoryId != null) {
            listState.animateScrollToItem(index = 3)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Text(
                text = "Use categories to organize manual entries now and to classify imported transactions later when we connect external providers.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            ConnectionsOverviewCard(
                uiState = uiState,
                onSelectConnection = onSelectConnection,
                onIndexaTokenChange = onIndexaTokenChange,
                onTestIndexaConnection = onTestIndexaConnection,
                onConnectIndexa = onConnectIndexa,
                onRunIndexaSync = onRunIndexaSync,
                onRequestDisconnectConnection = onRequestDisconnectConnection,
            )
        }

        item {
            CategoryFormCard(
                uiState = uiState,
                onNameChange = onNameChange,
                onKindSelected = onKindSelected,
                onSaveCategory = onSaveCategory,
                onCancelEditing = onCancelEditing,
            )
        }

        item {
            Text(
                text = "Current categories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (uiState.categories.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "No categories yet. Create your first one above.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            CategoryKind.entries.forEach { kind ->
                val categoriesForKind = uiState.categoriesByKind[kind].orEmpty()
                if (categoriesForKind.isNotEmpty()) {
                    item {
                        Text(
                            text = kind.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(
                        items = categoriesForKind,
                        key = { category -> category.id },
                    ) { category ->
                        CategoryCard(
                            category = category,
                            onEditCategory = onEditCategory,
                            onRequestDeleteCategory = onRequestDeleteCategory,
                            canInteract = !uiState.isBusy,
                            isEditing = uiState.editingCategoryId == category.id,
                            isDeleting = uiState.pendingDeleteCategoryId == category.id,
                        )
                    }
                }
            }
        }
    }

    if (uiState.deleteConfirmationCategoryId != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = {
                Text("Delete category?")
            },
            text = {
                Text(
                    "Delete ${uiState.deleteConfirmationCategoryName ?: "this category"}? Existing transactions will remain, but their category will be cleared.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteCategory,
                    enabled = !uiState.isBusy,
                ) {
                    Text(if (uiState.pendingDeleteCategoryId != null) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDeleteDialog,
                    enabled = !uiState.isBusy,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (uiState.disconnectConfirmationConnectionId != null) {
        AlertDialog(
            onDismissRequest = onDismissDisconnectDialog,
            title = {
                Text("Disconnect provider?")
            },
            text = {
                Text(
                    "Disconnect ${uiState.disconnectConfirmationConnectionName ?: "this connection"}? Imported local accounts, holdings, and transactions will stay in the ledger, but future syncs will stop until you reconnect.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDisconnectConnection,
                    enabled = uiState.pendingDisconnectConnectionId == null,
                ) {
                    Text(if (uiState.pendingDisconnectConnectionId != null) "Disconnecting..." else "Disconnect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDisconnectDialog,
                    enabled = uiState.pendingDisconnectConnectionId == null,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
