package com.myfinances.app.presentation.shared

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal fun formatMinorMoney(
    amountMinor: Long,
    currencyCode: String,
    includePositiveSign: Boolean = false,
): String {
    val sign = when {
        amountMinor < 0 -> "-"
        includePositiveSign -> "+"
        else -> ""
    }
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    return "$sign$major.${minor.toString().padStart(2, '0')} $currencyCode"
}

internal fun formatTimestampLabel(epochMs: Long): String {
    val localDateTime = Instant
        .fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val month = localDateTime.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)
    val day = localDateTime.day
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    return "$month $day, $hour:$minute"
}

internal fun formatDayLabel(
    epochMs: Long,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): String {
    val timeZone = TimeZone.currentSystemDefault()
    val currentDate = Instant
        .fromEpochMilliseconds(nowEpochMs)
        .toLocalDateTime(timeZone)
        .date
    val targetDate = Instant
        .fromEpochMilliseconds(epochMs)
        .toLocalDateTime(timeZone)
        .date

    if (currentDate == targetDate) return "Today"

    val month = targetDate.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)

    return "$month ${targetDate.day}"
}
