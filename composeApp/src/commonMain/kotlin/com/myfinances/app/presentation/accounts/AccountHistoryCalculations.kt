package com.myfinances.app.presentation.accounts

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountValuationSnapshot
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.integrations.indexa.model.IndexaPerformanceHistory
import com.myfinances.app.presentation.shared.formatMinorMoney
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun buildLocalAccountHistoryChart(
    account: Account,
    transactions: List<FinanceTransaction>,
): AccountHistoryChart {
    val sortedTransactions = transactions.sortedBy(FinanceTransaction::postedAtEpochMs)
    val firstTransactionDate = sortedTransactions.firstOrNull()?.postedAtEpochMs
    val startingPointEpochMs = firstTransactionDate ?: account.createdAtEpochMs
    var runningBalanceMinor = account.openingBalanceMinor
    val points = buildList {
        add(
            AccountHistoryPoint(
                axisLabel = formatHistoryDate(startingPointEpochMs),
                detailLabel = formatHistoryDetailDate(startingPointEpochMs),
                timestampEpochMs = startingPointEpochMs,
                value = runningBalanceMinor.toDouble() / 100.0,
            ),
        )
        sortedTransactions.forEach { transaction ->
            runningBalanceMinor += transaction.balanceDeltaMinor()
            add(
                AccountHistoryPoint(
                    axisLabel = formatHistoryDate(transaction.postedAtEpochMs),
                    detailLabel = formatHistoryDetailDate(transaction.postedAtEpochMs),
                    timestampEpochMs = transaction.postedAtEpochMs,
                    value = runningBalanceMinor.toDouble() / 100.0,
                ),
            )
        }
    }

    return buildAccountHistoryChart(
        title = "Balance history",
        subtitle = "Running balance from the opening balance and tracked transactions.",
        points = points,
        valueFormatter = { value -> formatMinorMoney((value * 100).toLong(), account.currencyCode) },
        valueFormat = AccountHistoryValueFormat.MONEY,
        currencyCode = account.currencyCode,
    )
}

internal fun buildIndexaHistoryCharts(
    history: IndexaPerformanceHistory,
    currencyCode: String,
    currentBalanceMinor: Long,
): Map<AccountHistoryMode, AccountHistoryChart> {
    val charts = linkedMapOf<AccountHistoryMode, AccountHistoryChart>()

    val resolvedValueHistory = history.valueHistory
        .takeIf { valueHistory -> valueHistory.isNotEmpty() }
        ?: reconstructIndexaValueHistory(
            normalizedHistory = history.normalizedHistory,
            currentBalanceMinor = currentBalanceMinor,
        )

    val valuePoints = resolvedValueHistory
        .toSortedMap()
        .map { (date, value) ->
            AccountHistoryPoint(
                axisLabel = formatIsoDate(date),
                detailLabel = formatIsoDetailDate(date),
                timestampEpochMs = parseIsoDateEpochMs(date),
                value = value,
            )
        }

    if (valuePoints.isNotEmpty()) {
        charts[AccountHistoryMode.VALUE] = buildAccountHistoryChart(
            title = "Indexa account value",
            subtitle = if (history.valueHistory.isNotEmpty()) {
                "Historical account value from the Indexa performance endpoint."
            } else {
                "Estimated account value reconstructed from Indexa evolution data and the current synced portfolio value."
            },
            points = valuePoints,
            valueFormatter = { value -> formatMinorMoney((value * 100).toLong(), currencyCode) },
            valueFormat = AccountHistoryValueFormat.MONEY,
            currencyCode = currencyCode,
        )
    }

    val performancePoints = history.normalizedHistory
        .toSortedMap()
        .map { (date, value) ->
            AccountHistoryPoint(
                axisLabel = formatIsoDate(date),
                detailLabel = formatIsoDetailDate(date),
                timestampEpochMs = parseIsoDateEpochMs(date),
                value = value * 100.0,
            )
        }

    if (performancePoints.isNotEmpty()) {
        charts[AccountHistoryMode.PERFORMANCE] = buildAccountHistoryChart(
            title = "Indexa performance history",
            subtitle = "Normalized evolution from Indexa. A value of 100 means the starting point of the series.",
            points = performancePoints,
            valueFormatter = { value -> "${formatHistoryDecimal(value)}%" },
            valueFormat = AccountHistoryValueFormat.PERFORMANCE_PERCENT,
        )
    }

    return charts
}

