package cl.tickers.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import cl.tickers.app.core.format.ThousandsTransformation
import cl.tickers.app.core.format.sanitizeNumericInput

/** A numeric field that accepts Chilean input ("1.250,5") and reports raw text. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    allowDecimals: Boolean = true,
    /** Sits after the suffix, inside the field's border. For acting on this one value. */
    action: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        // The field holds the raw number; thousands separators belong to the
        // display, so they are never part of what the user types or of what
        // gets parsed.
        onValueChange = { new -> onValueChange(sanitizeNumericInput(new, allowDecimals)) },
        visualTransformation = ThousandsTransformation(allowDecimals),
        label = { Text(label) },
        suffix = when {
            action != null -> {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        suffix?.let { Text(it) }
                        action()
                    }
                }
            }
            suffix != null -> { { Text(suffix) } }
            else -> null
        },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A single-choice row of chips. Used for ranges and enum options. */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
