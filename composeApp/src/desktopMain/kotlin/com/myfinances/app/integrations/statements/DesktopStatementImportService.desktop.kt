package com.myfinances.app.integrations.statements

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.AccountValuationSnapshot
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.cajaingenieros.importer.CajaIngenierosPdfStatement
import com.myfinances.app.integrations.cajaingenieros.importer.CajaIngenierosPdfStatementParser
import com.myfinances.app.integrations.degiro.importer.DegiroPortfolioCsv
import com.myfinances.app.integrations.degiro.importer.DegiroPortfolioCsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.abs
import kotlin.time.Clock

private const val CAJA_INGENIEROS_PDF_SOURCE = "Caja Ingenieros PDF"
private const val DEGIRO_PORTFOLIO_CSV_SOURCE = "DEGIRO Portfolio CSV"
private const val DEGIRO_PORTFOLIO_EXTERNAL_ID = "degiro-portfolio"

class DesktopStatementImportService(
    private val ledgerRepository: LedgerRepository,
) : StatementImportService {
    override val isSupported: Boolean = true

    override suspend fun importCajaIngenierosPdf(): StatementImportResult? {
        val selectedFile = withContext(Dispatchers.Swing) { choosePdfFile() } ?: return null
        return importCajaIngenierosPdfFromFile(selectedFile.absolutePath)
    }

    override suspend fun importCajaIngenierosPdfFromFile(filePath: String): StatementImportResult {
        val file = File(filePath)
        val statement = withContext(Dispatchers.IO) { extractAndParseStatement(file) }
        return withContext(Dispatchers.IO) { importStatement(file, statement) }
    }

    override suspend fun importDegiroPortfolioCsv(): PortfolioImportResult? {
        val selectedFile = withContext(Dispatchers.Swing) { chooseCsvFile() } ?: return null
        return importDegiroPortfolioCsvFromFile(selectedFile.absolutePath)
    }

    override suspend fun importDegiroPortfolioCsvFromFile(filePath: String): PortfolioImportResult {
        val file = File(filePath)
        val portfolio = withContext(Dispatchers.IO) {
            DegiroPortfolioCsvParser.parse(file.readText())
        }
        return withContext(Dispatchers.IO) { importDegiroPortfolio(file, portfolio) }
    }

    private suspend fun importStatement(
        file: File,
        statement: CajaIngenierosPdfStatement,
    ): StatementImportResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val existingAccounts = ledgerRepository.observeAccounts(includeArchived = true).first()
        val existingAccount = existingAccounts.firstOrNull { account ->
            account.externalAccountId == statement.iban
        }
        val accountId = existingAccount?.id ?: buildImportedAccountId(statement.iban)
        val initialAccount = Account(
            id = accountId,
            name = existingAccount?.name ?: buildImportedAccountName(statement),
            type = existingAccount?.type ?: AccountType.CHECKING,
            currencyCode = statement.currencyCode,
            openingBalanceMinor = existingAccount?.openingBalanceMinor ?: 0L,
            sourceType = AccountSourceType.FILE_IMPORT,
            sourceProvider = CAJA_INGENIEROS_PDF_SOURCE,
            externalAccountId = statement.iban,
            lastSyncedAtEpochMs = now,
            isArchived = existingAccount?.isArchived ?: false,
            createdAtEpochMs = existingAccount?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )
        ledgerRepository.upsertAccount(initialAccount)

        val transactionIdsBeforeImport = ledgerRepository.observeTransactionsForAccount(accountId)
            .first()
            .map(FinanceTransaction::id)
            .toSet()

        val importedTransactions = statement.transactions.map { row ->
            val transactionId = buildImportedTransactionId(
                iban = statement.iban,
                operationDate = row.operationDate,
                valueDate = row.valueDate,
                description = row.description,
                amountMinor = row.amountMinor,
                resultingBalanceMinor = row.resultingBalanceMinor,
            )
            FinanceTransaction(
                id = transactionId,
                accountId = accountId,
                categoryId = null,
                type = if (row.amountMinor >= 0L) TransactionType.INCOME else TransactionType.EXPENSE,
                amountMinor = abs(row.amountMinor),
                currencyCode = statement.currencyCode,
                merchantName = row.description,
                note = "Imported from Caja Ingenieros PDF. Value date: ${row.valueDate}. Statement balance: ${formatBalanceNote(row.resultingBalanceMinor, statement.currencyCode)}.",
                sourceProvider = CAJA_INGENIEROS_PDF_SOURCE,
                externalTransactionId = null,
                postedAtEpochMs = parseStatementDate(row.operationDate),
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )
        }

        importedTransactions.forEach { transaction ->
            ledgerRepository.upsertTransaction(transaction)
        }

        val allTransactionsForAccount = ledgerRepository.observeTransactionsForAccount(accountId)
            .first()
        val signedNetMinor = allTransactionsForAccount.sumOf { transaction ->
            when (transaction.type) {
                TransactionType.EXPENSE -> -transaction.amountMinor
                TransactionType.INCOME -> transaction.amountMinor
                TransactionType.TRANSFER -> 0L
                TransactionType.ADJUSTMENT -> 0L
            }
        }
        val reconstructedOpeningBalanceMinor = statement.endingBalanceMinor - signedNetMinor
        val account = Account(
            id = accountId,
            name = initialAccount.name,
            type = initialAccount.type,
            currencyCode = statement.currencyCode,
            openingBalanceMinor = reconstructedOpeningBalanceMinor,
            sourceType = AccountSourceType.FILE_IMPORT,
            sourceProvider = CAJA_INGENIEROS_PDF_SOURCE,
            externalAccountId = statement.iban,
            lastSyncedAtEpochMs = now,
            isArchived = initialAccount.isArchived,
            createdAtEpochMs = initialAccount.createdAtEpochMs,
            updatedAtEpochMs = now,
        )
        ledgerRepository.upsertAccount(account)

        val importedCount = importedTransactions.count { transaction ->
            transaction.id !in transactionIdsBeforeImport
        }

        return StatementImportResult(
            accountName = account.name,
            importedTransactions = importedCount,
            skippedTransactions = importedTransactions.size - importedCount,
            endingBalanceMinor = statement.endingBalanceMinor,
            currencyCode = statement.currencyCode,
            sourceFileName = file.name,
        )
    }

    private suspend fun importDegiroPortfolio(
        file: File,
        portfolio: DegiroPortfolioCsv,
    ): PortfolioImportResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val valuationDate = todayIsoDate()
        val existingAccounts = ledgerRepository.observeAccounts(includeArchived = true).first()
        val existingAccount = existingAccounts.firstOrNull { account ->
            account.sourceProvider == DEGIRO_PORTFOLIO_CSV_SOURCE ||
                account.externalAccountId == DEGIRO_PORTFOLIO_EXTERNAL_ID
        }
        val accountId = existingAccount?.id ?: "account-file-degiro-portfolio"
        val account = Account(
            id = accountId,
            name = existingAccount?.name ?: "DEGIRO Portfolio",
            type = AccountType.INVESTMENT,
            currencyCode = portfolio.currencyCode,
            openingBalanceMinor = existingAccount?.openingBalanceMinor ?: portfolio.totalValueMinor,
            sourceType = AccountSourceType.FILE_IMPORT,
            sourceProvider = DEGIRO_PORTFOLIO_CSV_SOURCE,
            externalAccountId = DEGIRO_PORTFOLIO_EXTERNAL_ID,
            lastSyncedAtEpochMs = now,
            isArchived = existingAccount?.isArchived ?: false,
            createdAtEpochMs = existingAccount?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )
        ledgerRepository.upsertAccount(account)

        val positions = portfolio.positions.map { row ->
            InvestmentPosition(
                id = buildDegiroPositionId(accountId = accountId, productName = row.productName, isin = row.isin),
                accountId = accountId,
                providerAccountId = DEGIRO_PORTFOLIO_EXTERNAL_ID,
                instrumentIsin = row.isin,
                instrumentName = row.productName,
                assetClass = "Broker portfolio holding",
                titles = row.quantity,
                price = row.price,
                marketValueMinor = row.valueMinor,
                costAmountMinor = null,
                valuationDate = valuationDate,
                updatedAtEpochMs = now,
            )
        }
        ledgerRepository.replaceInvestmentPositions(accountId, positions)

        ledgerRepository.upsertAccountValuationSnapshot(
            AccountValuationSnapshot(
                id = "snapshot-file-degiro-$accountId-$valuationDate",
                accountId = accountId,
                sourceProvider = DEGIRO_PORTFOLIO_CSV_SOURCE,
                currencyCode = portfolio.currencyCode,
                valueMinor = portfolio.totalValueMinor,
                valuationDate = valuationDate,
                capturedAtEpochMs = now,
            ),
        )

        return PortfolioImportResult(
            accountName = account.name,
            importedPositions = positions.size,
            totalValueMinor = portfolio.totalValueMinor,
            cashBalanceMinor = portfolio.cashBalanceMinor,
            currencyCode = portfolio.currencyCode,
            sourceFileName = file.name,
        )
    }
}

