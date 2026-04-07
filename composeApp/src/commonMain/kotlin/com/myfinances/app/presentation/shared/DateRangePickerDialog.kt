package com.myfinances.app.presentation.shared

import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFinancesDateRangePickerDialog(
    initialStartEpochMs: Long?,
    initialEndEpochMs: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartEpochMs,
        initialSelectedEndDateMillis = initialEndEpochMs,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val startEpochMs = pickerState.selectedStartDateMillis ?: return@Button
                    val endEpochMs = pickerState.selectedEndDateMillis ?: return@Button
                    onConfirm(
                        normalizeDateRangeStart(startEpochMs),
                        normalizeDateRangeEnd(endEpochMs),
                    )
                },
                enabled = pickerState.selectedStartDateMillis != null &&
                    pickerState.selectedEndDateMillis != null,
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DateRangePicker(state = pickerState)
    }
}

fun formatDateRangeLabel(
    startEpochMs: Long?,
    endEpochMs: Long?,
): String {
    val start = startEpochMs?.let(::formatShortDate) ?: return "Custom"
    val end = endEpochMs?.let(::formatShortDate) ?: return "Custom"
    return "$start - $end"
}

private fun formatShortDate(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val month = date.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)
    return "$month ${date.day}"
}

private fun normalizeDateRangeStart(epochMs: Long): Long {
    val date = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

private fun normalizeDateRangeEnd(epochMs: Long): Long {
    val startOfDayEpochMs = normalizeDateRangeStart(epochMs)
    return startOfDayEpochMs + MILLIS_IN_DAY - 1
}

private const val MILLIS_IN_DAY = 24L * 60L * 60L * 1000L
