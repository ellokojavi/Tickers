package cl.ufchile.app.ui.credit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.ufchile.app.R
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.domain.engine.UfEngine
import cl.ufchile.app.ui.appViewModel
import cl.ufchile.app.ui.components.AppCard
import cl.ufchile.app.ui.components.EmptyState
import cl.ufchile.app.ui.components.KeyValueRow
import cl.ufchile.app.ui.components.ScreenHeader

@Composable
fun CreditListScreen(onOpen: (Long) -> Unit) {
    val vm = appViewModel { CreditListViewModel(it.simulationRepository, it.ufRepository) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.lastDeleted) {
        val deleted = ui.lastDeleted ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "\"${deleted.name}\" eliminada",
            actionLabel = "Deshacer",
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearUndo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen(0L) },
                // The label lives in a slot that is not merged into the node's
                // semantics, so without this the button is unlabelled for
                // TalkBack.
                icon = { Icon(Icons.Filled.Add, contentDescription = "Nueva simulación") },
                text = { Text("Nueva") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        // The outer Scaffold already consumed the system bars. Without this the
        // inset is applied twice and this title sits lower than every other
        // screen's.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(title = "Créditos hipotecarios")
            }

            if (!ui.loading && ui.items.isEmpty()) {
                item {
                    EmptyState(
                        title = "Sin simulaciones",
                        subtitle = "Crea una para modelar un crédito UF + tasa y guardar la tabla de pagos.",
                        illustration = R.drawable.ic_copihue,
                    )
                }
            }

            items(ui.items, key = { it.simulation.id }) { summary ->
                SimulationCard(
                    summary = summary,
                    ufValue = ui.ufValue,
                    onOpen = { onOpen(summary.simulation.id) },
                    onDuplicate = { vm.duplicate(summary.simulation) },
                    onDelete = { vm.delete(summary.simulation) },
                )
            }
        }
    }
}

@Composable
private fun SimulationCard(
    summary: SimulationSummary,
    ufValue: java.math.BigDecimal?,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val r = summary.result

    AppCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                summary.simulation.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        onClick = { menuOpen = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }

        Text(
            "UF + ${Fmt.pct(summary.simulation.input.annualRatePct)} · ${summary.simulation.input.termYears} años",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            Fmt.uf(r.firstTotalPaymentUf),
            style = MaterialTheme.typography.headlineMedium,
        )
        ufValue?.let {
            Text(
                "≈ ${Fmt.clp(UfEngine.ufToClp(r.firstTotalPaymentUf, it))} al mes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Monto del crédito", Fmt.uf(r.loanAmountUf))
        r.caePct?.let { KeyValueRow("CAE", Fmt.pct(it)) }
    }
}