private fun choosePdfFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import Caja Ingenieros PDF statement"
        fileFilter = FileNameExtensionFilter("PDF files", "pdf")
        selectedFile = File("movimientos.pdf")
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun chooseCsvFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import DEGIRO portfolio CSV"
        fileFilter = FileNameExtensionFilter("CSV files", "csv")
        selectedFile = File("Portfolio.csv")
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun extractAndParseStatement(file: File): CajaIngenierosPdfStatement {
    val text = Loader.loadPDF(file).use { document ->
        PDFTextStripper().getText(document)
    }
    return CajaIngenierosPdfStatementParser.parse(text)
}

private fun buildImportedAccountId(iban: String): String =
    "account-file-caja-${iban.lowercase()}"

private fun buildImportedAccountName(statement: CajaIngenierosPdfStatement): String {
    val holderLabel = statement.holderName?.takeIf { it.isNotBlank() }
    return if (holderLabel != null) {
        "Caja Ingenieros ${statement.iban.takeLast(4)} ($holderLabel)"
    } else {
        "Caja Ingenieros ${statement.iban.takeLast(4)}"
    }
}

private fun buildImportedTransactionId(
    iban: String,
    operationDate: String,
    valueDate: String,
    description: String,
    amountMinor: Long,
    resultingBalanceMinor: Long,
): String {
    val signature = listOf(
        iban,
        operationDate,
        valueDate,
        description.trim().lowercase(),
        amountMinor.toString(),
        resultingBalanceMinor.toString(),
    ).joinToString("|")
    val hash = signature.hashCode().toUInt().toString(16)
    return "txn-file-caja-$hash"
}

private fun parseStatementDate(raw: String): Long {
    val parts = raw.split("/")
    val localDate = LocalDate.parse("${parts[2]}-${parts[1]}-${parts[0]}")
    return localDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

private fun formatBalanceNote(amountMinor: Long, currencyCode: String): String {
    val absoluteMinor = abs(amountMinor)
    val major = absoluteMinor / 100
    val minor = absoluteMinor % 100
    return "$major.${minor.toString().padStart(2, '0')} $currencyCode"
}

private fun todayIsoDate(): String =
    Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()

private fun buildDegiroPositionId(
    accountId: String,
    productName: String,
    isin: String?,
): String {
    val signature = listOf(accountId, isin.orEmpty(), productName.trim().lowercase())
        .joinToString("|")
    val hash = signature.hashCode().toUInt().toString(16)
    return "position-file-degiro-$hash"
}
