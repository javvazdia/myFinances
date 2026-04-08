package com.myfinances.app.data

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountValuationSnapshot
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.OverviewHistory
import com.myfinances.app.domain.model.OverviewHistoryLine
import com.myfinances.app.domain.model.OverviewHistoryPoint
import com.myfinances.app.domain.model.OverviewPeriodFilter
import com.myfinances.app.domain.model.OverviewSnapshot
import com.myfinances.app.domain.model.RecentTransaction
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.model.calculateAccountCurrentBalances
import com.myfinances.app.domain.repository.FinanceRepository
import com.myfinances.app.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultFinanceRepository(
    private val ledgerRepository: LedgerRepository,
) : FinanceRepository {
    override fun observeOverview(
        period: OverviewPeriodFilter,
        customStartEpochMs: Long?,
        customEndEpochMs: Long?,
    ): Flow<OverviewSnapshot> = combine(
        ledgerRepository.observeAccounts(),
        ledgerRepository.observeCategories(),
        ledgerRepository.observeAllTransactions(),
        ledgerRepository.observeAllAccountValuationSnapshots(),
        ledgerRepository.observeRecentTransactions(limit = 5),
    ) { accounts, categories, allTransactions, allSnapshots, recentTransactions ->
        val categoryNames = categories.associateBy(Category::id)
        val totalBalanceMinor = calculateAccountCurrentBalances(
            accounts = accounts,
            transactions = allTransactions,
        ).values.sum()
        val nowEpochMs = Clock.System.now().toEpochMilliseconds()
        val cashFlow = calculateOverviewCashFlow(
            transactions = allTransactions,
            period = period,
            customStartEpochMs = customStartEpochMs,
            customEndEpochMs = customEndEpochMs,
            nowEpochMs = nowEpochMs,
        )
        val history = buildOverviewHistory(
            accounts = accounts,
            allTransactions = allTransactions,
            allSnapshots = allSnapshots,
            period = period,
            customStartEpochMs = customStartEpochMs,
            customEndEpochMs = customEndEpochMs,
            nowEpochMs = nowEpochMs,
        )

        OverviewSnapshot(
            totalBalance = formatMoney(totalBalanceMinor, "EUR"),
            income = formatMoney(cashFlow.incomeMinor, "EUR"),
            expenses = formatMoney(cashFlow.expenseMinor, "EUR"),
            savingsRate = formatSavingsRate(cashFlow.incomeMinor, cashFlow.expenseMinor),
            focusMessage = buildFocusMessage(accounts, allTransactions),
            history = history,
            recentTransactions = recentTransactions.map { transaction ->
                val isExpense = transaction.type == TransactionType.EXPENSE
                RecentTransaction(
                    title = transaction.merchantName ?: transaction.note ?: fallbackTitle(transaction.type),
                    category = transaction.categoryId
                        ?.let(categoryNames::get)
                        ?.name ?: "Uncategorized",
                    amountLabel = formatSignedMoney(
                        amountMinor = transaction.amountMinor,
                        currencyCode = transaction.currencyCode,
                        isExpense = isExpense,
                    ),
                    dateLabel = formatDateLabel(transaction.postedAtEpochMs),
                    isExpense = isExpense,
                )
            },
        )
    }

    private fun buildFocusMessage(
        accounts: List<Account>,
        allTransactions: List<FinanceTransaction>,
    ): String {
        val linkedAccounts = accounts.count { it.sourceType == AccountSourceType.API_SYNC }
        val uncategorizedCount = allTransactions.count { it.categoryId == null }

        return when {
            linkedAccounts > 0 && uncategorizedCount > 0 ->
                "Imported accounts are in place. Next good milestone: map uncategorized synced transactions."
            linkedAccounts > 0 ->
                "Your local ledger is ready to absorb synced account activity from external providers."
            uncategorizedCount > 0 ->
                "Starter data is seeded. Next good milestone: categorize transactions before adding bank sync."
            else ->
                "Your local-first finance core is ready. Next step: add account integrations and reconciliation flows."
        }
    }
}

