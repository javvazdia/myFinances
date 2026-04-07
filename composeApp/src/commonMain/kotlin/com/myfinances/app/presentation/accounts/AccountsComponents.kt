package com.myfinances.app.presentation.accounts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.presentation.shared.MyFinancesDateRangePickerDialog
import com.myfinances.app.presentation.shared.formatDateRangeLabel

@Composable
internal fun AccountActionsCard(
    uiState: AccountsUiState,
    onShowCreateForm: () -> Unit,
    onHideAccountForm: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uiState.isFormVisible) {
                Button(
                    onClick = onShowCreateForm,
                    enabled = !uiState.isBusy,
                ) {
                    Text("Create account")
                }
            } else {
                Button(
                    onClick = onHideAccountForm,
                    enabled = !uiState.isBusy,
                ) {
                    Text(if (uiState.isEditing) "Cancel editing" else "Hide form")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountFormCard(
    uiState: AccountsUiState,
    onNameChange: (String) -> Unit,
    onTypeSelected: (AccountType) -> Unit,
    onCurrencyCodeChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onSaveAccount: () -> Unit,
    onHideAccountForm: () -> Unit,
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.editingAccountId, uiState.isFormVisible) {
        if (uiState.isFormVisible) {
            nameFocusRequester.requestFocus()
        }
    }

    Card(
        colors = if (uiState.isEditing) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (uiState.isEditing) "Edit account" else "Create account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = when {
                    uiState.isEditing && uiState.editingAccount?.sourceType == AccountSourceType.API_SYNC ->
                        "You can update the local presentation of this synced account, but a future provider sync may overwrite some fields like name or valuation."
                    uiState.isEditing ->
                        "You are updating this account in place, so existing transactions and holdings keep pointing to the same local account id."
                    else ->
                        "This form creates a local account. For synced providers, imported accounts will also land in this same ledger."
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
                label = { Text("Account name") },
                singleLine = true,
                enabled = !uiState.isBusy,
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
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    enabled = !uiState.isBusy,
                    label = { Text("Account type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    AccountType.entries.forEach { type ->
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

            OutlinedTextField(
                value = uiState.draftCurrencyCode,
                onValueChange = onCurrencyCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Currency code") },
                supportingText = { Text("Use a 3-letter code like EUR or USD.") },
                singleLine = true,
                enabled = !uiState.isBusy,
            )

            OutlinedTextField(
                value = uiState.draftOpeningBalance,
                onValueChange = onOpeningBalanceChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (uiState.editingAccount?.sourceType == AccountSourceType.API_SYNC) "Current synced value" else "Opening balance")
                },
                supportingText = {
                    Text("Examples: 1200, 1200.50, or -250.00")
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                singleLine = true,
                enabled = !uiState.isBusy,
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSaveAccount,
                    enabled = !uiState.isBusy,
                ) {
                    Text(
                        when {
                            uiState.isSaving -> "Saving..."
                            uiState.isEditing -> "Save changes"
                            else -> "Create account"
                        },
                    )
                }

                TextButton(
                    onClick = onHideAccountForm,
                    enabled = !uiState.isBusy,
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
internal fun AccountCard(
    account: Account,
    currentBalanceMinor: Long,
    onSelectAccount: (String) -> Unit,
    onEditAccount: (String) -> Unit,
    onRequestDeleteAccount: (String) -> Unit,
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
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = account.type.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatMoney(currentBalanceMinor, account.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (account.sourceType) {
                    AccountSourceType.MANUAL -> "Source: Manual"
                    AccountSourceType.API_SYNC ->
                        "Source: Synced from ${account.sourceProvider ?: "external provider"}"
                    AccountSourceType.FILE_IMPORT ->
                        "Source: Imported from ${account.sourceProvider ?: "file"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSelectAccount(account.id) },
                    enabled = canInteract,
                ) {
                    Text("View details")
                }
                TextButton(
                    onClick = { onEditAccount(account.id) },
                    enabled = canInteract,
                ) {
                    Text(if (isEditing) "Editing" else "Edit")
                }
                TextButton(
                    onClick = { onRequestDeleteAccount(account.id) },
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

@Composable
internal fun AccountDetailScreen(
    account: Account,
    currentBalanceMinor: Long,
    historyChart: AccountHistoryChart?,
    availableHistoryModes: List<AccountHistoryMode>,
    selectedHistoryMode: AccountHistoryMode,
    selectedHistoryRange: AccountHistoryRange,
    customHistoryStartEpochMs: Long?,
    customHistoryEndEpochMs: Long?,
    isLoadingHistory: Boolean,
    historyErrorMessage: String?,
    positions: List<InvestmentPosition>,
    onSelectHistoryMode: (AccountHistoryMode) -> Unit,
    onSelectHistoryRange: (AccountHistoryRange) -> Unit,
    onApplyCustomHistoryRange: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onEditAccount: (String) -> Unit,
    onRequestDeleteAccount: (String) -> Unit,
    canInteract: Boolean,
    isDeleting: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("Back to accounts")
            }
        }

        item {
            Text(
                text = "Account details",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            AccountSummaryCard(
                account = account,
                currentBalanceMinor = currentBalanceMinor,
            )
        }

        item {
            AccountHistoryCard(
                chart = historyChart,
                availableModes = availableHistoryModes,
                selectedMode = selectedHistoryMode,
                selectedRange = selectedHistoryRange,
                customStartEpochMs = customHistoryStartEpochMs,
                customEndEpochMs = customHistoryEndEpochMs,
                isLoading = isLoadingHistory,
                errorMessage = historyErrorMessage,
                onSelectMode = onSelectHistoryMode,
                onSelectRange = onSelectHistoryRange,
                onApplyCustomRange = onApplyCustomHistoryRange,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = { onEditAccount(account.id) },
                    enabled = canInteract,
                ) {
                    Text("Edit account")
                }
                TextButton(
                    onClick = { onRequestDeleteAccount(account.id) },
                    enabled = canInteract,
                ) {
                    Text(
                        text = if (isDeleting) "Deleting..." else "Delete account",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            Text(
                text = if (account.type == AccountType.INVESTMENT) "Portfolio holdings" else "Holdings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            account.type != AccountType.INVESTMENT -> {
                item {
                    EmptyDetailCard(
                        message = "This account is not an investment account, so there are no portfolio holdings to show here.",
                    )
                }
            }
            account.sourceType != AccountSourceType.API_SYNC -> {
                item {
                    EmptyDetailCard(
                        message = "This investment account is local-only for now. Synced providers like Indexa will surface holdings here after import.",
                    )
                }
            }
            positions.isEmpty() -> {
                item {
                    EmptyDetailCard(
                        message = "No holdings have been imported for this account yet. Run sync again to refresh the latest portfolio positions.",
                    )
                }
            }
            else -> {
                items(
                    items = positions,
                    key = { position -> position.id },
                ) { position ->
                    InvestmentPositionCard(
                        position = position,
                        currencyCode = account.currencyCode,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(
    account: Account,
    currentBalanceMinor: Long,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = account.type.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMoney(currentBalanceMinor, account.currencyCode),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (account.sourceType == AccountSourceType.API_SYNC && account.type == AccountType.INVESTMENT) {
                    "Current synced portfolio value"
                } else {
                    "Current balance from opening balance plus tracked transactions"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when (account.sourceType) {
                    AccountSourceType.MANUAL -> "Source: Manual"
                    AccountSourceType.API_SYNC -> "Source: Synced from ${account.sourceProvider ?: "external provider"}"
                    AccountSourceType.FILE_IMPORT -> "Source: Imported from ${account.sourceProvider ?: "file"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last sync: ${formatSyncTimestamp(account.lastSyncedAtEpochMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (account.externalAccountId != null) {
                Text(
                    text = "Provider account: ${account.externalAccountId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountHistoryCard(
    chart: AccountHistoryChart?,
    availableModes: List<AccountHistoryMode>,
    selectedMode: AccountHistoryMode,
    selectedRange: AccountHistoryRange,
    customStartEpochMs: Long?,
    customEndEpochMs: Long?,
    isLoading: Boolean,
    errorMessage: String?,
    onSelectMode: (AccountHistoryMode) -> Unit,
    onSelectRange: (AccountHistoryRange) -> Unit,
    onApplyCustomRange: (Long, Long) -> Unit,
) {
    var isCustomRangePickerVisible by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = chart?.title ?: "Account history",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (availableModes.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableModes.forEach { mode ->
                        val isSelected = mode == selectedMode
                        if (isSelected) {
                            Button(onClick = { onSelectMode(mode) }) {
                                Text(mode.label)
                            }
                        } else {
                            OutlinedButton(onClick = { onSelectMode(mode) }) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountHistoryRange.entries.forEach { range ->
                    val isSelected = range == selectedRange
                    val label = if (range == AccountHistoryRange.CUSTOM) {
                        formatDateRangeLabel(customStartEpochMs, customEndEpochMs)
                    } else {
                        range.label
                    }
                    if (isSelected) {
                        Button(onClick = {
                            if (range == AccountHistoryRange.CUSTOM) {
                                isCustomRangePickerVisible = true
                            } else {
                                onSelectRange(range)
                            }
                        }) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = {
                            if (range == AccountHistoryRange.CUSTOM) {
                                isCustomRangePickerVisible = true
                            } else {
                                onSelectRange(range)
                            }
                        }) {
                            Text(label)
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                isLoading && (chart == null || chart.points.isEmpty()) -> {
                    Text(
                        text = "Loading history...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                chart == null || chart.points.isEmpty() -> {
                    Text(
                        text = "No history is available for this account yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        text = chart.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    InteractiveHistoryChart(chart = chart)
                }
            }
        }
    }

    if (isCustomRangePickerVisible) {
        MyFinancesDateRangePickerDialog(
            initialStartEpochMs = customStartEpochMs,
            initialEndEpochMs = customEndEpochMs,
            onDismiss = { isCustomRangePickerVisible = false },
            onConfirm = { startEpochMs, endEpochMs ->
                isCustomRangePickerVisible = false
                onApplyCustomRange(startEpochMs, endEpochMs)
            },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InteractiveHistoryChart(chart: AccountHistoryChart) {
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.tertiary
    var chartSize by remember(chart.points) { mutableStateOf(IntSize.Zero) }
    var selectedIndex by remember(chart.points) {
        mutableStateOf(chart.points.lastIndex.takeIf { it >= 0 } ?: 0)
    }
    val currentPoints by rememberUpdatedState(chart.points)
    val selectedPoint = currentPoints.getOrNull(selectedIndex)

    selectedPoint?.let { point ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = point.detailLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatHistoryValue(
                    value = point.value,
                    valueFormat = chart.valueFormat,
                    currencyCode = chart.currencyCode,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = chart.minimumLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = chart.maximumLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .onPointerEvent(PointerEventType.Enter) { event ->
                updateSelectedPointIndex(event, chartSize, currentPoints.size) { index -> selectedIndex = index }
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                updateSelectedPointIndex(event, chartSize, currentPoints.size) { index -> selectedIndex = index }
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                updateSelectedPointIndex(event, chartSize, currentPoints.size) { index -> selectedIndex = index }
            }
            .onPointerEvent(PointerEventType.Release) { event ->
                updateSelectedPointIndex(event, chartSize, currentPoints.size) { index -> selectedIndex = index }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .onSizeChanged { size -> chartSize = size },
        ) {
            val points = chart.points
            if (points.isEmpty()) return@Canvas

            val values = points.map(AccountHistoryPoint::value)
            val minValue = values.minOrNull() ?: 0.0
            val maxValue = values.maxOrNull() ?: 0.0
            val valueRange = (maxValue - minValue).takeIf { range -> range > 0.0 } ?: 1.0
            val horizontalStep = if (points.size > 1) size.width / (points.size - 1) else 0f
            val path = Path()
            val coordinates = points.mapIndexed { index, point ->
                val x = if (points.size == 1) size.width / 2f else horizontalStep * index
                val normalized = ((point.value - minValue) / valueRange).toFloat()
                val y = size.height - (normalized * size.height)
                Offset(x, y)
            }

            coordinates.forEachIndexed { index, offset ->
                if (index == 0) {
                    path.moveTo(offset.x, offset.y)
                } else {
                    path.lineTo(offset.x, offset.y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 6f),
            )

            coordinates.getOrNull(selectedIndex)?.let { selectedOffset ->
                drawLine(
                    color = markerColor.copy(alpha = 0.4f),
                    start = Offset(selectedOffset.x, 0f),
                    end = Offset(selectedOffset.x, size.height),
                    strokeWidth = 3f,
                )
                drawCircle(
                    color = markerColor,
                    radius = 8f,
                    center = selectedOffset,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = chart.startLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = chart.endLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InvestmentPositionCard(
    position: InvestmentPosition,
    currencyCode: String,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = position.instrumentName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildPositionSubtitle(position),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildPositionValuationLabel(position, currencyCode),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (position.valuationDate != null) {
                Text(
                    text = "Valuation date: ${position.valuationDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyDetailCard(message: String) {
    Card {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val AccountHistoryMode.label: String
    get() = when (this) {
        AccountHistoryMode.VALUE -> "Value"
        AccountHistoryMode.PERFORMANCE -> "Performance"
        AccountHistoryMode.SNAPSHOTS -> "Snapshots"
    }

private val AccountHistoryRange.label: String
    get() = when (this) {
        AccountHistoryRange.ONE_MONTH -> "1 month"
        AccountHistoryRange.THREE_MONTHS -> "3 months"
        AccountHistoryRange.SIX_MONTHS -> "6 months"
        AccountHistoryRange.ONE_YEAR -> "1 year"
        AccountHistoryRange.THREE_YEARS -> "3 years"
        AccountHistoryRange.CUSTOM -> "Custom"
        AccountHistoryRange.ALL -> "All"
    }

private fun nearestPointIndex(
    x: Float,
    width: Float,
    pointCount: Int,
): Int {
    if (pointCount <= 1 || width <= 0f) return 0
    val step = width / (pointCount - 1)
    return kotlin.math.round(x / step).toInt().coerceIn(0, pointCount - 1)
}

private fun updateSelectedPointIndex(
    event: PointerEvent,
    chartSize: IntSize,
    pointCount: Int,
    onSelectedIndex: (Int) -> Unit,
) {
    val position = event.changes.firstOrNull()?.position ?: return
    if (chartSize.width == 0 || pointCount == 0) return
    onSelectedIndex(
        nearestPointIndex(
            x = position.x,
            width = chartSize.width.toFloat(),
            pointCount = pointCount,
        ),
    )
}
