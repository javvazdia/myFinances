package com.myfinances.app.integrations.cajaingenieros.importer

data class CajaIngenierosPdfStatement(
    val iban: String,
    val holderName: String?,
    val endingBalanceMinor: Long,
    val currencyCode: String,
    val transactions: List<CajaIngenierosPdfStatementTransaction>,
)

data class CajaIngenierosPdfStatementTransaction(
    val operationDate: String,
    val description: String,
    val valueDate: String,
    val amountMinor: Long,
    val resultingBalanceMinor: Long,
)
