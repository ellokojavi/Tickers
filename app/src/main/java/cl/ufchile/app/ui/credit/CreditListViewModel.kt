package cl.ufchile.app.ui.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.ufchile.app.data.repo.SimulationRepository
import cl.ufchile.app.data.repo.UfRepository
import cl.ufchile.app.domain.engine.MortgageEngine
import cl.ufchile.app.domain.model.MortgageResult
import cl.ufchile.app.domain.model.Simulation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class SimulationSummary(
    val simulation: Simulation,
    val result: MortgageResult,
)

data class CreditListUiState(
    val loading: Boolean = true,
    val items: List<SimulationSummary> = emptyList(),
    val ufValue: BigDecimal? = null,
    /** Set after a delete so the UI can offer an undo. */
    val lastDeleted: Simulation? = null,
)

class CreditListViewModel(
    private val simulations: SimulationRepository,
    private val uf: UfRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CreditListUiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(ufValue = uf.ufOn(LocalDate.now())?.value)
        }
        viewModelScope.launch {
            simulations.observeAll().collect { list ->
                _ui.value = _ui.value.copy(
                    loading = false,
                    items = list.map { SimulationSummary(it, MortgageEngine.simulate(it.input)) },
                )
            }
        }
    }

    fun delete(simulation: Simulation) {
        viewModelScope.launch {
            simulations.delete(simulation.id)
            _ui.value = _ui.value.copy(lastDeleted = simulation)
        }
    }

    /** Re-inserts the last deleted simulation. It gets a new id; the data is identical. */
    fun undoDelete() {
        val deleted = _ui.value.lastDeleted ?: return
        viewModelScope.launch {
            simulations.create(deleted.name, deleted.notes, deleted.input)
            _ui.value = _ui.value.copy(lastDeleted = null)
        }
    }

    fun clearUndo() {
        _ui.value = _ui.value.copy(lastDeleted = null)
    }

    fun duplicate(simulation: Simulation) {
        viewModelScope.launch {
            simulations.duplicate(simulation.id, "${simulation.name} (copia)")
        }
    }
}