internal fun buildOverviewHistory(
    accounts: List<Account>,
    allTransactions: List<FinanceTransaction>,
    allSnapshots: List<AccountValuationSnapshot>,
    period: OverviewPeriodFilter,
    customStartEpochMs: Long? = null,
    customEndEpochMs: Long? = null,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): OverviewHistory? {
    val fullSeries = accounts.mapNotNull { account ->
        buildOverviewLineForAccount(
            account = account,
            transactions = allTransactions.filter { transaction -> transaction.accountId == account.id },
            snapshots = allSnapshots.filter { snapshot -> snapshot.accountId == account.id },
        )
    }

    if (fullSeries.isEmpty()) return null

    val filteredSeries = fullSeries.mapNotNull { line ->
        val filteredPoints = filterOverviewHistoryPoints(
            points = line.points,
            period = period,
            customStartEpochMs = customStartEpochMs,
            customEndEpochMs = customEndEpochMs,
            nowEpochMs = nowEpochMs,
        )
        if (filteredPoints.isEmpty()) null else line.copy(points = filteredPoints)
    }

    if (filteredSeries.isEmpty()) return null

    val totalLine = buildTotalOverviewLine(filteredSeries) ?: return null
    val lines = listOf(totalLine) + filteredSeries
    val allValues = lines.flatMap { line -> line.points.map(OverviewHistoryPoint::valueMinor) }

    return OverviewHistory(
        currencyCode = "EUR",
        lines = lines,
        minimumLabel = formatMoney(allValues.minOrNull() ?: 0L, "EUR"),
        maximumLabel = formatMoney(allValues.maxOrNull() ?: 0L, "EUR"),
        startLabel = lines.first().points.first().axisLabel,
        endLabel = lines.first().points.last().axisLabel,
    )
}

internal data class OverviewCashFlow(
    val incomeMinor: Long,
    val expenseMinor: Long,
)

private fun buildOverviewLineForAccount(
    account: Account,
    transactions: List<FinanceTransaction>,
    snapshots: List<AccountValuationSnapshot>,
): OverviewHistoryLine? {
    val points = if (
        account.sourceType == AccountSourceType.API_SYNC &&
        account.type == AccountType.INVESTMENT &&
        snapshots.isNotEmpty()
    ) {
        snapshots
            .sortedBy(AccountValuationSnapshot::capturedAtEpochMs)
            .map { snapshot ->
                val timestamp = snapshot.valuationDate
                    ?.let(::parseOverviewIsoDateEpochMs)
                    ?.takeIf { it > 0L }
                    ?: snapshot.capturedAtEpochMs
                OverviewHistoryPoint(
                    timestampEpochMs = timestamp,
                    valueMinor = snapshot.valueMinor,
                    axisLabel = formatOverviewAxisLabel(timestamp),
                    detailLabel = formatOverviewDetailLabel(timestamp),
                )
            }
    } else {
        buildLocalOverviewPoints(
            account = account,
            transactions = transactions,
        )
    }

    if (points.isEmpty()) return null

    return OverviewHistoryLine(
        id = account.id,
        label = account.name,
        points = points,
        isTotal = false,
    )
}

private fun buildLocalOverviewPoints(
    account: Account,
    transactions: List<FinanceTransaction>,
): List<OverviewHistoryPoint> {
    val sortedTransactions = transactions.sortedBy(FinanceTransaction::postedAtEpochMs)
    val startingPointEpochMs = sortedTransactions.firstOrNull()?.postedAtEpochMs ?: account.createdAtEpochMs
    var runningBalanceMinor = account.openingBalanceMinor

    return buildList {
        add(
            OverviewHistoryPoint(
                timestampEpochMs = startingPointEpochMs,
                valueMinor = runningBalanceMinor,
                axisLabel = formatOverviewAxisLabel(startingPointEpochMs),
                detailLabel = formatOverviewDetailLabel(startingPointEpochMs),
            ),
        )
        sortedTransactions.forEach { transaction ->
            runningBalanceMinor += transaction.overviewBalanceDeltaMinor()
            add(
                OverviewHistoryPoint(
                    timestampEpochMs = transaction.postedAtEpochMs,
                    valueMinor = runningBalanceMinor,
                    axisLabel = formatOverviewAxisLabel(transaction.postedAtEpochMs),
                    detailLabel = formatOverviewDetailLabel(transaction.postedAtEpochMs),
                ),
            )
        }
    }
}

