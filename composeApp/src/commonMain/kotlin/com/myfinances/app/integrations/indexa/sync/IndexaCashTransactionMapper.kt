package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.model.IndexaCashTransaction
import com.myfinances.app.integrations.indexa.model.IndexaInstrumentTransaction
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.math.roundToLong

internal const val INDEXA_PROVIDER_NAME = "Indexa Capital"
internal const val DEFAULT_INDEXA_CURRENCY_CODE = "EUR"

private const val INDEXA_CATEGORY_TRANSFER = "cat-transfer-indexa-transfer"
private const val INDEXA_CATEGORY_INVESTMENT_CONTRIBUTION = "cat-transfer-indexa-investment-contribution"
private const val INDEXA_CATEGORY_INVESTMENT_WITHDRAWAL = "cat-transfer-indexa-investment-withdrawal"
private const val INDEXA_CATEGORY_INVESTMENT_FEES = "cat-expense-indexa-investment-fees"
private const val INDEXA_CATEGORY_INVESTMENT_TAX = "cat-expense-indexa-investment-tax"
private const val INDEXA_CATEGORY_FEE_REBATE = "cat-income-indexa-fee-rebate"
private const val INDEXA_CATEGORY_INVESTMENT_PURCHASE = "cat-transfer-indexa-investment-purchase"
private const val INDEXA_CATEGORY_INVESTMENT_REDEMPTION = "cat-transfer-indexa-investment-redemption"
private const val INDEXA_CATEGORY_INVESTMENT_REBALANCE = "cat-transfer-indexa-investment-rebalance"
private const val INDEXA_CATEGORY_INVESTMENT_DISTRIBUTION = "cat-income-indexa-investment-distribution"

internal fun normalizeIndexaCurrencyCode(rawValue: String?): String? {
    val normalized = rawValue?.trim()?.uppercase().orEmpty()
    return normalized.takeIf { value ->
        value.length == 3 && value.all(Char::isLetter)
    }
}

internal fun Double?.toMinorAmount(): Long? =
    this?.times(100.0)?.roundToLong()

internal fun IndexaCashTransaction.toLedgerTransaction(
    localAccountId: String,
    fallbackCurrencyCode: String,
    syncedAtEpochMs: Long,
    categoryLookup: Map<IndexaImportedCategory, Category>,
): FinanceTransaction? {
    val minorAmount = amount.toMinorAmount() ?: return null
    if (minorAmount == 0L) return null

    val transactionType = classifyIndexaCashTransactionType()
    val importedCategory = classifyIndexaImportedCategory()
    val normalizedAmountMinor = when (transactionType) {
        TransactionType.EXPENSE -> kotlin.math.abs(minorAmount)
        TransactionType.INCOME -> kotlin.math.abs(minorAmount)
        TransactionType.TRANSFER -> minorAmount
        TransactionType.ADJUSTMENT -> minorAmount
    }

    return FinanceTransaction(
        id = buildIndexaCashTransactionId(localAccountId, reference),
        accountId = localAccountId,
        categoryId = categoryLookup[importedCategory]?.id,
        type = transactionType,
        amountMinor = normalizedAmountMinor,
        currencyCode = normalizeIndexaCurrencyCode(currencyCode) ?: fallbackCurrencyCode,
        merchantName = operationType?.takeIf(String::isNotBlank),
        note = comments?.takeIf(String::isNotBlank),
        sourceProvider = INDEXA_PROVIDER_NAME,
        externalTransactionId = reference,
        postedAtEpochMs = parseIndexaDateToEpochMs(date) ?: syncedAtEpochMs,
        createdAtEpochMs = syncedAtEpochMs,
        updatedAtEpochMs = syncedAtEpochMs,
    )
}

internal fun IndexaInstrumentTransaction.toLedgerTransaction(
    localAccountId: String,
    fallbackCurrencyCode: String,
    syncedAtEpochMs: Long,
    categoryLookup: Map<IndexaImportedCategory, Category>,
): FinanceTransaction? {
    val minorAmount = amount.toMinorAmount() ?: return null
    if (minorAmount == 0L) return null

    val transactionType = classifyIndexaInstrumentTransactionType()
    val importedCategory = classifyIndexaInstrumentImportedCategory()
    val normalizedAmountMinor = when (transactionType) {
        TransactionType.EXPENSE,
        TransactionType.INCOME -> kotlin.math.abs(minorAmount)
        TransactionType.TRANSFER,
        TransactionType.ADJUSTMENT -> minorAmount
    }

    return FinanceTransaction(
        id = buildIndexaInstrumentTransactionId(localAccountId, reference),
        accountId = localAccountId,
        categoryId = categoryLookup[importedCategory]?.id,
        type = transactionType,
        amountMinor = normalizedAmountMinor,
        currencyCode = normalizeIndexaCurrencyCode(currencyCode) ?: fallbackCurrencyCode,
        merchantName = instrumentName?.takeIf(String::isNotBlank)
            ?: operationType?.takeIf(String::isNotBlank),
        note = buildInstrumentTransactionNote(),
        sourceProvider = INDEXA_PROVIDER_NAME,
        externalTransactionId = reference,
        postedAtEpochMs = parseIndexaDateTimeToEpochMs(executedAt)
            ?: parseIndexaDateTimeToEpochMs(valueDate)
            ?: parseIndexaDateToEpochMs(date)
            ?: syncedAtEpochMs,
        createdAtEpochMs = syncedAtEpochMs,
        updatedAtEpochMs = syncedAtEpochMs,
    )
}

