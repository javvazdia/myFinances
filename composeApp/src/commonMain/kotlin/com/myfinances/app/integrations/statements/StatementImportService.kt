package com.myfinances.app.integrations.statements

interface StatementImportService {
    val isSupported: Boolean

    suspend fun importCajaIngenierosPdf(): StatementImportResult?
}

data class StatementImportResult(
    val accountName: String,
    val importedTransactions: Int,
    val skippedTransactions: Int,
    val endingBalanceMinor: Long,
    val currencyCode: String,
    val sourceFileName: String,
)

object UnsupportedStatementImportService : StatementImportService {
    override val isSupported: Boolean = false

    override suspend fun importCajaIngenierosPdf(): StatementImportResult? =
        error("PDF statement import is not available on this platform yet.")
}
