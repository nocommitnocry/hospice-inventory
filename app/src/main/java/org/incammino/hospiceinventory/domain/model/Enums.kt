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
 * Tipo di intervento di manutenzione.
 *
 * @property label Nome visualizzato
 * @property displayName Nome esteso per prompt vocali
 * @property metaCategory Categoria superiore (ordinaria/straordinaria/lifecycle)
 * @property synonyms Sinonimi per il matching vocale
 */
enum class MaintenanceType(
    val label: String,
    val displayName: String,
    val metaCategory: MetaCategory,
    val synonyms: List<String>
) {
    PROGRAMMATA(
        label = "Programmata",
        displayName = "Manutenzione programmata",
        metaCategory = MetaCategory.ORDINARIA,
        synonyms = listOf("programmata", "periodica", "schedulata", "prevista")
    ),
    VERIFICA(
        label = "Verifica/Controllo",
        displayName = "Verifica periodica",
        metaCategory = MetaCategory.ORDINARIA,
        synonyms = listOf("verifica", "controllo", "check", "ispezione", "sopralluogo")
    ),
    RIPARAZIONE(
        label = "Riparazione",
        displayName = "Riparazione",
        metaCategory = MetaCategory.STRAORDINARIA,
        synonyms = listOf("riparazione", "riparato", "aggiustato", "sistemato", "riparare", "aggiustare")
    ),
    SOSTITUZIONE(
        label = "Sostituzione componente",
        displayName = "Sostituzione",
        metaCategory = MetaCategory.STRAORDINARIA,
        synonyms = listOf("sostituzione", "sostituito", "cambiato", "rimpiazzato", "sostituire", "cambiare")
    ),
    INSTALLAZIONE(
        label = "Installazione",
        displayName = "Installazione",
        metaCategory = MetaCategory.LIFECYCLE,
        synonyms = listOf("installazione", "installato", "montato", "messo", "installare", "montare")
    ),
    COLLAUDO(
        label = "Collaudo",
        displayName = "Collaudo",
        metaCategory = MetaCategory.LIFECYCLE,
        synonyms = listOf("collaudo", "collaudato", "test iniziale", "prima verifica")
    ),
    DISMISSIONE(
        label = "Dismissione",
        displayName = "Dismissione",
        metaCategory = MetaCategory.LIFECYCLE,
        synonyms = listOf("dismissione", "dismesso", "smontato", "buttato", "rimosso", "smantellato")
    ),
    STRAORDINARIA(
        label = "Straordinaria",
        displayName = "Intervento straordinario",
        metaCategory = MetaCategory.STRAORDINARIA,
        synonyms = listOf("straordinaria", "urgente", "emergenza", "imprevisto")
    );

    /**
     * Meta-categoria per raggruppamento tipi manutenzione.
     */
    enum class MetaCategory(val label: String) {
        /** Manutenzioni regolari pianificate */
        ORDINARIA("Ordinaria"),
        /** Interventi non pianificati (guasti, emergenze) */
        STRAORDINARIA("Straordinaria"),
        /** Eventi del ciclo di vita del prodotto */
        LIFECYCLE("Ciclo di vita")
    }

    companion object {
        /**
         * Trova tutti i tipi di una meta-categoria.
         */
        fun byMetaCategory(metaCategory: MetaCategory): List<MaintenanceType> {
            return entries.filter { it.metaCategory == metaCategory }
        }
    }
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
