package org.incammino.hospiceinventory.ui.screens.voice

import org.incammino.hospiceinventory.service.voice.MaintenanceConfirmData

/**
 * Holder temporaneo per passare i dati di conferma tra screen.
 * Usato perché MaintenanceConfirmData contiene classi complesse
 * (Product, Maintainer) che non sono facilmente serializzabili
 * per SavedStateHandle.
 *
 * Il dato viene consumato (nullificato) dopo la lettura.
 */
object MaintenanceDataHolder {
    private var data: MaintenanceConfirmData? = null

    fun set(confirmData: MaintenanceConfirmData) {
        data = confirmData
    }

    fun consume(): MaintenanceConfirmData? {
        val result = data
        data = null
        return result
    }

    fun peek(): MaintenanceConfirmData? = data

    fun clear() {
        data = null
    }
}
