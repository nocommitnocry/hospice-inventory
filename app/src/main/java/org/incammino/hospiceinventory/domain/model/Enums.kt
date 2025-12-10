package org.incammino.hospiceinventory.domain.model

/**
 * Frequenza manutenzioni periodiche
 */
enum class MaintenanceFrequency(val days: Int, val label: String) {
    TRIMESTRALE(90, "Trimestrale (3 mesi)"),
    SEMESTRALE(180, "Semestrale (6 mesi)"),
    ANNUALE(365, "Annuale"),
    BIENNALE(730, "Biennale (2 anni)"),
    TRIENNALE(1095, "Triennale (3 anni)"),
    QUADRIENNALE(1460, "Quadriennale (4 anni)"),
    QUINQUENNALE(1825, "Quinquennale (5 anni)"),
    CUSTOM(0, "Personalizzata");  // Usa maintenanceIntervalDays
    
    companion object {
        fun fromDays(days: Int): MaintenanceFrequency {
            return entries.find { it.days == days } ?: CUSTOM
        }
    }
}

/**
 * Tipo di proprietà del bene
 */
enum class AccountType(val label: String) {
    PROPRIETA("Proprietà"),
    NOLEGGIO("Noleggio"),
    COMODATO("Comodato d'uso"),
    LEASING("Leasing")
}

/**
 * Tipo di intervento di manutenzione
 */
enum class MaintenanceType(val label: String) {
    PROGRAMMATA("Programmata"),
    STRAORDINARIA("Straordinaria"),
    VERIFICA("Verifica/Controllo"),
    INSTALLAZIONE("Installazione"),
    DISMISSIONE("Dismissione"),
    RIPARAZIONE("Riparazione"),
    SOSTITUZIONE("Sostituzione componente")
}

/**
 * Esito dell'intervento di manutenzione
 */
enum class MaintenanceOutcome(val label: String) {
    RIPRISTINATO("Ripristinato/Funzionante"),
    PARZIALE("Parzialmente risolto"),
    GUASTO("Ancora guasto"),
    IN_ATTESA_RICAMBI("In attesa ricambi"),
    IN_ATTESA_TECNICO("In attesa tecnico"),
    DISMESSO("Dismesso"),
    SOSTITUITO("Sostituito"),
    NON_NECESSARIO("Intervento non necessario")
}

/**
 * Stato di sincronizzazione con il cloud
 */
enum class SyncStatus {
    SYNCED,    // Sincronizzato con Firebase
    PENDING,   // In attesa di sincronizzazione
    CONFLICT   // Conflitto da risolvere
}

/**
 * Stato di invio email
 */
enum class EmailStatus {
    PENDING,   // In coda
    SENT,      // Inviata con successo
    FAILED     // Invio fallito
}

/**
 * Tipo di alert per scadenze manutenzioni
 */
enum class AlertType(val daysBeforeDue: Int, val label: String) {
    ADVANCE_30(30, "30 giorni prima"),
    ADVANCE_7(7, "7 giorni prima"),
    DUE_TODAY(0, "Scadenza oggi"),
    OVERDUE(-1, "Scaduta");
    
    companion object {
        fun fromDaysRemaining(daysRemaining: Long): AlertType {
            return when {
                daysRemaining > 30 -> ADVANCE_30
                daysRemaining > 7 -> ADVANCE_30
                daysRemaining > 0 -> ADVANCE_7
                daysRemaining == 0L -> DUE_TODAY
                else -> OVERDUE
            }
        }
    }
}
