package com.myfinances.app.presentation.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun TransactionCard(
    transaction: TransactionCardUiModel,
    onShowDetails: (String) -> Unit,
    onEditTransaction: (String) -> Unit,
    onRequestDeleteTransaction: (String) -> Unit,
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
                        text = "${transaction.accountName} - ${transaction.categoryName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    transaction.sourceLabel?.let { sourceLabel ->
                        Spacer(modifier = Modifier.height(6.dp))
                        TransactionPill(
                            text = if (transaction.isProviderManaged) {
                                "$sourceLabel - provider managed"
                            } else {
                                sourceLabel
                            },
                        )
                    }
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

            transaction.metadataPreview?.let { metadata ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onShowDetails(transaction.id) },
                    enabled = canInteract,
                ) {
                    Text("View details")
                }
                if (transaction.isProviderManaged) {
                    Text(
                        text = "Managed by sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    TextButton(
                        onClick = { onEditTransaction(transaction.id) },
                        enabled = canInteract,
                    ) {
                        Text(if (isEditing) "Editing" else "Edit")
                    }
                    TextButton(
                        onClick = { onRequestDeleteTransaction(transaction.id) },
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

@Composable
internal fun TransactionDetailsCard(
    transaction: TransactionCardUiModel,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = transaction.amountLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = if (transaction.isExpense) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            transaction.sourceLabel?.let { sourceLabel ->
                TransactionPill(
                    text = if (transaction.isProviderManaged) {
                        "$sourceLabel - read only"
                    } else {
                        sourceLabel
                    },
                )
            }
            HorizontalDivider()
            transaction.detailRows.forEachIndexed { index, row ->
                DetailRow(
                    label = row.label,
                    value = row.value,
                )
                if (index != transaction.detailRows.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun TransactionPill(
    text: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