private fun FinanceTransaction.overviewBalanceDeltaMinor(): Long = when (type) {
    TransactionType.INCOME -> amountMinor
    TransactionType.EXPENSE -> -amountMinor
    TransactionType.TRANSFER -> 0L
    TransactionType.ADJUSTMENT -> amountMinor
}

private fun filterOverviewHistoryPoints(
    points: List<OverviewHistoryPoint>,
    period: OverviewPeriodFilter,
    customStartEpochMs: Long?,
    customEndEpochMs: Long?,
    nowEpochMs: Long,
): List<OverviewHistoryPoint> {
    if (points.isEmpty()) return emptyList()
    if (period == OverviewPeriodFilter.ALL) return points

    val (startEpochMs, endEpochMs) = resolveOverviewRange(
        period = period,
        customStartEpochMs = customStartEpochMs,
        customEndEpochMs = customEndEpochMs,
        nowEpochMs = nowEpochMs,
    ) ?: return points

    val carryForward = points
        .lastOrNull { point -> point.timestampEpochMs < startEpochMs }
        ?.copy(
            timestampEpochMs = startEpochMs,
            axisLabel = formatOverviewAxisLabel(startEpochMs),
            detailLabel = formatOverviewDetailLabel(startEpochMs),
        )

    val inRange = points.filter { point ->
        point.timestampEpochMs in startEpochMs..endEpochMs
    }

    return buildList {
        carryForward?.let(::add)
        addAll(inRange)
        if (isEmpty()) {
            points.lastOrNull()?.let { lastPoint ->
                add(
                    lastPoint.copy(
                        timestampEpochMs = endEpochMs,
                        axisLabel = formatOverviewAxisLabel(endEpochMs),
                        detailLabel = formatOverviewDetailLabel(endEpochMs),
                    ),
                )
            }
        }
    }.distinctBy { point -> point.timestampEpochMs to point.valueMinor }
}

private fun resolveOverviewRange(
    period: OverviewPeriodFilter,
    customStartEpochMs: Long?,
    customEndEpochMs: Long?,
    nowEpochMs: Long,
): Pair<Long, Long>? {
    if (period == OverviewPeriodFilter.ALL) return null
    if (period == OverviewPeriodFilter.CUSTOM) {
        val start = customStartEpochMs ?: return null
        val end = customEndEpochMs ?: return null
        return start to end
    }

    val timeZone = TimeZone.currentSystemDefault()
    val currentDate = Instant
        .fromEpochMilliseconds(nowEpochMs)
        .toLocalDateTime(timeZone)
        .date
    val startEpochMs = currentDate
        .minus(period.toDatePeriod())
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()

    return startEpochMs to nowEpochMs
}

private fun buildTotalOverviewLine(
    series: List<OverviewHistoryLine>,
): OverviewHistoryLine? {
    val timestamps = series
        .flatMap { line -> line.points.map(OverviewHistoryPoint::timestampEpochMs) }
        .distinct()
        .sorted()

    if (timestamps.isEmpty()) return null

    val totalPoints = timestamps.map { timestamp ->
        val totalValue = series.sumOf { line ->
            line.points
                .lastOrNull { point -> point.timestampEpochMs <= timestamp }
                ?.valueMinor
                ?: 0L
        }
        OverviewHistoryPoint(
            timestampEpochMs = timestamp,
            valueMinor = totalValue,
            axisLabel = formatOverviewAxisLabel(timestamp),
            detailLabel = formatOverviewDetailLabel(timestamp),
        )
    }

    return OverviewHistoryLine(
        id = "total",
        label = "Total",
        points = totalPoints,
        isTotal = true,
    )
}

