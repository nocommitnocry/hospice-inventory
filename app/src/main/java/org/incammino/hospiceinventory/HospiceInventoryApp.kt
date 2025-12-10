package org.incammino.hospiceinventory

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class principale per Hospice Inventory.
 * Inizializza Hilt per la dependency injection e configura WorkManager.
 */
@HiltAndroidApp
class HospiceInventoryApp : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    /**
     * Configurazione WorkManager con HiltWorkerFactory.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    /**
     * Crea i canali di notifica necessari per Android 8.0+.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Canale per alert manutenzioni
            val maintenanceChannel = NotificationChannel(
                CHANNEL_MAINTENANCE_ALERTS,
                "Scadenze Manutenzioni",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche per manutenzioni in scadenza o scadute"
                enableVibration(true)
                enableLights(true)
            }
            
            // Canale per sync
            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                "Sincronizzazione",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifiche sullo stato della sincronizzazione"
            }
            
            // Canale per garanzie
            val warrantyChannel = NotificationChannel(
                CHANNEL_WARRANTY_ALERTS,
                "Scadenze Garanzie",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifiche per garanzie in scadenza"
            }
            
            notificationManager.createNotificationChannels(
                listOf(maintenanceChannel, syncChannel, warrantyChannel)
            )
        }
    }
    
    companion object {
        const val CHANNEL_MAINTENANCE_ALERTS = "maintenance_alerts"
        const val CHANNEL_SYNC = "sync"
        const val CHANNEL_WARRANTY_ALERTS = "warranty_alerts"
    }
}
