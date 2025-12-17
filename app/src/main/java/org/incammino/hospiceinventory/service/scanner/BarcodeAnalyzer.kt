package org.incammino.hospiceinventory.service.scanner

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Analyzer CameraX per la scansione di barcode usando ML Kit.
 *
 * Supporta tutti i formati comuni:
 * - 1D: EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, Code 93, ITF
 * - 2D: QR Code, Data Matrix, PDF417, Aztec
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (Barcode) -> Unit,
    private val onError: (Exception) -> Unit = {}
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "BarcodeAnalyzer"
    }

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_ALL_FORMATS
        )
        .build()

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(options)

    // Debounce: evita scansioni multiple dello stesso codice
    private var lastScannedValue: String? = null
    private var lastScannedTime: Long = 0
    private val debounceTimeMs = 1500L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val barcode = barcodes.first()
                    val value = barcode.rawValue ?: ""
                    val now = System.currentTimeMillis()

                    // Debounce: evita di emettere lo stesso codice troppo rapidamente
                    if (value != lastScannedValue || (now - lastScannedTime) > debounceTimeMs) {
                        lastScannedValue = value
                        lastScannedTime = now
                        Log.i(TAG, "Barcode detected: $value (format: ${barcode.format})")
                        onBarcodeDetected(barcode)
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Barcode scanning failed", exception)
                onError(exception)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * Rilascia le risorse dello scanner.
     */
    fun close() {
        scanner.close()
    }
}

/**
 * Risultato della scansione barcode.
 */
data class BarcodeResult(
    val rawValue: String,
    val displayValue: String,
    val format: Int,
    val formatName: String
) {
    companion object {
        fun fromBarcode(barcode: Barcode): BarcodeResult {
            return BarcodeResult(
                rawValue = barcode.rawValue ?: "",
                displayValue = barcode.displayValue ?: barcode.rawValue ?: "",
                format = barcode.format,
                formatName = getFormatName(barcode.format)
            )
        }

        private fun getFormatName(format: Int): String = when (format) {
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_AZTEC -> "Aztec"
            else -> "Sconosciuto"
        }
    }
}
