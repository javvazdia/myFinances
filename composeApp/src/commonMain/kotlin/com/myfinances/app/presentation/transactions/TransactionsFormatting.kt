package com.myfinances.app.presentation.transactions

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.integrations.statements.StatementImportResult
import com.myfinances.app.presentation.shared.formatDayLabel
import com.myfinances.app.presentation.shared.formatMinorMoney
import kotlin.random.Random
import kotlin.time.Clock

private const val IMPORTED_REFERENCE_LABEL = "Reference"

internal fun FinanceTransaction.toCardUiModel(
    accounts: List<Account>,
    categories: List<Category>,
): TransactionCardUiModel {
    val accountName = accounts.firstOrNull { account -> account.id == accountId }?.name ?: "Unknown account"
    val categoryName = categories.firstOrNull { category -> category.id == categoryId }?.name ?: "Uncategorized"
    val isExpense = type == TransactionType.EXPENSE
    val isProviderManaged = isProviderManaged()

    return TransactionCardUiModel(
        id = id,
        title = merchantName ?: type.label,
        accountName = accountName,
        categoryName = categoryName,
        amountLabel = buildSignedAmountLabel(amountMinor, currencyCode, isExpense),
        dateLabel = formatTransactionDateLabel(postedAtEpochMs),
        sourceLabel = buildTransactionSourceLabel(),
        metadataPreview = buildTransactionMetadataPreview(),
        detailRows = buildTransactionDetailRows(accountName, categoryName),
        isProviderManaged = isProviderManaged,
        isExpense = isExpense,
    )
}

internal fun FinanceTransaction.isProviderManaged(): Boolean =
    !sourceProvider.isNullOrBlank() && !externalTransactionId.isNullOrBlank()

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

internal fun formatTransactionAmountInput(amountMinor: Long): String {
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    return "$major.${minor.toString().padStart(2, '0')}"
}

internal fun formatTransactionMoney(amountMinor: Long, currencyCode: String): String =
    formatMinorMoney(amountMinor, currencyCode)

internal fun formatTransactionDateLabel(
    epochMs: Long,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): String = formatDayLabel(epochMs, nowEpochMs)

internal fun buildImportMessage(result: StatementImportResult): String =
    if (result.skippedTransactions > 0) {
        "Imported ${result.importedTransactions} transactions from ${result.sourceFileName} into ${result.accountName}. Skipped ${result.skippedTransactions} duplicates. Closing balance ${formatTransactionMoney(result.endingBalanceMinor, result.currencyCode)}."
    } else {
        "Imported ${result.importedTransactions} transactions from ${result.sourceFileName} into ${result.accountName}. Closing balance ${formatTransactionMoney(result.endingBalanceMinor, result.currencyCode)}."
    }

private fun FinanceTransaction.buildTransactionMetadataPreview(): String? =
    note?.takeIf(String::isNotBlank)
        ?: externalTransactionId?.takeIf(String::isNotBlank)?.let { reference -> "$IMPORTED_REFERENCE_LABEL $reference" }

private fun FinanceTransaction.buildTransactionSourceLabel(): String? =
    sourceProvider?.takeIf(String::isNotBlank)?.let { provider ->
        if (externalTransactionId.isNullOrBlank()) {
            "Imported from $provider"
        } else {
            "Synced from $provider"
        }
    }

private fun FinanceTransaction.buildTransactionDetailRows(
    accountName: String,
    categoryName: String,
): List<TransactionDetailRowUiModel> = buildList {
    add(TransactionDetailRowUiModel(label = "Account", value = accountName))
    add(TransactionDetailRowUiModel(label = "Category", value = categoryName))
    add(TransactionDetailRowUiModel(label = "Type", value = type.label))
    add(TransactionDetailRowUiModel(label = "Date", value = formatTransactionDateLabel(postedAtEpochMs)))
    buildTransactionSourceLabel()?.let { sourceLabel ->
        add(TransactionDetailRowUiModel(label = "Source", value = sourceLabel))
    }
    externalTransactionId?.takeIf(String::isNotBlank)?.let { reference ->
        add(TransactionDetailRowUiModel(label = "Reference", value = reference))
    }
    merchantName?.takeIf(String::isNotBlank)?.let { merchant ->
        add(TransactionDetailRowUiModel(label = "Merchant", value = merchant))
    }
    note?.takeIf(String::isNotBlank)?.let { transactionNote ->
        add(TransactionDetailRowUiModel(label = "Notes", value = transactionNote))
    }
}

private fun buildSignedAmountLabel(
    amountMinor: Long,
    currencyCode: String,
    isExpense: Boolean,
): String {
    val sign = when {
        isExpense -> "-"
        amountMinor < 0L -> "-"
        else -> "+"
    }
    return sign + formatTransactionMoney(kotlin.math.abs(amountMinor), currencyCode)
}
