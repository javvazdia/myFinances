package com.myfinances.app.domain.model

fun calculateAccountCurrentBalances(
    accounts: List<Account>,
    transactions: List<FinanceTransaction>,
): Map<String, Long> {
    val transactionsByAccount = transactions.groupBy(FinanceTransaction::accountId)
    return accounts.associate { account ->
        account.id to calculateAccountCurrentBalance(
            account = account,
            transactions = transactionsByAccount[account.id].orEmpty(),
        )
    }
}

fun calculateAccountCurrentBalance(
    account: Account,
    transactions: List<FinanceTransaction>,
): Long {
    if (account.isSyncedInvestmentAccount()) {
        return account.openingBalanceMinor
    }

    val netTransactions = transactions.sumOf { transaction ->
        when (transaction.type) {
            TransactionType.INCOME -> transaction.amountMinor
            TransactionType.EXPENSE -> -transaction.amountMinor
            TransactionType.TRANSFER -> 0L
            TransactionType.ADJUSTMENT -> transaction.amountMinor
        }
    }

    return account.openingBalanceMinor + netTransactions
}

private fun Account.isSyncedInvestmentAccount(): Boolean =
    sourceType == AccountSourceType.API_SYNC && type == AccountType.INVESTMENT
