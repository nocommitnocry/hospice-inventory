package org.incammino.hospiceinventory.ui.screens.maintainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.domain.model.Maintainer
import java.util.UUID
import javax.inject.Inject

/**
 * UI State per MaintainerEditScreen.
 */
data class MaintainerEditUiState(
    // Dati principali
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val specialization: String = "",

    // Indirizzo
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
    val province: String = "",

    // Dati fiscali
    val vatNumber: String = "",

    // Referente
    val contactPerson: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",

    // Opzioni
    val isSupplier: Boolean = false,
    val notes: String = "",

    // Metadati
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMaintainerId: String? = null
)

/**
 * ViewModel per la creazione/modifica manutentore.
 */
@HiltViewModel
class MaintainerEditViewModel @Inject constructor(
    private val maintainerRepository: MaintainerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val maintainerId: String? = savedStateHandle.get<String>("maintainerId")
        ?.takeIf { it != "new" }

    private val _uiState = MutableStateFlow(MaintainerEditUiState(isNew = maintainerId == null))
    val uiState: StateFlow<MaintainerEditUiState> = _uiState.asStateFlow()

    init {
        if (maintainerId != null) {
            loadMaintainer(maintainerId)
        }
    }

    /**
     * Carica il manutentore esistente per la modifica.
     */
    private fun loadMaintainer(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            maintainerRepository.getByIdFlow(id).collect { maintainer ->
                if (maintainer != null) {
                    _uiState.update {
                        it.copy(
                            name = maintainer.name,
                            email = maintainer.email ?: "",
                            phone = maintainer.phone ?: "",
                            specialization = maintainer.specialization ?: "",
                            address = maintainer.address ?: "",
                            city = maintainer.city ?: "",
                            postalCode = maintainer.postalCode ?: "",
                            province = maintainer.province ?: "",
                            vatNumber = maintainer.vatNumber ?: "",
                            contactPerson = maintainer.contactPerson ?: "",
                            // contactPhone e contactEmail non sono nel domain model, li lasciamo vuoti
                            isSupplier = maintainer.isSupplier,
                            notes = maintainer.notes ?: "",
                            isNew = false,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Manutentore non trovato"
                        )
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AGGIORNAMENTO CAMPI
    // ═══════════════════════════════════════════════════════════════════════════

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun updatePhone(value: String) {
        _uiState.update { it.copy(phone = value) }
    }

    fun updateSpecialization(value: String) {
        _uiState.update { it.copy(specialization = value) }
    }

    fun updateAddress(value: String) {
        _uiState.update { it.copy(address = value) }
    }

    fun updateCity(value: String) {
        _uiState.update { it.copy(city = value) }
    }

    fun updatePostalCode(value: String) {
        _uiState.update { it.copy(postalCode = value) }
    }

    fun updateProvince(value: String) {
        _uiState.update { it.copy(province = value) }
    }

    fun updateVatNumber(value: String) {
        _uiState.update { it.copy(vatNumber = value) }
    }

    fun updateContactPerson(value: String) {
        _uiState.update { it.copy(contactPerson = value) }
    }

    fun updateContactPhone(value: String) {
        _uiState.update { it.copy(contactPhone = value) }
    }

    fun updateContactEmail(value: String) {
        _uiState.update { it.copy(contactEmail = value) }
    }

    fun updateIsSupplier(value: Boolean) {
        _uiState.update { it.copy(isSupplier = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDAZIONE E SALVATAGGIO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Valida il form.
     */
    private fun validate(): Boolean {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Il nome/ragione sociale è obbligatorio") }
            return false
        }

        // Validazione email se presente
        if (state.email.isNotBlank() && !isValidEmail(state.email)) {
            _uiState.update { it.copy(error = "Formato email non valido") }
            return false
        }

        // Validazione P.IVA se presente (11 cifre)
        if (state.vatNumber.isNotBlank() && !isValidVatNumber(state.vatNumber)) {
            _uiState.update { it.copy(error = "Partita IVA non valida (11 cifre)") }
            return false
        }

        return true
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidVatNumber(vatNumber: String): Boolean {
        val cleaned = vatNumber.filter { it.isDigit() }
        return cleaned.length == 11
    }

    /**
     * Salva il manutentore.
     */
    fun save() {
        if (!validate()) return

        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val maintainer = Maintainer(
                    id = maintainerId ?: UUID.randomUUID().toString(),
                    name = state.name.trim(),
                    email = state.email.takeIf { it.isNotBlank() },
                    phone = state.phone.takeIf { it.isNotBlank() },
                    address = state.address.takeIf { it.isNotBlank() },
                    city = state.city.takeIf { it.isNotBlank() },
                    postalCode = state.postalCode.takeIf { it.isNotBlank() },
                    province = state.province.takeIf { it.isNotBlank() },
                    vatNumber = state.vatNumber.takeIf { it.isNotBlank() },
                    contactPerson = state.contactPerson.takeIf { it.isNotBlank() },
                    specialization = state.specialization.takeIf { it.isNotBlank() },
                    isSupplier = state.isSupplier,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    isActive = true
                )

                val id = if (state.isNew) {
                    maintainerRepository.insert(maintainer)
                } else {
                    maintainerRepository.update(maintainer)
                    maintainer.id
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedMaintainerId = id
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "Errore durante il salvataggio: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Pulisce l'errore.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
