package com.myfinances.app.integrations.degiro.importer

data class DegiroPortfolioCsv(
    val currencyCode: String,
    val rows: List<DegiroPortfolioCsvRow>,
) {
    val totalValueMinor: Long
        get() = rows.sumOf(DegiroPortfolioCsvRow::valueMinor)

    val cashBalanceMinor: Long
        get() = rows
            .filter(DegiroPortfolioCsvRow::isCash)
            .sumOf(DegiroPortfolioCsvRow::valueMinor)

    val positions: List<DegiroPortfolioCsvRow>
        get() = rows.filterNot(DegiroPortfolioCsvRow::isCash)
}

data class DegiroPortfolioCsvRow(
    val productName: String,
    val isin: String?,
    val quantity: Double?,
    val price: Double?,
    val localCurrencyCode: String?,
    val localValueMinor: Long?,
    val valueMinor: Long,
) {
    val isCash: Boolean
        get() = isin.isNullOrBlank() && productName.contains("CASH", ignoreCase = true)
}
