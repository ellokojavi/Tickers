package cl.ufchile.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.ufchile.app.core.format.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A labelled date with a bounded picker.
 *
 * The bounds are enforced in the calendar itself rather than validated
 * afterwards: a date the app has no value for should not be selectable in the
 * first place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    min: LocalDate? = null,
    max: LocalDate? = null,
) {
    var open by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Fmt.longDate(value), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(
                onClick = { open = true },
                shape = MaterialTheme.shapes.small,
            ) { Text("Cambiar") }
        }
    }

    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.toUtcMillis(),
            yearRange = (min?.year ?: 1900)..(max?.year ?: 2100),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = utcTimeMillis.toUtcDate()
                    return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
                }

                override fun isSelectableYear(year: Int): Boolean =
                    year in (min?.year ?: 1900)..(max?.year ?: 2100)
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { onSelect(it.toUtcDate()) }
                        open = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = state) }
    }
}

/** The Material date picker speaks UTC milliseconds; the app speaks dates. */
fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.toUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
