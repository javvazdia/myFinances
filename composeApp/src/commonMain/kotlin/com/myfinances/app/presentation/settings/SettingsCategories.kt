package com.myfinances.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryFormCard(
    uiState: SettingsUiState,
    onNameChange: (String) -> Unit,
    onKindSelected: (CategoryKind) -> Unit,
    onSaveCategory: () -> Unit,
    onCancelEditing: () -> Unit,
) {
    var kindMenuExpanded by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.editingCategoryId) {
        if (uiState.editingCategoryId != null) {
            nameFocusRequester.requestFocus()
        }
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (uiState.isEditing) "Edit category" else "Create category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = if (uiState.isEditing) {
                    "You are updating a category in place, so existing transactions keep pointing to the same local category id."
                } else {
                    "This first flow creates custom categories locally. Later we can add icons, colors, rules, and sync mapping for imported transactions."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.draftName,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester),
                label = { Text("Category name") },
                supportingText = { Text("Examples: Dining Out, Rent, Freelance") },
                singleLine = true,
                enabled = !uiState.isBusy,
            )

            ExposedDropdownMenuBox(
                expanded = kindMenuExpanded,
                onExpandedChange = { kindMenuExpanded = !kindMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedKind.label,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    enabled = !uiState.isBusy,
                    label = { Text("Category type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = kindMenuExpanded,
                    onDismissRequest = { kindMenuExpanded = false },
                ) {
                    CategoryKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.label) },
                            onClick = {
                                onKindSelected(kind)
                                kindMenuExpanded = false
                            },
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(
                    onClick = onSaveCategory,
                    enabled = !uiState.isBusy,
                ) {
                    Text(
                        when {
                            uiState.isSaving -> "Saving..."
                            uiState.isEditing -> "Save changes"
                            else -> "Create category"
                        },
                    )
                }

                if (uiState.isEditing) {
                    TextButton(
                        onClick = onCancelEditing,
                        enabled = !uiState.isBusy,
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryCard(
    category: Category,
    onEditCategory: (String) -> Unit,
    onRequestDeleteCategory: (String) -> Unit,
    canInteract: Boolean,
    isEditing: Boolean,
    isDeleting: Boolean,
) {
    Card(
        colors = if (isEditing) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = category.kind.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = if (category.isSystem) "System" else "Custom",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (category.isSystem) {
                Text(
                    text = "System categories are seeded defaults and can't be edited or deleted from the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onEditCategory(category.id) },
                        enabled = canInteract,
                    ) {
                        Text(if (isEditing) "Editing" else "Edit")
                    }
                    TextButton(
                        onClick = { onRequestDeleteCategory(category.id) },
                        enabled = canInteract,
                    ) {
                        Text(
                            text = if (isDeleting) "Deleting..." else "Delete",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

internal val CategoryKind.label: String
    get() = name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }

internal fun normalizeCategoryName(rawValue: String): String =
    rawValue
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" ")