internal suspend fun ensureIndexaCategories(
    ledgerRepository: LedgerRepository,
    timestampMs: Long,
): Map<IndexaImportedCategory, Category> {
    val existingCategories = ledgerRepository.observeCategories().first()
    val missingCategories = buildIndexaSystemCategories(timestampMs).filter { candidate ->
        existingCategories.none { category -> category.id == candidate.id }
    }

    for (category in missingCategories) {
        ledgerRepository.upsertCategory(category)
    }

    val allCategories = existingCategories + missingCategories
    return IndexaImportedCategory.entries.associateWith { importedCategory ->
        allCategories.first { category -> category.id == importedCategory.categoryId }
    }
}

private fun IndexaCashTransaction.classifyIndexaCashTransactionType(): TransactionType {
    val descriptor = normalizedDescriptor()

    return when {
        descriptor.contains("COMISION") || descriptor.contains("COMISI") || descriptor.contains("FEE") ->
            if (amount >= 0.0) TransactionType.INCOME else TransactionType.EXPENSE
        descriptor.contains("RETROCESION") || descriptor.contains("ABONO") || descriptor.contains("DEVOLU") ->
            TransactionType.INCOME
        else -> TransactionType.TRANSFER
    }
}

private fun IndexaCashTransaction.classifyIndexaImportedCategory(): IndexaImportedCategory {
    val descriptor = normalizedDescriptor()

    return when {
        descriptor.contains("COMISION") || descriptor.contains("COMISI") || descriptor.contains("FEE") ->
            if (amount >= 0.0) IndexaImportedCategory.FEE_REBATE else IndexaImportedCategory.INVESTMENT_FEES
        descriptor.contains("RETROCESION") || descriptor.contains("ABONO") || descriptor.contains("DEVOLU") ->
            IndexaImportedCategory.FEE_REBATE
        descriptor.contains("IMPUEST") || descriptor.contains("TAX") || descriptor.contains("RETENCION") ->
            IndexaImportedCategory.INVESTMENT_TAX
        descriptor.contains("REEMBOLSO") || descriptor.contains("RETIRADA") || descriptor.contains("RETIRO") ->
            IndexaImportedCategory.INVESTMENT_WITHDRAWAL
        descriptor.contains("SUSCRIP") || descriptor.contains("APORTA") || descriptor.contains("INGRESO") ->
            IndexaImportedCategory.INVESTMENT_CONTRIBUTION
        else -> IndexaImportedCategory.TRANSFER
    }
}

private fun IndexaInstrumentTransaction.classifyIndexaInstrumentTransactionType(): TransactionType {
    val descriptor = normalizedDescriptor()

    return when {
        descriptor.contains("COMISION") || descriptor.contains("COMISI") || descriptor.contains("FEE") ->
            TransactionType.EXPENSE
        descriptor.contains("DIVID") || descriptor.contains("CUPON") || descriptor.contains("ABONO") ->
            TransactionType.INCOME
        descriptor.contains("RETROCESION") || descriptor.contains("DEVOLU") ->
            TransactionType.INCOME
        else -> TransactionType.TRANSFER
    }
}

private fun IndexaInstrumentTransaction.classifyIndexaInstrumentImportedCategory(): IndexaImportedCategory {
    val descriptor = normalizedDescriptor()

    return when {
        descriptor.contains("COMISION") || descriptor.contains("COMISI") || descriptor.contains("FEE") ->
            IndexaImportedCategory.INVESTMENT_FEES
        descriptor.contains("DIVID") || descriptor.contains("CUPON") || descriptor.contains("ABONO") ->
            IndexaImportedCategory.INVESTMENT_DISTRIBUTION
        descriptor.contains("RETROCESION") || descriptor.contains("DEVOLU") ->
            IndexaImportedCategory.FEE_REBATE
        descriptor.contains("REEMBOLSO") || descriptor.contains("VENTA") || descriptor.contains("RETIRO") ->
            IndexaImportedCategory.INVESTMENT_REDEMPTION
        descriptor.contains("TRASPASO") || descriptor.contains("REBAL") ->
            IndexaImportedCategory.INVESTMENT_REBALANCE
        else -> IndexaImportedCategory.INVESTMENT_PURCHASE
    }
}

private fun buildIndexaCashTransactionId(
    localAccountId: String,
    reference: String,
): String = "txn-indexa-cash-$localAccountId-${reference.lowercase()}"

private fun buildIndexaInstrumentTransactionId(
    localAccountId: String,
    reference: String,
): String = "txn-indexa-instrument-$localAccountId-${reference.lowercase()}"

