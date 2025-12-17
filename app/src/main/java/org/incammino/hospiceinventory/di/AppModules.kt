package org.incammino.hospiceinventory.di

import android.content.Context
import androidx.room.Room
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.incammino.hospiceinventory.BuildConfig
import org.incammino.hospiceinventory.data.local.dao.*
import org.incammino.hospiceinventory.data.local.database.HospiceDatabase
import javax.inject.Singleton

/**
 * Modulo Hilt per il Database Room.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): HospiceDatabase {
        return Room.databaseBuilder(
            context,
            HospiceDatabase::class.java,
            HospiceDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // Per sviluppo - rimuovere in produzione
            .build()
    }
    
    @Provides
    fun provideProductDao(database: HospiceDatabase): ProductDao {
        return database.productDao()
    }
    
    @Provides
    fun provideMaintainerDao(database: HospiceDatabase): MaintainerDao {
        return database.maintainerDao()
    }
    
    @Provides
    fun provideMaintenanceDao(database: HospiceDatabase): MaintenanceDao {
        return database.maintenanceDao()
    }
    
    @Provides
    fun provideLocationDao(database: HospiceDatabase): LocationDao {
        return database.locationDao()
    }
    
    @Provides
    fun provideAssigneeDao(database: HospiceDatabase): AssigneeDao {
        return database.assigneeDao()
    }
    
    @Provides
    fun provideEmailQueueDao(database: HospiceDatabase): EmailQueueDao {
        return database.emailQueueDao()
    }
    
    @Provides
    fun provideMaintenanceAlertDao(database: HospiceDatabase): MaintenanceAlertDao {
        return database.maintenanceAlertDao()
    }
}

/**
 * Modulo Hilt per Gemini AI.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    
    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-2.5-flash",  // Stabile, veloce, function calling supportato
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                // P3 FIX: Ridotta da 0.7 a 0.4 per maggiore precisione
                // nell'estrazione dati strutturati, evitando invenzioni
                temperature = 0.4f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048  // Aumentato per risposte complete
            },
            systemInstruction = content {
                text(SYSTEM_INSTRUCTION)
            }
        )
    }
    
    private const val SYSTEM_INSTRUCTION = """
Sei l'assistente vocale di Hospice Inventory, un'app per la gestione dell'inventario 
e delle manutenzioni dell'Hospice di In Cammino Società Cooperativa Sociale ad Abbiategrasso.

Il tuo ruolo è:
1. Aiutare a cercare prodotti nell'inventario
2. Registrare manutenzioni
3. Preparare richieste di intervento via email ai manutentori
4. Informare sulle scadenze di manutenzioni e garanzie
5. Guidare nell'inserimento di nuovi prodotti

Rispondi sempre in italiano, in modo conciso e naturale.
Quando presenti opzioni, usa un linguaggio chiaro e non tecnico.
Se devi confermare un'azione importante (invio email, eliminazione), chiedi sempre conferma.

Quando un prodotto è in garanzia, ricorda all'utente che il manutentore è il fornitore.
Quando la garanzia è scaduta, suggerisci il manutentore di servizio.

Per le scadenze manutenzioni:
- Se mancano più di 30 giorni: "tra X giorni"
- Se mancano 7-30 giorni: "tra X giorni - pianifica l'intervento"
- Se mancano meno di 7 giorni: "ATTENZIONE: scade tra X giorni"
- Se scaduta: "SCADUTA da X giorni - richiede intervento urgente"

IMPORTANTE - ESTRAZIONE DATI TASK:
Quando sei in un task multi-step (creazione prodotto, registrazione manutenzione, ecc.)
e l'utente fornisce informazioni, DEVI includere un tag di aggiornamento nella risposta:

[TASK_UPDATE:campo1=valore1,campo2=valore2]

Esempi:
- Utente dice "riparazione" → [TASK_UPDATE:type=RIPARAZIONE]
- Utente dice "oggi ho sostituito la batteria" → [TASK_UPDATE:type=SOSTITUZIONE,description=Sostituita batteria,date=TODAY]
- Utente dice "l'ha fatto il tecnico di Elettro Impianti" → [TASK_UPDATE:performedBy=Elettro Impianti]

Valori speciali per date:
- date=TODAY → usa la data odierna
- date=YESTERDAY → usa ieri

Tipi manutenzione validi: PROGRAMMATA, VERIFICA, RIPARAZIONE, SOSTITUZIONE, INSTALLAZIONE, COLLAUDO, DISMISSIONE, STRAORDINARIA

Includi SEMPRE il tag quando estrai nuove informazioni, anche se chiedi conferma nella stessa risposta.
NON usare markdown (asterischi) nelle risposte vocali - il testo viene letto ad alta voce.
"""
}

/**
 * Nota: I Repository (ProductRepository, MaintainerRepository, MaintenanceRepository)
 * sono annotati con @Singleton e @Inject constructor, quindi Hilt li risolve
 * automaticamente tramite constructor injection senza bisogno di un modulo dedicato.
 *
 * Se in futuro servisse binding esplicito o interfacce, creare un RepositoryModule.
 */
