package com.myfinances.app.data

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.OverviewSnapshot
import com.myfinances.app.domain.model.RecentTransaction
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.repository.FinanceRepository
import com.myfinances.app.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultFinanceRepository(
    private val ledgerRepository: LedgerRepository,
) : FinanceRepository {
    override fun observeOverview(): Flow<OverviewSnapshot> = combine(
        ledgerRepository.observeAccounts(),
        ledgerRepository.observeCategories(),
        ledgerRepository.observeAllTransactions(),
        ledgerRepository.observeRecentTransactions(limit = 5),
    ) { accounts, categories, allTransactions, recentTransactions ->
        val categoryNames = categories.associateBy(Category::id)
        val totalBalanceMinor = calculateTotalBalance(accounts, allTransactions)
        val monthlyIncomeMinor = allTransactions
            .filter { it.type == TransactionType.INCOME && isInCurrentMonth(it.postedAtEpochMs) }
            .sumOf { it.amountMinor }
        val monthlyExpenseMinor = allTransactions
            .filter { it.type == TransactionType.EXPENSE && isInCurrentMonth(it.postedAtEpochMs) }
            .sumOf { it.amountMinor }

        OverviewSnapshot(
            totalBalance = formatMoney(totalBalanceMinor, "EUR"),
            monthlyIncome = formatMoney(monthlyIncomeMinor, "EUR"),
            monthlyExpenses = formatMoney(monthlyExpenseMinor, "EUR"),
            savingsRate = formatSavingsRate(monthlyIncomeMinor, monthlyExpenseMinor),
            focusMessage = buildFocusMessage(accounts, allTransactions),
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

    private fun calculateTotalBalance(
        accounts: List<Account>,
        allTransactions: List<FinanceTransaction>,
    ): Long {
        val openingBalances = accounts.sumOf(Account::openingBalanceMinor)
        val netTransactions = allTransactions.sumOf { transaction ->
            when (transaction.type) {
                TransactionType.INCOME -> transaction.amountMinor
                TransactionType.EXPENSE -> -transaction.amountMinor
                TransactionType.TRANSFER -> 0L
                TransactionType.ADJUSTMENT -> transaction.amountMinor
            }
        }

        return openingBalances + netTransactions
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

private fun fallbackTitle(type: TransactionType): String = when (type) {
    TransactionType.INCOME -> "Income"
    TransactionType.EXPENSE -> "Expense"
    TransactionType.TRANSFER -> "Transfer"
    TransactionType.ADJUSTMENT -> "Adjustment"
}

private fun isInCurrentMonth(epochMs: Long): Boolean {
    val timeZone = TimeZone.currentSystemDefault()
    val current = Instant
        .fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
        .toLocalDateTime(timeZone)
    val target = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)

    return current.year == target.year && current.month == target.month
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
