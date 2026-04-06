package com.myfinances.app.integrations.cajaingenieros.importer

private val ibanRegex = Regex("""\bES(?:\s*\d){22}\b""")
private val balanceRegex = Regex("""([\d\.]+,\d{2})\s*EUR""", RegexOption.IGNORE_CASE)
private val fullTransactionRegex = Regex(
    """^(\d{2}/\d{2}/\d{4})\s+(.+?)\s+(\d{2}/\d{2}/\d{4})\s+(-?[\d\.]+,\d{2})\s*EUR\s+([\d\.]+,\d{2})\s*EUR$""",
    RegexOption.IGNORE_CASE,
)

object CajaIngenierosPdfStatementParser {
    fun parse(rawText: String): CajaIngenierosPdfStatement {
        val normalizedText = rawText
            .replace('\u00A0', ' ')
            .replace("Ã‚", "")
            .replace(Regex("[ ]+"), " ")
        val headerSection = normalizedText.substringBefore("FECHA DE OPERACI")
        val headerLines = headerSection
            .lines()
            .map(::normalizeStatementLine)
            .filter(String::isNotBlank)

        val iban = ibanRegex.find(headerSection)?.value
            ?.replace(" ", "")
            ?: error("Could not find a Caja Ingenieros IBAN in the PDF.")
        val endingBalanceMinor = headerLines
            .mapNotNull { line ->
                balanceRegex.matchEntire(line)?.groupValues?.getOrNull(1)
            }
            .firstOrNull()
            ?.let(::parseEuropeanAmountToMinor)
            ?: error("Could not find the statement balance in the PDF.")
        val holderName = headerLines.firstOrNull(::looksLikeHolderName)
        val transactions = parseTransactions(normalizedText)

        if (transactions.isEmpty()) {
            error("Could not find any transaction rows in the Caja Ingenieros PDF.")
        }

        return CajaIngenierosPdfStatement(
            iban = iban,
            holderName = holderName,
            endingBalanceMinor = endingBalanceMinor,
            currencyCode = "EUR",
            transactions = transactions,
        )
    }

    private fun parseTransactions(normalizedText: String): List<CajaIngenierosPdfStatementTransaction> {
        val lines = normalizedText
            .lines()
            .map(::normalizeStatementLine)
            .filter(String::isNotBlank)

        val transactions = mutableListOf<CajaIngenierosPdfStatementTransaction>()
        var inTable = false
        var currentRecordLines = mutableListOf<String>()

        fun flushCurrentRecord() {
            if (currentRecordLines.isEmpty()) return
            parseTransactionRecord(currentRecordLines.joinToString(" "))?.let(transactions::add)
            currentRecordLines = mutableListOf()
        }

        lines.forEach { line ->
            if (line.isTableHeaderLine()) {
                flushCurrentRecord()
                inTable = true
                return@forEach
            }
            if (!inTable || shouldIgnoreLine(line)) {
                return@forEach
            }
            if (line.contains("Consulta de movimientos de cuentas") || line.startsWith("http")) {
                flushCurrentRecord()
                inTable = false
                return@forEach
            }
            if (currentRecordLines.isNotEmpty() && line.isTrailingValueLine()) {
                currentRecordLines += line
            } else if (line.startsWithDate()) {
                flushCurrentRecord()
                currentRecordLines += line
            } else if (currentRecordLines.isNotEmpty()) {
                currentRecordLines += line
            }
        }

        flushCurrentRecord()
        return transactions
    }

    private fun parseTransactionRecord(recordLine: String): CajaIngenierosPdfStatementTransaction? {
        val sanitized = recordLine
            .replace("Ã‚", "")
            .replace("  ", " ")
            .trim()
        val match = fullTransactionRegex.matchEntire(sanitized) ?: return null

        return CajaIngenierosPdfStatementTransaction(
            operationDate = match.groupValues[1],
            description = match.groupValues[2].trim(),
            valueDate = match.groupValues[3],
            amountMinor = parseEuropeanAmountToMinor(match.groupValues[4]),
            resultingBalanceMinor = parseEuropeanAmountToMinor(match.groupValues[5]),
        )
    }
}

