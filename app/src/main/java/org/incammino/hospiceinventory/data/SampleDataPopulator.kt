package org.incammino.hospiceinventory.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import org.incammino.hospiceinventory.data.local.dao.MaintainerDao
import org.incammino.hospiceinventory.data.local.dao.MaintenanceDao
import org.incammino.hospiceinventory.data.local.dao.ProductDao
import org.incammino.hospiceinventory.data.local.entity.MaintainerEntity
import org.incammino.hospiceinventory.data.local.entity.MaintenanceEntity
import org.incammino.hospiceinventory.data.local.entity.ProductEntity
import org.incammino.hospiceinventory.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Popola il database con dati di esempio per testing.
 * Da usare solo in ambiente di sviluppo.
 */
@Singleton
class SampleDataPopulator @Inject constructor(
    private val productDao: ProductDao,
    private val maintainerDao: MaintainerDao,
    private val maintenanceDao: MaintenanceDao
) {

    /**
     * Popola il database con dati di esempio se è vuoto.
     */
    suspend fun populateIfEmpty() {
        withContext(Dispatchers.IO) {
            val productCount = productDao.countAll()
            if (productCount > 0) return@withContext

            // Inserisci manutentori
            val maintainers = createSampleMaintainers()
            maintainers.forEach { maintainerDao.insert(it) }

            // Inserisci prodotti
            val products = createSampleProducts(maintainers)
            products.forEach { productDao.insert(it) }

            // Inserisci alcune manutenzioni
            val maintenances = createSampleMaintenances(products)
            maintenances.forEach { maintenanceDao.insert(it) }
        }
    }

    private fun createSampleMaintainers(): List<MaintainerEntity> {
        val now = Clock.System.now()
        return listOf(
            MaintainerEntity(
                id = "maint-001",
                name = "TechMed S.r.l.",
                email = "assistenza@techmed.it",
                phone = "02 1234567",
                address = "Via Milano 10",
                city = "Milano",
                postalCode = "20100",
                province = "MI",
                vatNumber = "IT12345678901",
                contactPerson = "Mario Rossi",
                specialization = "Apparecchiature elettromedicali",
                isSupplier = true,
                notes = null,
                isActive = true,
                createdAt = now,
                updatedAt = now
            ),
            MaintainerEntity(
                id = "maint-002",
                name = "ClimaService",
                email = "info@climaservice.it",
                phone = "02 9876543",
                address = "Via Roma 25",
                city = "Abbiategrasso",
                postalCode = "20081",
                province = "MI",
                vatNumber = "IT98765432109",
                contactPerson = "Luigi Bianchi",
                specialization = "Climatizzazione e refrigerazione",
                isSupplier = false,
                notes = null,
                isActive = true,
                createdAt = now,
                updatedAt = now
            ),
            MaintainerEntity(
                id = "maint-003",
                name = "Elettro Impianti",
                email = "servizio@elettroimpianti.com",
                phone = "02 5555555",
                address = "Via Garibaldi 8",
                city = "Magenta",
                postalCode = "20013",
                province = "MI",
                vatNumber = "IT11223344556",
                contactPerson = "Paolo Verdi",
                specialization = "Impianti elettrici e UPS",
                isSupplier = false,
                notes = null,
                isActive = true,
                createdAt = now,
                updatedAt = now
            ),
            MaintainerEntity(
                id = "maint-004",
                name = "MediPlus S.p.A.",
                email = "support@mediplus.it",
                phone = "800 123456",
                address = "Via dell'Industria 50",
                city = "Monza",
                postalCode = "20900",
                province = "MB",
                vatNumber = "IT99887766554",
                contactPerson = "Anna Neri",
                specialization = "Letti e ausili ospedalieri",
                isSupplier = true,
                notes = "Contratto assistenza annuale",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun createSampleProducts(maintainers: List<MaintainerEntity>): List<ProductEntity> {
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

        return listOf(
            // Prodotto con manutenzione SCADUTA
            ProductEntity(
                id = "prod-001",
                barcode = "8001234567890",
                name = "Monitor Multiparametrico Philips",
                description = "Monitor per parametri vitali - Stanza 101",
                category = "Elettromedicali",
                location = "Piano 1 - Stanza 101",
                assigneeId = null,
                warrantyMaintainerId = "maint-001",
                warrantyStartDate = today.minus(DatePeriod(years = 2)),
                warrantyEndDate = today.minus(DatePeriod(months = 6)),
                serviceMaintainerId = "maint-001",
                maintenanceFrequency = MaintenanceFrequency.SEMESTRALE,
                maintenanceStartDate = today.minus(DatePeriod(years = 1)),
                maintenanceIntervalDays = null,
                lastMaintenanceDate = today.minus(DatePeriod(months = 8)),
                nextMaintenanceDue = today.minus(DatePeriod(days = 15)), // SCADUTO
                purchaseDate = today.minus(DatePeriod(years = 2)),
                price = 4500.0,
                accountType = AccountType.PROPRIETA,
                supplier = "TechMed S.r.l.",
                invoiceNumber = "FT-2023-1234",
                imageUri = null,
                notes = "Controllare sensore SpO2",
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCED
            ),
            // Prodotto in scadenza questa settimana
            ProductEntity(
                id = "prod-002",
                barcode = "8009876543210",
                name = "Concentratore Ossigeno Inogen",
                description = "Concentratore portatile per ossigenoterapia",
                category = "Elettromedicali",
                location = "Piano 1 - Stanza 105",
                assigneeId = null,
                warrantyMaintainerId = "maint-001",
                warrantyStartDate = today.minus(DatePeriod(months = 18)),
                warrantyEndDate = today.plus(DatePeriod(months = 6)),
                serviceMaintainerId = "maint-001",
                maintenanceFrequency = MaintenanceFrequency.ANNUALE,
                maintenanceStartDate = today.minus(DatePeriod(years = 1)),
                maintenanceIntervalDays = null,
                lastMaintenanceDate = today.minus(DatePeriod(months = 11, days = 25)),
                nextMaintenanceDue = today.plus(DatePeriod(days = 5)), // 5 giorni
                purchaseDate = today.minus(DatePeriod(months = 18)),
                price = 2800.0,
                accountType = AccountType.PROPRIETA,
                supplier = "TechMed S.r.l.",
                invoiceNumber = "FT-2024-0567",
                imageUri = null,
                notes = null,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCED
            ),
            // Prodotto in scadenza questo mese
            ProductEntity(
                id = "prod-003",
                barcode = "8005555555555",
                name = "Climatizzatore Samsung",
                description = "Split 12000 BTU - Sala comune",
                category = "Climatizzazione",
                location = "Piano Terra - Sala comune",
                assigneeId = null,
                warrantyMaintainerId = null,
                warrantyStartDate = null,
                warrantyEndDate = null,
                serviceMaintainerId = "maint-002",
                maintenanceFrequency = MaintenanceFrequency.ANNUALE,
                maintenanceStartDate = today.minus(DatePeriod(years = 2)),
                maintenanceIntervalDays = null,
                lastMaintenanceDate = today.minus(DatePeriod(months = 11)),
                nextMaintenanceDue = today.plus(DatePeriod(days = 20)), // 20 giorni
                purchaseDate = today.minus(DatePeriod(years = 3)),
                price = 1200.0,
                accountType = AccountType.PROPRIETA,
                supplier = "ClimaService",
                invoiceNumber = "FT-2021-0089",
                imageUri = null,
                notes = "Filtri da sostituire alla prossima manutenzione",
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCED
            ),
            // Prodotto OK
            ProductEntity(
                id = "prod-004",
                barcode = "8007777777777",
                name = "Letto Ospedaliero Elettrico",
                description = "Letto 3 snodi con sponde - Stanza 102",
                category = "Arredi sanitari",
                location = "Piano 1 - Stanza 102",
                assigneeId = null,
                warrantyMaintainerId = "maint-004",
                warrantyStartDate = today.minus(DatePeriod(months = 6)),
                warrantyEndDate = today.plus(DatePeriod(months = 18)),
                serviceMaintainerId = "maint-004",
                maintenanceFrequency = MaintenanceFrequency.ANNUALE,
                maintenanceStartDate = today.minus(DatePeriod(months = 6)),
                maintenanceIntervalDays = null,
                lastMaintenanceDate = today.minus(DatePeriod(months = 6)),
                nextMaintenanceDue = today.plus(DatePeriod(months = 6)), // OK
                purchaseDate = today.minus(DatePeriod(months = 6)),
                price = 3500.0,
                accountType = AccountType.NOLEGGIO,
                supplier = "MediPlus S.p.A.",
                invoiceNumber = "FT-2024-2345",
                imageUri = null,
                notes = "Contratto noleggio fino a 12/2025",
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCED
            ),
            // Altro prodotto SCADUTO
            ProductEntity(
                id = "prod-005",
                barcode = "8003333333333",
                name = "UPS APC Smart 3000VA",
                description = "Gruppo di continuità sala server",
                category = "Elettrico",
                location = "Seminterrato - Sala server",
                assigneeId = null,
                warrantyMaintainerId = null,
                warrantyStartDate = null,
                warrantyEndDate = null,
                serviceMaintainerId = "maint-003",
                maintenanceFrequency = MaintenanceFrequency.SEMESTRALE,
                maintenanceStartDate = today.minus(DatePeriod(years = 1)),
                maintenanceIntervalDays = null,
                lastMaintenanceDate = today.minus(DatePeriod(months = 9)),
                nextMaintenanceDue = today.minus(DatePeriod(months = 3)), // SCADUTO da 3 mesi
                purchaseDate = today.minus(DatePeriod(years = 2)),
                price = 1800.0,
                accountType = AccountType.PROPRIETA,
                supplier = "Elettro Impianti",
                invoiceNumber = "FT-2022-0456",
                imageUri = null,
                notes = "URGENTE: batterie potrebbero essere scariche",
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCED
            ),
            // Prodotti vari per testing ricerca
            ProductEntity(
                id = "prod-006",
                barcode = null,
                name = "Frigorifero Farmaci Liebherr",
                description = "Frigorifero +2/+8 gradi per conservazione farmaci",
                category = "Elettromedicali",
                location = "Piano Terra - Ambulatorio",
                assigneeId = null,
                warrantyMaintainerId = "maint-002",
                warrantyStartDate = today.minus(DatePeriod(months = 12)),
                warrantyEndDate = today.plus(DatePeriod(months = 12)),
                serviceMaintainerId = "maint-002",
                maintenanceFrequency = MaintenanceFrequency.ANNUALE,
                maintenanceStartDate = today.minus(DatePeriod(months = 12)),
                maintenanceIntervalDays = null,
                lastMaintenanceDate = today.minus(DatePeriod(months = 1)),
                nextMaintenanceDue = today.plus(DatePeriod(months = 11)),
                purchaseDate = today.minus(DatePeriod(months = 12)),
                price = 2200.0,
                accountType = AccountType.PROPRIETA,
                supplier = "ClimaService",
                invoiceNumber = "FT-2024-0123",
                imageUri = null,
                notes = "Calibrazione termometro eseguita",
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCED
            ),
            ProductEntity(
                id = "prod-007",
                barcode = "8001111111111",
                name = "Aspiratore chirurgico",
                description = "Aspiratore per secrezioni",
                category = "Elettromedicali",
                location = "Piano 1 - Stanza 103",
                assigneeId = null,
                warrantyMaintainerId = "maint-001",
                warrantyStartDate = today.minus(DatePeriod(months = 8)),
                warrantyEndDate = today.plus(DatePeriod(months = 16)),
                serviceMaintainerId = "maint-001",
                maintenanceFrequency = MaintenanceFrequency.TRIMESTRALE,
                maintenanceStartDate = today.minus(DatePeriod(months = 8)),
                maintenanceIntervalDays = null,
                lastMaintenanceDate = today.minus(DatePeriod(months = 2)),
                nextMaintenanceDue = today.plus(DatePeriod(days = 25)),
                purchaseDate = today.minus(DatePeriod(months = 8)),
                price = 890.0,
                accountType = AccountType.PROPRIETA,
                supplier = "TechMed S.r.l.",
                invoiceNumber = "FT-2024-0789",
                imageUri = null,
                notes = null,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCED
            ),
            ProductEntity(
                id = "prod-008",
                barcode = "8002222222222",
                name = "Materasso Antidecubito",
                description = "Materasso ad aria alternata",
                category = "Arredi sanitari",
                location = "Piano 1 - Stanza 104",
                assigneeId = null,
                warrantyMaintainerId = "maint-004",
                warrantyStartDate = today.minus(DatePeriod(months = 3)),
                warrantyEndDate = today.plus(DatePeriod(months = 21)),
                serviceMaintainerId = "maint-004",
                maintenanceFrequency = MaintenanceFrequency.SEMESTRALE,
                maintenanceStartDate = today.minus(DatePeriod(months = 3)),
                maintenanceIntervalDays = null,
                lastMaintenanceDate = today.minus(DatePeriod(months = 3)),
                nextMaintenanceDue = today.plus(DatePeriod(months = 3)),
                purchaseDate = today.minus(DatePeriod(months = 3)),
                price = 1500.0,
                accountType = AccountType.COMODATO,
                supplier = "MediPlus S.p.A.",
                invoiceNumber = "FT-2024-1567",
                imageUri = null,
                notes = "Verificare pressione settimanalmente",
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    private fun createSampleMaintenances(products: List<ProductEntity>): List<MaintenanceEntity> {
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

        return listOf(
            // Manutenzione passata per prod-001
            MaintenanceEntity(
                id = "maint-rec-001",
                productId = "prod-001",
                maintainerId = "maint-001",
                date = now.minus(8.months()),
                type = MaintenanceType.PROGRAMMATA,
                outcome = MaintenanceOutcome.RIPRISTINATO,
                notes = "Sostituzione sensore SpO2, calibrazione completata",
                cost = 350.0,
                invoiceNumber = "FT-SRV-2024-001",
                isWarrantyWork = false,
                requestEmailSent = true,
                requestEmailDate = now.minus(8.months() + 7.days()),
                reportEmailSent = false,
                reportEmailDate = null,
                createdAt = now.minus(8.months()),
                updatedAt = now.minus(8.months()),
                syncStatus = SyncStatus.SYNCED
            ),
            // Manutenzione per prod-003
            MaintenanceEntity(
                id = "maint-rec-002",
                productId = "prod-003",
                maintainerId = "maint-002",
                date = now.minus(11.months()),
                type = MaintenanceType.PROGRAMMATA,
                outcome = MaintenanceOutcome.RIPRISTINATO,
                notes = "Pulizia filtri, ricarica gas refrigerante",
                cost = 180.0,
                invoiceNumber = "FT-SRV-2024-002",
                isWarrantyWork = false,
                requestEmailSent = true,
                requestEmailDate = now.minus(11.months() + 5.days()),
                reportEmailSent = false,
                reportEmailDate = null,
                createdAt = now.minus(11.months()),
                updatedAt = now.minus(11.months()),
                syncStatus = SyncStatus.SYNCED
            ),
            // Manutenzione recente per prod-006
            MaintenanceEntity(
                id = "maint-rec-003",
                productId = "prod-006",
                maintainerId = "maint-002",
                date = now.minus(1.months()),
                type = MaintenanceType.VERIFICA,
                outcome = MaintenanceOutcome.RIPRISTINATO,
                notes = "Verifica temperatura, calibrazione termometro interno",
                cost = 120.0,
                invoiceNumber = null,
                isWarrantyWork = true,
                requestEmailSent = false,
                requestEmailDate = null,
                reportEmailSent = false,
                reportEmailDate = null,
                createdAt = now.minus(1.months()),
                updatedAt = now.minus(1.months()),
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    // Helper per durate
    private fun Int.months(): kotlin.time.Duration = (this * 30).days()
    private fun Int.days(): kotlin.time.Duration = kotlin.time.Duration.parse("${this}d")
    private fun kotlin.time.Duration.plus(other: kotlin.time.Duration) = this + other

    private fun kotlinx.datetime.Instant.minus(duration: kotlin.time.Duration): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochMilliseconds(
            this.toEpochMilliseconds() - duration.inWholeMilliseconds
        )
    }
}
