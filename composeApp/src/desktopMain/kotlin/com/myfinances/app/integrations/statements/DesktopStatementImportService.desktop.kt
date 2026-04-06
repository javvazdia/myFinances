package com.myfinances.app.integrations.statements

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.cajaingenieros.importer.CajaIngenierosPdfStatement
import com.myfinances.app.integrations.cajaingenieros.importer.CajaIngenierosPdfStatementParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.abs
import kotlin.time.Clock

private const val CAJA_INGENIEROS_PDF_SOURCE = "Caja Ingenieros PDF"

class DesktopStatementImportService(
    private val ledgerRepository: LedgerRepository,
) : StatementImportService {
    override val isSupported: Boolean = true

    override suspend fun importCajaIngenierosPdf(): StatementImportResult? {
        val selectedFile = withContext(Dispatchers.Swing) { choosePdfFile() } ?: return null
        val statement = withContext(Dispatchers.IO) { extractAndParseStatement(selectedFile) }
        return withContext(Dispatchers.IO) { importStatement(selectedFile, statement) }
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
