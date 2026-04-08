package com.myfinances.app.presentation.accounts

import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.presentation.shared.formatMinorMoney
import com.myfinances.app.presentation.shared.formatTimestampLabel
import kotlin.random.Random

internal val AccountType.label: String
    get() = name
        .lowercase()
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { character -> character.uppercase() }
        }

internal fun normalizeCurrencyCode(rawValue: String): String? {
    val normalized = rawValue.trim().uppercase()
    return normalized.takeIf { value ->
        value.length == 3 && value.all(Char::isLetter)
    }
}

internal fun parseAmountToMinor(rawValue: String): Long? {
    val normalized = rawValue.trim().replace(',', '.')
    if (normalized.isBlank()) return 0L

    val amountPattern = Regex("^-?\\d+(\\.\\d{0,2})?$")
    if (!amountPattern.matches(normalized)) return null

    val isNegative = normalized.startsWith('-')
    val unsignedValue = normalized.removePrefix("-")
    val parts = unsignedValue.split('.')
    val wholePart = parts.firstOrNull()?.toLongOrNull() ?: return null
    val decimalPart = parts.getOrNull(1)?.padEnd(2, '0') ?: "00"
    val minorAmount = (wholePart * 100) + decimalPart.toLong()

    return if (isNegative) -minorAmount else minorAmount
}

internal fun generateAccountId(name: String, timestampMs: Long): String {
    val slug = name
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "account" }

    return "account-$slug-$timestampMs-${Random.nextInt(1000, 9999)}"
}

internal fun formatMoney(amountMinor: Long, currencyCode: String): String =
    formatMinorMoney(amountMinor, currencyCode)

internal fun formatSyncTimestamp(epochMs: Long?): String {
    if (epochMs == null) return "Not synced yet"
    return formatTimestampLabel(epochMs)
}

internal fun buildPositionSubtitle(position: InvestmentPosition): String {
    val assetClass = position.assetClass ?: "Portfolio holding"
    val quantity = position.titles?.let { titles ->
        "${formatHoldingDecimal(titles)} units"
    } ?: "Units unavailable"
    val price = position.price?.let { value ->
        "@ ${formatHoldingDecimal(value)}"
    }

    return listOfNotNull(assetClass, quantity, price).joinToString(" | ")
}

internal fun buildPositionValuationLabel(
    position: InvestmentPosition,
    currencyCode: String,
): String {
    val marketValue = position.marketValueMinor?.let { amount ->
        formatMoney(amount, currencyCode)
    } ?: "Unknown value"
    val costBasis = position.costAmountMinor?.let { amount ->
        "Cost ${formatMoney(amount, currencyCode)}"
    }

    return listOfNotNull("Market value $marketValue", costBasis).joinToString(" | ")
}

internal fun formatAccountAmountInput(amountMinor: Long): String {
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    val prefix = if (amountMinor < 0) "-" else ""
    return "$prefix$major.${minor.toString().padStart(2, '0')}"
}

private fun formatHoldingDecimal(value: Double): String =
    value
        .toString()
        .trimEnd('0')
        .trimEnd('.')