internal fun buildSnapshotHistoryChart(
    account: Account,
    snapshots: List<AccountValuationSnapshot>,
): AccountHistoryChart? {
    val points = snapshots
        .sortedBy(AccountValuationSnapshot::capturedAtEpochMs)
        .map { snapshot ->
            AccountHistoryPoint(
                axisLabel = snapshot.valuationDate?.let(::formatIsoDate)
                    ?: formatHistoryDate(snapshot.capturedAtEpochMs),
                detailLabel = snapshot.valuationDate?.let(::formatIsoDetailDate)
                    ?: formatHistoryDetailDate(snapshot.capturedAtEpochMs),
                timestampEpochMs = snapshot.valuationDate?.let(::parseIsoDateEpochMs)
                    ?: snapshot.capturedAtEpochMs,
                value = snapshot.valueMinor.toDouble() / 100.0,
            )
        }

    if (points.isEmpty()) return null

    return buildAccountHistoryChart(
        title = "Snapshot history",
        subtitle = "Locally stored balance snapshots captured during syncs. Useful even when a provider does not expose full historical data.",
        points = points,
        valueFormatter = { value -> formatMinorMoney((value * 100).toLong(), account.currencyCode) },
        valueFormat = AccountHistoryValueFormat.MONEY,
        currencyCode = account.currencyCode,
    )
}

private fun reconstructIndexaValueHistory(
    normalizedHistory: Map<String, Double>,
    currentBalanceMinor: Long,
): Map<String, Double> {
    if (normalizedHistory.isEmpty()) return emptyMap()

    val latestEntry = normalizedHistory.toSortedMap().lastEntry()
    val latestNormalizedValue = latestEntry.value
    if (latestNormalizedValue <= 0.0) return emptyMap()

    val currentBalance = currentBalanceMinor.toDouble() / 100.0
    if (currentBalance <= 0.0) return emptyMap()

    val baseValue = currentBalance / latestNormalizedValue
    return normalizedHistory.mapValues { (_, normalizedValue) ->
        baseValue * normalizedValue
    }
}

private fun buildAccountHistoryChart(
    title: String,
    subtitle: String,
    points: List<AccountHistoryPoint>,
    valueFormatter: (Double) -> String,
    valueFormat: AccountHistoryValueFormat,
    currencyCode: String? = null,
): AccountHistoryChart {
    val values = points.map(AccountHistoryPoint::value)
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0

    return AccountHistoryChart(
        title = title,
        subtitle = subtitle,
        points = points,
        valueFormat = valueFormat,
        currencyCode = currencyCode,
        minimumLabel = valueFormatter(minimum),
        maximumLabel = valueFormatter(maximum),
        startLabel = points.first().axisLabel,
        endLabel = points.last().axisLabel,
    )
}

private fun FinanceTransaction.balanceDeltaMinor(): Long = when (type) {
    TransactionType.INCOME -> amountMinor
    TransactionType.EXPENSE -> -amountMinor
    TransactionType.TRANSFER -> 0L
    TransactionType.ADJUSTMENT -> amountMinor
}

private fun formatHistoryDate(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return formatLocalDate(date)
}

private fun formatHistoryDetailDate(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return formatLocalDetailDate(date)
}

private fun formatIsoDate(isoDate: String): String = runCatching {
    formatLocalDate(parseFlexibleLocalDate(isoDate))
}.getOrDefault(isoDate)