internal fun parseEuropeanAmountToMinor(raw: String): Long {
    val normalized = raw.trim()
    val sign = if (normalized.startsWith("-")) -1L else 1L
    val unsigned = normalized.removePrefix("-")
    val parts = unsigned.split(",")
    val wholePart = parts.firstOrNull()
        ?.replace(".", "")
        ?.toLongOrNull()
        ?: error("Invalid European amount: $raw")
    val fractionPart = parts.getOrNull(1)
        ?.padEnd(2, '0')
        ?.take(2)
        ?.toLongOrNull()
        ?: 0L
    return sign * ((wholePart * 100L) + fractionPart)
}

private fun normalizeStatementLine(line: String): String =
    line
        .replace('\u00A0', ' ')
        .replace("Ã‚", "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun shouldIgnoreLine(line: String): Boolean =
    line == "." ||
        line == "Ã‚" ||
        line.startsWith("EstÃƒÂ¡s en:", ignoreCase = true) ||
        line.startsWith("Estas en:", ignoreCase = true) ||
        line.startsWith("Estás en:", ignoreCase = true) ||
        line.startsWith("Consulta de movimientos", ignoreCase = true) ||
        line.startsWith("NÃƒÂºmero de cuenta", ignoreCase = true) ||
        line.startsWith("Número de cuenta", ignoreCase = true) ||
        line.startsWith("BIC entidad:", ignoreCase = true) ||
        line.startsWith("Titulares:", ignoreCase = true) ||
        line.startsWith("Saldo:", ignoreCase = true) ||
        line.startsWith("WEB PRIVADA", ignoreCase = true) ||
        line.startsWith("WCAG", ignoreCase = true) ||
        line.startsWith("Â© Caja Ingenieros", ignoreCase = true) ||
        line.startsWith("Mapa web", ignoreCase = true) ||
        line.startsWith("PÃƒÂ¡gina optimizada", ignoreCase = true) ||
        line.startsWith("Página optimizada", ignoreCase = true) ||
        line.startsWith("EncuÃƒÂ©ntranos", ignoreCase = true) ||
        line.startsWith("Encuéntranos", ignoreCase = true) ||
        line.startsWith("Descarga ahora", ignoreCase = true) ||
        line.matches(Regex("""^\d{1,2}/\d{1,2}/\d{2},.*$""")) ||
        line.all { character -> !character.isLetterOrDigit() }

private fun looksLikeHolderName(line: String): Boolean =
    line.any(Char::isLetter) &&
        !line.startsWith("Est", ignoreCase = true) &&
        !line.startsWith("Consulta de movimientos", ignoreCase = true) &&
        !line.startsWith("N", ignoreCase = true) &&
        !line.startsWith("BIC", ignoreCase = true) &&
        !line.startsWith("Titulares", ignoreCase = true) &&
        !line.startsWith("Saldo", ignoreCase = true) &&
        !line.contains("EUR", ignoreCase = true) &&
        !ibanRegex.containsMatchIn(line) &&
        !line.matches(Regex("""^[A-Z0-9]{8,}$"""))

private fun String.isTableHeaderLine(): Boolean =
    contains("FECHA DE OPERACI", ignoreCase = true) &&
        contains("CONCEPTO", ignoreCase = true) &&
        contains("FECHA VALOR", ignoreCase = true) &&
        contains("IMPORTE", ignoreCase = true) &&
        contains("SALDO", ignoreCase = true)

private fun String.isTrailingValueLine(): Boolean =
    Regex("""^\d{2}/\d{2}/\d{4}\s+-?[\d\.]+,\d{2}\s*EUR\s+[\d\.]+,\d{2}\s*EUR$""")
        .containsMatchIn(this)

private fun String.startsWithDate(): Boolean =
    Regex("""^\d{2}/\d{2}/\d{4}\b""").containsMatchIn(this)
