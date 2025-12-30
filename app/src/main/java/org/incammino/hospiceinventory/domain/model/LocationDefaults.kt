package org.incammino.hospiceinventory.domain.model

/**
 * Valori predefiniti per i campi ubicazione.
 * Usati come suggerimenti iniziali quando il DB è vuoto.
 */
object LocationDefaults {
    val COMMON_BUILDINGS = listOf(
        "Hospice Abbiategrasso",
        "Ala Vecchia",
        "Ala Nuova"
    )

    val COMMON_FLOORS = listOf(
        "P-1",  // Seminterrato
        "PT",   // Piano Terra
        "P1",   // Primo Piano
        "P2"    // Secondo Piano
    )

    val COMMON_FLOOR_NAMES = listOf(
        "Seminterrato",
        "Piano Terra",
        "Primo Piano",
        "Secondo Piano"
    )

    val COMMON_DEPARTMENTS = listOf(
        "Degenza",
        "Direzione",
        "Ambulatorio",
        "Day Hospital",
        "Cucina",
        "Magazzino"
    )
}