private fun formatIsoDetailDate(isoDate: String): String = runCatching {
    formatLocalDetailDate(parseFlexibleLocalDate(isoDate))
}.getOrDefault(isoDate)

private fun formatLocalDate(date: LocalDate): String {
    val month = date.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)
    return "$month ${date.day}"
}

private fun formatLocalDetailDate(date: LocalDate): String {
    val month = date.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)
    return "$month ${date.day}, ${date.year}"
}

private fun parseIsoDateEpochMs(isoDate: String): Long = runCatching {
    parseFlexibleLocalDate(isoDate)
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}.getOrDefault(0L)

private fun parseFlexibleLocalDate(rawValue: String): LocalDate =
    runCatching { LocalDate.parse(rawValue) }
        .getOrElse {
            val normalizedValue = rawValue.substringBefore('T')
            LocalDate.parse(normalizedValue)
        }

private fun formatHistoryDecimal(value: Double): String =
    value
        .toString()
        .trimEnd('0')
        .trimEnd('.')

internal fun AccountHistoryChart.filteredBy(
    range: AccountHistoryRange,
    customStartEpochMs: Long? = null,
    customEndEpochMs: Long? = null,
): AccountHistoryChart {
    if (range == AccountHistoryRange.ALL || points.isEmpty()) return this

    val filteredPoints = filterHistoryPointsByRange(
        points = points,
        range = range,
        customStartEpochMs = customStartEpochMs,
        customEndEpochMs = customEndEpochMs,
    )
    if (filteredPoints.isEmpty()) return this

    return buildAccountHistoryChart(
        title = title,
        subtitle = subtitle,
        points = filteredPoints,
        valueFormatter = { value -> formatHistoryValue(value, valueFormat, currencyCode) },
        valueFormat = valueFormat,
        currencyCode = currencyCode,
    )
}

internal fun filterHistoryPointsByRange(
    points: List<AccountHistoryPoint>,
    range: AccountHistoryRange,
    customStartEpochMs: Long? = null,
    customEndEpochMs: Long? = null,
): List<AccountHistoryPoint> {
    if (points.isEmpty() || range == AccountHistoryRange.ALL) return points
    if (range == AccountHistoryRange.CUSTOM) {
        val startEpoch = customStartEpochMs ?: return points
        val endEpoch = customEndEpochMs ?: return points
        val inRangePoints = points.filter { point ->
            point.timestampEpochMs in startEpoch..endEpoch
        }
        return inRangePoints.ifEmpty { points.takeLast(1) }
    }

    val latestDate = Instant.fromEpochMilliseconds(points.maxOf(AccountHistoryPoint::timestampEpochMs))
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val cutoffDate = latestDate.minus(range.toDatePeriod())
    val cutoffEpochMs = cutoffDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    val inRangePoints = points.filter { point -> point.timestampEpochMs >= cutoffEpochMs }

    return inRangePoints.ifEmpty { points.takeLast(1) }
}

private fun AccountHistoryRange.toDatePeriod(): DatePeriod = when (this) {
    AccountHistoryRange.ONE_MONTH -> DatePeriod(months = 1)
    AccountHistoryRange.THREE_MONTHS -> DatePeriod(months = 3)
    AccountHistoryRange.SIX_MONTHS -> DatePeriod(months = 6)
    AccountHistoryRange.ONE_YEAR -> DatePeriod(years = 1)
    AccountHistoryRange.THREE_YEARS -> DatePeriod(years = 3)
    AccountHistoryRange.CUSTOM -> DatePeriod()
    AccountHistoryRange.ALL -> DatePeriod()
}

internal fun formatHistoryValue(
    value: Double,
    valueFormat: AccountHistoryValueFormat,
    currencyCode: String?,
): String = when (valueFormat) {
    AccountHistoryValueFormat.MONEY -> formatMinorMoney((value * 100).toLong(), currencyCode ?: "EUR")
    AccountHistoryValueFormat.PERFORMANCE_PERCENT -> "${formatHistoryDecimal(value)}%"
}
