package cl.ufchile.app.ui.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.domain.engine.UfEngine
import cl.ufchile.app.domain.model.PrepaymentMode
import cl.ufchile.app.domain.model.RateConvention
import cl.ufchile.app.ui.appViewModel
import cl.ufchile.app.ui.components.AppCard
import cl.ufchile.app.ui.components.ChipRow
import cl.ufchile.app.ui.components.KeyValueRow
import cl.ufchile.app.ui.components.NumberField
import cl.ufchile.app.ui.components.SectionTitle
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditEditorScreen(simulationId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "editor-$simulationId") {
        CreditEditorViewModel(it.simulationRepository, it.ufRepository, simulationId)
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showTable by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showPrepayDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (ui.isNew) "Nueva simulación" else "Editar simulación") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!ui.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    OutlinedTextField(
                        value = ui.form.name,
                        onValueChange = { v -> vm.update { it.copy(name = v) } },
                        label = { Text("Nombre") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ui.form.notes,
                        onValueChange = { v -> vm.update { it.copy(notes = v) } },
                        label = { Text("Notas") },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                AppCard {
                    SectionTitle("Crédito")
                    NumberField(
                        value = ui.form.propertyValueUf,
                        onValueChange = { v -> vm.update { it.copy(propertyValueUf = v) } },
                        label = "Valor de la propiedad",
                        suffix = "UF",
                        supporting = ui.ufValue?.let { uf ->
                            Fmt.parseNumber(ui.form.propertyValueUf)
                                ?.let { "≈ ${Fmt.clp(UfEngine.ufToClp(it, uf))}" }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = ui.form.downPaymentUf,
                        onValueChange = { v -> vm.update { it.copy(downPaymentUf = v) } },
                        label = "Pie",
                        suffix = "UF",
                        supporting = ui.result?.let {
                            "Financias ${Fmt.pct(it.input.financedPct)} de la propiedad"
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = ui.form.annualRatePct,
                        onValueChange = { v -> vm.update { it.copy(annualRatePct = v) } },
                        label = "Tasa anual sobre la UF",
                        suffix = "%",
                        supporting = "El crédito queda en UF + ${ui.form.annualRatePct}%",
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = ui.form.termYears,
                        onValueChange = { v -> vm.update { it.copy(termYears = v) } },
                        label = "Plazo",
                        suffix = "años",
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Conversión de la tasa",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChipRow(
                        options = RateConvention.entries.toList(),
                        selected = ui.form.rateConvention,
                        onSelect = { v -> vm.update { it.copy(rateConvention = v) } },
                        label = { if (it == RateConvention.NOMINAL_DIVIDED) "Anual / 12" else "Efectiva" },
                    )
                    Text(
                        "Los bancos chilenos no usan todos la misma conversión. Si tu cotización " +
                            "no calza, prueba la otra opción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                AppCard {
                    SectionTitle("Seguros y costos")
                    NumberField(
                        value = ui.form.lifeInsuranceMonthlyPct,
                        onValueChange = { v -> vm.update { it.copy(lifeInsuranceMonthlyPct = v) } },
                        label = "Desgravamen mensual sobre el saldo",
                        suffix = "%",
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = ui.form.fireInsuranceMonthlyUf,
                        onValueChange = { v -> vm.update { it.copy(fireInsuranceMonthlyUf = v) } },
                        label = "Incendio y sismo (mensual)",
                        suffix = "UF",
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = ui.form.stampTaxPct,
                        onValueChange = { v -> vm.update { it.copy(stampTaxPct = v) } },
                        label = "Impuesto de timbres",
                        suffix = "%",
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = ui.form.originationFeeUf,
                        onValueChange = { v -> vm.update { it.copy(originationFeeUf = v) } },
                        label = "Comisión",
                        suffix = "UF",
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = ui.form.otherUpfrontCostsUf,
                        onValueChange = { v -> vm.update { it.copy(otherUpfrontCostsUf = v) } },
                        label = "Notaría, tasación, conservador",
                        suffix = "UF",
                    )
                }
            }

            item {
                AppCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle("Prepagos")
                        TextButton(onClick = { showPrepayDialog = true }) { Text("Agregar") }
                    }
                    if (ui.form.prepayments.isEmpty()) {
                        Text(
                            "Sin abonos a capital.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ui.form.prepayments.forEachIndexed { index, p ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Cuota ${p.monthNumber} · ${Fmt.uf(p.amountUf)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        p.mode.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.removePrepayment(index) }) { Text("Quitar") }
                            }
                        }
                    }
                }
            }

            ui.validationError?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            ui.result?.let { r ->
                item {
                    AppCard {
                        SectionTitle("Resultado")
                        Text(
                            Fmt.uf(r.firstTotalPaymentUf),
                            style = MaterialTheme.typography.displayMedium,
                        )
                        ui.ufValue?.let {
                            Text(
                                "≈ ${Fmt.clp(UfEngine.ufToClp(r.firstTotalPaymentUf, it))} la primera cuota",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        KeyValueRow("Monto del crédito", Fmt.uf(r.loanAmountUf))
                        KeyValueRow("Dividendo (capital + interés)", Fmt.uf(r.basePaymentUf))
                        KeyValueRow("Total intereses", Fmt.uf(r.totalInterestUf))
                        KeyValueRow("Total seguros", Fmt.uf(r.totalLifeInsuranceUf + r.totalFireInsuranceUf))
                        KeyValueRow("Gastos iniciales", Fmt.uf(r.upfrontCostsUf))
                        if (r.totalPrepaymentsUf.signum() > 0) {
                            KeyValueRow("Prepagos", Fmt.uf(r.totalPrepaymentsUf))
                            KeyValueRow("Cuotas ahorradas", "${r.monthsSaved}")
                        }
                        KeyValueRow("Cuotas", "${r.effectiveTermMonths}")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        KeyValueRow("Costo total", Fmt.uf(r.totalCostUf), emphasise = true)
                        r.caePct?.let { KeyValueRow("CAE", Fmt.pct(it), emphasise = true) }
                        ui.ufValue?.let {
                            Text(
                                "Los montos en pesos usan la UF de hoy (${Fmt.clpExact(it)}) y " +
                                    "cambiarán con la inflación. La deuda en UF no cambia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { showTable = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("Ver tabla de pagos") }
                        Button(
                            onClick = { vm.save { onBack() } },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (ui.isNew) "Guardar" else "Actualizar") }
                    }
                }
            }
        }
    }

    if (showTable && ui.result != null) {
        AmortizationTableDialog(
            title = ui.form.name,
            result = ui.result!!,
            ufValue = ui.ufValue,
            onDismiss = { showTable = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar simulación") },
            text = { Text("Se borrará \"${ui.form.name}\". Esta acción no se puede deshacer aquí.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete { onBack() }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }

    if (showPrepayDialog) {
        PrepaymentDialog(
            maxMonth = ui.result?.effectiveTermMonths ?: 360,
            onDismiss = { showPrepayDialog = false },
            onAdd = { month, amount, mode ->
                vm.addPrepayment(month, amount, mode)
                showPrepayDialog = false
            },
        )
    }
}

@Composable
private fun PrepaymentDialog(
    maxMonth: Int,
    onDismiss: () -> Unit,
    onAdd: (Int, BigDecimal, PrepaymentMode) -> Unit,
) {
    var month by remember { mutableStateOf("12") }
    var amount by remember { mutableStateOf("100") }
    var mode by remember { mutableStateOf(PrepaymentMode.REDUCE_TERM) }

    val monthValue = month.toIntOrNull()
    val amountValue = Fmt.parseNumber(amount)
    val valid = monthValue != null && monthValue in 1..maxMonth &&
        amountValue != null && amountValue.signum() > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abono a capital") },
        text = {
            Column {
                NumberField(
                    value = month,
                    onValueChange = { month = it },
                    label = "En la cuota número",
                    supporting = "Entre 1 y $maxMonth",
                )
                Spacer(Modifier.height(10.dp))
                NumberField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Monto",
                    suffix = "UF",
                )
                Spacer(Modifier.height(14.dp))
                ChipRow(
                    options = PrepaymentMode.entries.toList(),
                    selected = mode,
                    onSelect = { mode = it },
                    label = { it.label },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onAdd(monthValue!!, amountValue!!, mode) },
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