internal fun calculateOverviewCashFlow(
    transactions: List<FinanceTransaction>,
    period: OverviewPeriodFilter,
    customStartEpochMs: Long? = null,
    customEndEpochMs: Long? = null,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): OverviewCashFlow {
    val filteredTransactions = transactions.filter { transaction ->
        isInOverviewPeriod(
            epochMs = transaction.postedAtEpochMs,
            period = period,
            customStartEpochMs = customStartEpochMs,
            customEndEpochMs = customEndEpochMs,
            nowEpochMs = nowEpochMs,
        )
    }
    val incomeMinor = filteredTransactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amountMinor }
    val expenseMinor = filteredTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amountMinor }

    return OverviewCashFlow(
        incomeMinor = incomeMinor,
        expenseMinor = expenseMinor,
    )
}

private fun fallbackTitle(type: TransactionType): String = when (type) {
    TransactionType.INCOME -> "Income"
    TransactionType.EXPENSE -> "Expense"
    TransactionType.TRANSFER -> "Transfer"
    TransactionType.ADJUSTMENT -> "Adjustment"
}

internal fun isInOverviewPeriod(
    epochMs: Long,
    period: OverviewPeriodFilter,
    customStartEpochMs: Long? = null,
    customEndEpochMs: Long? = null,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): Boolean {
    if (period == OverviewPeriodFilter.ALL) return true
    if (period == OverviewPeriodFilter.CUSTOM) {
        val startEpoch = customStartEpochMs ?: return true
        val endEpoch = customEndEpochMs ?: return true
        return epochMs in startEpoch..endEpoch
    }

    val timeZone = TimeZone.currentSystemDefault()
    val currentDate = Instant
        .fromEpochMilliseconds(nowEpochMs)
        .toLocalDateTime(timeZone)
        .date
    val targetDate = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).date

    val cutoffDate = currentDate.minus(period.toDatePeriod())
    return targetDate >= cutoffDate && targetDate <= currentDate
}

private fun OverviewPeriodFilter.toDatePeriod(): DatePeriod = when (this) {
    OverviewPeriodFilter.ONE_MONTH -> DatePeriod(months = 1)
    OverviewPeriodFilter.THREE_MONTHS -> DatePeriod(months = 3)
    OverviewPeriodFilter.SIX_MONTHS -> DatePeriod(months = 6)
    OverviewPeriodFilter.ONE_YEAR -> DatePeriod(years = 1)
    OverviewPeriodFilter.CUSTOM -> DatePeriod()
    OverviewPeriodFilter.ALL -> DatePeriod()
}

private fun formatSavingsRate(monthlyIncomeMinor: Long, monthlyExpenseMinor: Long): String {
    if (monthlyIncomeMinor <= 0L) return "--"
    val savingsMinor = monthlyIncomeMinor - monthlyExpenseMinor
    val percentage = (savingsMinor * 100) / monthlyIncomeMinor
    return "$percentage%"
}

private fun formatSignedMoney(
    amountMinor: Long,
    currencyCode: String,
    isExpense: Boolean,
): String {
    val prefix = if (isExpense) "-" else "+"
    return prefix + formatMoney(amountMinor, currencyCode)
}

private fun formatMoney(amountMinor: Long, currencyCode: String): String {
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    return "$major.${minor.toString().padStart(2, '0')} $currencyCode"
}

private fun formatDateLabel(epochMs: Long): String {
    val timeZone = TimeZone.currentSystemDefault()
    val current = Instant
        .fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
        .toLocalDateTime(timeZone)
        .date
    val target = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).date

    if (current == target) return "Today"

    val month = target.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)

    return "$month ${target.day}"
}

private fun formatOverviewAxisLabel(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${shortMonthLabel(date)} ${date.day}"
}

private fun formatOverviewDetailLabel(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${shortMonthLabel(date)} ${date.day}, ${date.year}"
}

private fun parseOverviewIsoDateEpochMs(rawValue: String): Long = runCatching {
    val localDate = runCatching { LocalDate.parse(rawValue) }
        .getOrElse { LocalDate.parse(rawValue.substringBefore('T')) }
    localDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}.getOrDefault(0L)

private fun shortMonthLabel(date: LocalDate): String =
    date.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)
