package com.myfinances.app.domain.model

fun calculateAccountCurrentBalances(
    accounts: List<Account>,
    transactions: List<FinanceTransaction>,
    snapshots: List<AccountValuationSnapshot> = emptyList(),
): Map<String, Long> {
    val transactionsByAccount = transactions.groupBy(FinanceTransaction::accountId)
    val latestSnapshotsByAccountId = snapshots
        .groupBy(AccountValuationSnapshot::accountId)
        .mapValues { (_, accountSnapshots) ->
            accountSnapshots.maxWithOrNull(
                compareBy<AccountValuationSnapshot> { snapshot -> snapshot.valuationDate.orEmpty() }
                    .thenBy(AccountValuationSnapshot::capturedAtEpochMs),
            )
        }
    return accounts.associate { account ->
        account.id to calculateAccountCurrentBalance(
            account = account,
            transactions = transactionsByAccount[account.id].orEmpty(),
            latestSnapshot = latestSnapshotsByAccountId[account.id],
        )
    }
}

fun calculateAccountCurrentBalance(
    account: Account,
    transactions: List<FinanceTransaction>,
    latestSnapshot: AccountValuationSnapshot? = null,
): Long {
    if (account.type == AccountType.INVESTMENT && latestSnapshot != null) {
        return latestSnapshot.valueMinor
    }

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
