package org.incammino.hospiceinventory.ui.screens.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Product
import javax.inject.Inject

/**
 * Filtro per le manutenzioni.
 */
enum class MaintenanceFilter(val label: String) {
    ALL("Tutte"),
    OVERDUE("Scadute"),
    THIS_WEEK("Questa settimana"),
    THIS_MONTH("Questo mese")
}

/**
 * UI State per MaintenanceListScreen.
 */
data class MaintenanceListUiState(
    val items: List<MaintenanceAlertItem> = emptyList(),
    val filter: MaintenanceFilter = MaintenanceFilter.ALL,
    val isLoading: Boolean = true,
    val overdueCount: Int = 0,
    val weekCount: Int = 0,
    val monthCount: Int = 0
)

/**
 * Item per la lista delle manutenzioni in scadenza.
 */
data class MaintenanceAlertItem(
    val product: Product,
    val daysRemaining: Int,
    val isOverdue: Boolean,
    val isUrgent: Boolean // entro 7 giorni
)

/**
 * ViewModel per la lista manutenzioni in scadenza.
 */
@HiltViewModel
class MaintenanceListViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaintenanceListUiState())
    val uiState: StateFlow<MaintenanceListUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(MaintenanceFilter.ALL)

    init {
        loadData()
    }

    /**
     * Carica i dati delle manutenzioni.
     */
    private fun loadData() {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        viewModelScope.launch {
            combine(
                productRepository.getWithOverdueMaintenance(today),
                productRepository.getWithMaintenanceDueBetween(today, today.plus(DatePeriod(days = 7))),
                productRepository.getWithMaintenanceDueBetween(today.plus(DatePeriod(days = 8)), today.plus(DatePeriod(days = 30))),
                _filter
            ) { overdue, thisWeek, thisMonth, filter ->
                // Combina tutti i prodotti con manutenzione
                val allItems = buildList {
                    overdue.forEach { product ->
                        val days = product.maintenanceDaysRemaining()?.toInt() ?: 0
                        add(MaintenanceAlertItem(
                            product = product,
                            daysRemaining = days,
                            isOverdue = true,
                            isUrgent = false
                        ))
                    }
                    thisWeek.forEach { product ->
                        val days = product.maintenanceDaysRemaining()?.toInt() ?: 0
                        add(MaintenanceAlertItem(
                            product = product,
                            daysRemaining = days,
                            isOverdue = false,
                            isUrgent = true
                        ))
                    }
                    thisMonth.forEach { product ->
                        val days = product.maintenanceDaysRemaining()?.toInt() ?: 0
                        add(MaintenanceAlertItem(
                            product = product,
                            daysRemaining = days,
                            isOverdue = false,
                            isUrgent = false
                        ))
                    }
                }

                // Filtra in base al filtro selezionato
                val filtered = when (filter) {
                    MaintenanceFilter.ALL -> allItems
                    MaintenanceFilter.OVERDUE -> allItems.filter { it.isOverdue }
                    MaintenanceFilter.THIS_WEEK -> allItems.filter { it.isUrgent || it.isOverdue }
                    MaintenanceFilter.THIS_MONTH -> allItems
                }

                // Ordina per urgenza
                val sorted = filtered.sortedWith(
                    compareBy<MaintenanceAlertItem> { !it.isOverdue }
                        .thenBy { !it.isUrgent }
                        .thenBy { it.daysRemaining }
                )

                MaintenanceListUiState(
                    items = sorted,
                    filter = filter,
                    isLoading = false,
                    overdueCount = overdue.size,
                    weekCount = thisWeek.size,
                    monthCount = thisMonth.size
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Cambia il filtro.
     */
    fun setFilter(filter: MaintenanceFilter) {
        _filter.value = filter
    }
}
