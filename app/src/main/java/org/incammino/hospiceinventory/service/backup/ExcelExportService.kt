package org.incammino.hospiceinventory.service.backup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Location
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.Maintenance
import org.incammino.hospiceinventory.domain.model.Product
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service per generazione file Excel.
 * Esporta tutti i dati dell'inventario in formato .xlsx leggibile
 * per condivisione con l'amministrazione.
 */
@Singleton
class ExcelExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val productRepository: ProductRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val maintainerRepository: MaintainerRepository,
    private val locationRepository: LocationRepository
) {
    companion object {
        private const val TAG = "ExcelExportService"
    }

    /**
     * Genera Excel con tutti i dati inventario.
     * @return File Excel temporaneo pronto per l'upload
     */
    suspend fun generateInventoryExcel(): File = withContext(Dispatchers.IO) {
        val workbook = XSSFWorkbook()

        try {
            // Stile header
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.LIGHT_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                val font = workbook.createFont().apply {
                    bold = true
                    color = IndexedColors.WHITE.index
                }
                setFont(font)
            }

            // Sheet Prodotti
            createProductsSheet(workbook, headerStyle)

            // Sheet Manutenzioni
            createMaintenancesSheet(workbook, headerStyle)

            // Sheet Manutentori
            createMaintainersSheet(workbook, headerStyle)

            // Sheet Ubicazioni
            createLocationsSheet(workbook, headerStyle)

            // Salva file
            val outputFile = File(context.cacheDir, "export_temp.xlsx")
            FileOutputStream(outputFile).use { fos ->
                workbook.write(fos)
            }

            Log.i(TAG, "Excel generato: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            outputFile
        } finally {
            workbook.close()
        }
    }

    /**
     * Crea sheet Prodotti con tutte le colonne rilevanti.
     */
    private suspend fun createProductsSheet(workbook: XSSFWorkbook, headerStyle: XSSFCellStyle) {
        val sheet = workbook.createSheet("Prodotti")
        val products = productRepository.getAll().first()

        // Header
        val headers = listOf(
            "ID", "Barcode", "Nome", "Descrizione", "Categoria", "Ubicazione",
            "Fornitore", "Prezzo", "Tipo Conto", "Numero Fattura",
            "Data Acquisto", "Inizio Garanzia", "Fine Garanzia",
            "Frequenza Manutenzione", "Ultima Manutenzione", "Prossima Manutenzione",
            "Note", "Attivo"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title ->
            headerRow.createCell(index).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        // Dati
        products.forEachIndexed { index, product ->
            val row = sheet.createRow(index + 1)
            var col = 0

            row.createCell(col++).setCellValue(product.id)
            row.createCell(col++).setCellValue(product.barcode ?: "")
            row.createCell(col++).setCellValue(product.name)
            row.createCell(col++).setCellValue(product.description ?: "")
            row.createCell(col++).setCellValue(product.category)
            row.createCell(col++).setCellValue(product.location)
            row.createCell(col++).setCellValue(product.supplier ?: "")
            row.createCell(col++).setCellValue(product.price ?: 0.0)
            row.createCell(col++).setCellValue(product.accountType?.name ?: "")
            row.createCell(col++).setCellValue(product.invoiceNumber ?: "")
            row.createCell(col++).setCellValue(product.purchaseDate?.toString() ?: "")
            row.createCell(col++).setCellValue(product.warrantyStartDate?.toString() ?: "")
            row.createCell(col++).setCellValue(product.warrantyEndDate?.toString() ?: "")
            row.createCell(col++).setCellValue(product.maintenanceFrequency?.label ?: "")
            row.createCell(col++).setCellValue(product.lastMaintenanceDate?.toString() ?: "")
            row.createCell(col++).setCellValue(product.nextMaintenanceDue?.toString() ?: "")
            row.createCell(col++).setCellValue(product.notes ?: "")
            row.createCell(col).setCellValue(if (product.isActive) "Si" else "No")
        }

        // Auto-size colonne
        headers.indices.forEach { sheet.autoSizeColumn(it) }
        Log.d(TAG, "Sheet Prodotti: ${products.size} righe")
    }

    /**
     * Crea sheet Manutenzioni.
     */
    private suspend fun createMaintenancesSheet(workbook: XSSFWorkbook, headerStyle: XSSFCellStyle) {
        val sheet = workbook.createSheet("Manutenzioni")
        val maintenances = maintenanceRepository.getAll().first()

        // Header
        val headers = listOf(
            "ID", "ID Prodotto", "ID Manutentore", "Data", "Tipo",
            "Esito", "Costo", "Numero Fattura", "In Garanzia",
            "Email Richiesta", "Email Report", "Note"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title ->
            headerRow.createCell(index).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        // Dati
        maintenances.forEachIndexed { index, maintenance ->
            val row = sheet.createRow(index + 1)
            var col = 0

            val dateStr = maintenance.date
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .let { "${it.date} ${it.hour}:${it.minute.toString().padStart(2, '0')}" }

            row.createCell(col++).setCellValue(maintenance.id)
            row.createCell(col++).setCellValue(maintenance.productId)
            row.createCell(col++).setCellValue(maintenance.maintainerId ?: "")
            row.createCell(col++).setCellValue(dateStr)
            row.createCell(col++).setCellValue(maintenance.type.name)
            row.createCell(col++).setCellValue(maintenance.outcome?.name ?: "")
            row.createCell(col++).setCellValue(maintenance.cost ?: 0.0)
            row.createCell(col++).setCellValue(maintenance.invoiceNumber ?: "")
            row.createCell(col++).setCellValue(if (maintenance.isWarrantyWork) "Si" else "No")
            row.createCell(col++).setCellValue(if (maintenance.requestEmailSent) "Si" else "No")
            row.createCell(col++).setCellValue(if (maintenance.reportEmailSent) "Si" else "No")
            row.createCell(col).setCellValue(maintenance.notes ?: "")
        }

        headers.indices.forEach { sheet.autoSizeColumn(it) }
        Log.d(TAG, "Sheet Manutenzioni: ${maintenances.size} righe")
    }

    /**
     * Crea sheet Manutentori.
     */
    private suspend fun createMaintainersSheet(workbook: XSSFWorkbook, headerStyle: XSSFCellStyle) {
        val sheet = workbook.createSheet("Manutentori")
        val maintainers = maintainerRepository.getAll().first()

        // Header
        val headers = listOf(
            "ID", "Nome", "Email", "Telefono", "Indirizzo",
            "Citta", "CAP", "Provincia", "P.IVA",
            "Referente", "Specializzazione", "Fornitore", "Note", "Attivo"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title ->
            headerRow.createCell(index).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        // Dati
        maintainers.forEachIndexed { index, maintainer ->
            val row = sheet.createRow(index + 1)
            var col = 0

            row.createCell(col++).setCellValue(maintainer.id)
            row.createCell(col++).setCellValue(maintainer.name)
            row.createCell(col++).setCellValue(maintainer.email ?: "")
            row.createCell(col++).setCellValue(maintainer.phone ?: "")
            row.createCell(col++).setCellValue(maintainer.address ?: "")
            row.createCell(col++).setCellValue(maintainer.city ?: "")
            row.createCell(col++).setCellValue(maintainer.postalCode ?: "")
            row.createCell(col++).setCellValue(maintainer.province ?: "")
            row.createCell(col++).setCellValue(maintainer.vatNumber ?: "")
            row.createCell(col++).setCellValue(maintainer.contactPerson ?: "")
            row.createCell(col++).setCellValue(maintainer.specialization ?: "")
            row.createCell(col++).setCellValue(if (maintainer.isSupplier) "Si" else "No")
            row.createCell(col++).setCellValue(maintainer.notes ?: "")
            row.createCell(col).setCellValue(if (maintainer.isActive) "Si" else "No")
        }

        headers.indices.forEach { sheet.autoSizeColumn(it) }
        Log.d(TAG, "Sheet Manutentori: ${maintainers.size} righe")
    }

    /**
     * Crea sheet Ubicazioni.
     */
    private suspend fun createLocationsSheet(workbook: XSSFWorkbook, headerStyle: XSSFCellStyle) {
        val sheet = workbook.createSheet("Ubicazioni")
        val locations = locationRepository.getAll().first()

        // Header
        val headers = listOf(
            "ID", "Nome", "Tipo", "Edificio", "Piano", "Nome Piano",
            "Reparto", "Posti Letto", "Presa Ossigeno", "Indirizzo",
            "Note", "Attivo"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title ->
            headerRow.createCell(index).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        // Dati
        locations.forEachIndexed { index, location ->
            val row = sheet.createRow(index + 1)
            var col = 0

            row.createCell(col++).setCellValue(location.id)
            row.createCell(col++).setCellValue(location.name)
            row.createCell(col++).setCellValue(location.type?.label ?: "")
            row.createCell(col++).setCellValue(location.building ?: "")
            row.createCell(col++).setCellValue(location.floor ?: "")
            row.createCell(col++).setCellValue(location.floorName ?: "")
            row.createCell(col++).setCellValue(location.department ?: "")
            row.createCell(col++).setCellValue(location.bedCount?.toDouble() ?: 0.0)
            row.createCell(col++).setCellValue(if (location.hasOxygenOutlet) "Si" else "No")
            row.createCell(col++).setCellValue(location.address ?: "")
            row.createCell(col++).setCellValue(location.notes ?: "")
            row.createCell(col).setCellValue(if (location.isActive) "Si" else "No")
        }

        headers.indices.forEach { sheet.autoSizeColumn(it) }
        Log.d(TAG, "Sheet Ubicazioni: ${locations.size} righe")
    }
}
