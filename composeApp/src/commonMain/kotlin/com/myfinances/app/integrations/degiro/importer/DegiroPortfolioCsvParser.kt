package com.myfinances.app.integrations.degiro.importer

object DegiroPortfolioCsvParser {
    fun parse(rawCsv: String): DegiroPortfolioCsv {
        val records = rawCsv
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::parseCsvRecord)
            .toList()

        if (records.size < 2) {
            error("DEGIRO portfolio CSV does not contain portfolio rows.")
        }

        val header = records.first()
        val indexes = DegiroPortfolioIndexes.from(header)
        val rows = records
            .drop(1)
            .mapNotNull { record -> parseRow(record, indexes) }

        if (rows.isEmpty()) {
            error("DEGIRO portfolio CSV did not contain importable positions.")
        }

        val currencyCode = header.getOrNull(indexes.valueIndex)
            ?.substringAfter(" en ", missingDelimiterValue = "")
            ?.trim()
            ?.takeIf { value -> value.length == 3 }
            ?: rows.firstNotNullOfOrNull(DegiroPortfolioCsvRow::localCurrencyCode)
            ?: "EUR"

        return DegiroPortfolioCsv(
            currencyCode = currencyCode,
            rows = rows,
        )
    }

    private fun parseRow(
        record: List<String>,
        indexes: DegiroPortfolioIndexes,
    ): DegiroPortfolioCsvRow? {
        val productName = record.getOrNull(indexes.productIndex)?.trim().orEmpty()
        if (productName.isBlank()) return null

        val valueMinor = record.getOrNull(indexes.valueIndex)
            ?.let(::parseDecimalMinor)
            ?: return null

        return DegiroPortfolioCsvRow(
            productName = productName,
            isin = record.getOrNull(indexes.isinIndex)?.trim()?.ifBlank { null },
            quantity = record.getOrNull(indexes.quantityIndex)?.let(::parseDecimalDouble),
            price = record.getOrNull(indexes.priceIndex)?.let(::parseDecimalDouble),
            localCurrencyCode = record.getOrNull(indexes.localCurrencyIndex)?.trim()?.ifBlank { null },
            localValueMinor = record.getOrNull(indexes.localValueIndex)?.let(::parseDecimalMinor),
            valueMinor = valueMinor,
        )
    }
}

private data class DegiroPortfolioIndexes(
    val productIndex: Int,
    val isinIndex: Int,
    val quantityIndex: Int,
    val priceIndex: Int,
    val localCurrencyIndex: Int,
    val localValueIndex: Int,
    val valueIndex: Int,
) {
    companion object {
        fun from(header: List<String>): DegiroPortfolioIndexes {
            val normalized = header.map { value -> value.trim().lowercase() }
            return DegiroPortfolioIndexes(
                productIndex = normalized.indexOf("producto").requireColumn("Producto"),
                isinIndex = normalized.indexOf("symbol/isin").requireColumn("Symbol/ISIN"),
                quantityIndex = normalized.indexOf("cantidad").requireColumn("Cantidad"),
                priceIndex = normalized.indexOf("precio de").requireColumn("Precio de"),
                localCurrencyIndex = normalized.indexOf("valor local").requireColumn("Valor local"),
                localValueIndex = normalized.indexOfFirst { value -> value.isBlank() },
                valueIndex = normalized.indexOfFirst { value -> value.startsWith("valor en ") }
                    .requireColumn("Valor en EUR"),
            )
        }
    }
}

private fun Int.requireColumn(label: String): Int =
    takeIf { index -> index >= 0 }
        ?: error("DEGIRO portfolio CSV is missing the '$label' column.")

private fun parseCsvRecord(line: String): List<String> {
    val values = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    var insideQuotes = false

    while (index < line.length) {
        val character = line[index]
        when {
            character == '"' && insideQuotes && line.getOrNull(index + 1) == '"' -> {
                current.append('"')
                index += 1
            }
            character == '"' -> insideQuotes = !insideQuotes
            character == ',' && !insideQuotes -> {
                values += current.toString()
                current.clear()
            }
            else -> current.append(character)
        }
        index += 1
    }

    values += current.toString()
    return values
}

private fun parseDecimalDouble(rawValue: String): Double? =
    rawValue
        .trim()
        .ifBlank { null }
        ?.replace(".", "")
        ?.replace(',', '.')
        ?.toDoubleOrNull()

private fun parseDecimalMinor(rawValue: String): Long? {
    val normalized = rawValue
        .trim()
        .ifBlank { return null }
        .replace(".", "")
        .replace(',', '.')
    val sign = if (normalized.startsWith("-")) -1L else 1L
    val unsignedValue = normalized.removePrefix("-").removePrefix("+")
    val parts = unsignedValue.split('.')
    val wholePart = parts.firstOrNull()?.toLongOrNull() ?: return null
    val decimalPart = parts.getOrNull(1)?.padEnd(2, '0')?.take(2) ?: "00"
    return sign * ((wholePart * 100) + decimalPart.toLong())
}