private fun parseIndexaDateToEpochMs(value: String): Long? = runCatching {
    LocalDate.parse(value).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}.getOrNull()

private fun parseIndexaDateTimeToEpochMs(value: String?): Long? = runCatching {
    LocalDateTime.parse(value.orEmpty().replace(' ', 'T'))
        .toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}.getOrNull()

private fun IndexaCashTransaction.normalizedDescriptor(): String = buildString {
    append(operationType.orEmpty())
    append(' ')
    append(comments.orEmpty())
}.uppercase()

private fun IndexaInstrumentTransaction.normalizedDescriptor(): String = buildString {
    append(operationType.orEmpty())
    append(' ')
    append(instrumentName.orEmpty())
    append(' ')
    append(assetClass.orEmpty())
}.uppercase()

private fun IndexaInstrumentTransaction.buildInstrumentTransactionNote(): String =
    buildList {
        operationType?.takeIf(String::isNotBlank)?.let { add("Operation: $it") }
        titles?.let { add("Units: ${formatImportedQuantity(it)}") }
        price?.let { add("Price: ${formatImportedQuantity(it)} ${normalizeIndexaCurrencyCode(currencyCode) ?: DEFAULT_INDEXA_CURRENCY_CODE}") }
        valueDate?.takeIf(String::isNotBlank)?.let { add("Value date: $it") }
        status?.takeIf(String::isNotBlank)?.let { add("Status: $it") }
        reference.takeIf(String::isNotBlank)?.let { add("Reference: $it") }
    }.joinToString(" | ")

private fun formatImportedQuantity(value: Double): String =
    value.toString().trimEnd('0').trimEnd('.')

private fun buildIndexaSystemCategories(timestampMs: Long): List<Category> = listOf(
    Category(
        id = INDEXA_CATEGORY_TRANSFER,
        name = "Transfer",
        kind = CategoryKind.TRANSFER,
        colorHex = "#5D6B7A",
        iconKey = "transfer",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_INVESTMENT_CONTRIBUTION,
        name = "Investment contribution",
        kind = CategoryKind.TRANSFER,
        colorHex = "#2C6E63",
        iconKey = "investment_contribution",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_INVESTMENT_WITHDRAWAL,
        name = "Investment withdrawal",
        kind = CategoryKind.TRANSFER,
        colorHex = "#7A5D45",
        iconKey = "investment_withdrawal",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_INVESTMENT_FEES,
        name = "Investment fees",
        kind = CategoryKind.EXPENSE,
        colorHex = "#8A4F4F",
        iconKey = "investment_fees",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_INVESTMENT_TAX,
        name = "Investment tax",
        kind = CategoryKind.EXPENSE,
        colorHex = "#7D6440",
        iconKey = "investment_tax",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_FEE_REBATE,
        name = "Fee rebate",
        kind = CategoryKind.INCOME,
        colorHex = "#2E7D5A",
        iconKey = "fee_rebate",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_INVESTMENT_PURCHASE,
        name = "Investment purchase",
        kind = CategoryKind.TRANSFER,
        colorHex = "#3C6E71",
        iconKey = "investment_purchase",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_INVESTMENT_REDEMPTION,
        name = "Investment redemption",
        kind = CategoryKind.TRANSFER,
        colorHex = "#836953",
        iconKey = "investment_redemption",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_INVESTMENT_REBALANCE,
        name = "Investment rebalance",
        kind = CategoryKind.TRANSFER,
        colorHex = "#5E6C84",
        iconKey = "investment_rebalance",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
    Category(
        id = INDEXA_CATEGORY_INVESTMENT_DISTRIBUTION,
        name = "Investment distribution",
        kind = CategoryKind.INCOME,
        colorHex = "#4D8B67",
        iconKey = "investment_distribution",
        isSystem = true,
        isArchived = false,
        createdAtEpochMs = timestampMs,
    ),
)

internal enum class IndexaImportedCategory(val categoryId: String) {
    TRANSFER(INDEXA_CATEGORY_TRANSFER),
    INVESTMENT_CONTRIBUTION(INDEXA_CATEGORY_INVESTMENT_CONTRIBUTION),
    INVESTMENT_WITHDRAWAL(INDEXA_CATEGORY_INVESTMENT_WITHDRAWAL),
    INVESTMENT_FEES(INDEXA_CATEGORY_INVESTMENT_FEES),
    INVESTMENT_TAX(INDEXA_CATEGORY_INVESTMENT_TAX),
    FEE_REBATE(INDEXA_CATEGORY_FEE_REBATE),
    INVESTMENT_PURCHASE(INDEXA_CATEGORY_INVESTMENT_PURCHASE),
    INVESTMENT_REDEMPTION(INDEXA_CATEGORY_INVESTMENT_REDEMPTION),
    INVESTMENT_REBALANCE(INDEXA_CATEGORY_INVESTMENT_REBALANCE),
    INVESTMENT_DISTRIBUTION(INDEXA_CATEGORY_INVESTMENT_DISTRIBUTION),
}
