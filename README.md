# 🦋 Hospice Inventory

App Android voice-first per la gestione inventario e manutenzioni dell'Hospice di In Cammino Società Cooperativa Sociale.

## 📱 Caratteristiche

- **Voice-First**: 90% delle operazioni eseguibili a voce
- **Offline-Ready**: Funziona anche senza connessione
- **Scansione Barcode**: Lettura codici a barre con ML Kit
- **Gestione Garanzie**: Traccia automaticamente garanzia vs post-garanzia
- **Scadenze Manutenzioni**: Notifiche per manutenzioni periodiche
- **Email Integrate**: Richieste di intervento ai manutentori

## 🚀 Setup

### Prerequisiti

- Android Studio Hedgehog (2023.1.1) o superiore
- JDK 17
- Android SDK 35
- Nokia T21 o altro dispositivo Android 11+

### Configurazione

1. **Clona il repository**
   ```bash
   git clone [repository-url]
   cd HospiceInventory
   ```

2. **Configura le API key**
   ```bash
   cp local.properties.template local.properties
   ```
   Modifica `local.properties` inserendo:
   - `sdk.dir` = percorso Android SDK
   - `GEMINI_API_KEY` = chiave API Gemini

3. **Aggiungi Firebase**
   - Scarica `google-services.json` dalla console Firebase
   - Posizionalo in `app/google-services.json`

4. **Compila e installa**
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## 📁 Struttura Progetto

```
app/src/main/
├── java/org/incammino/hospiceinventory/
│   ├── data/
│   │   ├── local/
│   │   │   ├── dao/          # Data Access Objects (Room)
│   │   │   ├── entity/       # Entità database
│   │   │   └── database/     # Database Room
│   │   ├── remote/           # Firebase, API
│   │   └── repository/       # Repository pattern
│   ├── domain/
│   │   ├── model/            # Domain models
│   │   └── usecase/          # Business logic
│   ├── ui/
│   │   ├── theme/            # Colori, tipografia
│   │   ├── screens/          # Schermate Compose
│   │   ├── components/       # Componenti riutilizzabili
│   │   └── navigation/       # Navigazione
│   ├── service/
│   │   ├── voice/            # Speech-to-Text, TTS
│   │   ├── notification/     # Notifiche scadenze
│   │   └── sync/             # Sincronizzazione Firebase
│   └── di/                   # Hilt modules
└── res/
    ├── values/               # Strings, colors, themes
    └── xml/                  # Configurazioni
```

## 🗄️ Database

### Entità principali

- **Product**: Prodotti/asset con gestione garanzia e manutenzioni periodiche
- **Maintainer**: Manutentori (fornitori garanzia + service)
- **Maintenance**: Storico interventi
- **MaintenanceAlert**: Notifiche scadenze

### Logica Manutentore

```kotlin
// Se in garanzia → contatta warrantyMaintainer
// Se fuori garanzia → contatta serviceMaintainer
fun Product.getCurrentMaintainer(): String? {
    return if (isUnderWarranty()) {
        warrantyMaintainerId ?: serviceMaintainerId
    } else {
        serviceMaintainerId ?: warrantyMaintainerId
    }
}
```

## 🎤 Comandi Vocali

| Comando | Azione |
|---------|--------|
| "Cerca [termine]" | Ricerca prodotti |
| "Scansiona" | Attiva barcode scanner |
| "Nuovo prodotto" | Wizard inserimento |
| "Manutenzione [prodotto]" | Registra intervento |
| "Manda email a [manutentore]" | Prepara richiesta |
| "Scadenze" | Lista manutenzioni in scadenza |

## 📅 Frequenze Manutenzione

- Trimestrale (3 mesi)
- Semestrale (6 mesi)
- Annuale
- Biennale
- Triennale
- Quadriennale
- Quinquennale
- Custom (giorni personalizzati)

## 🔔 Notifiche

- **30 giorni prima**: Reminder pianificazione
- **7 giorni prima**: Alert urgente
- **Giorno stesso**: Notifica scadenza
- **Scaduta**: Reminder giornaliero

## 🔒 Sicurezza

- Autenticazione Google Workspace
- Dati cifrati in transito (TLS)
- Nessun dato sanitario (GDPR compliant)
- Wipe remoto via Google Admin

## 📊 Costi Operativi

| Servizio | Stima mensile |
|----------|---------------|
| Gemini 2.5 Flash | ~€0.70 |
| Firebase | €0 (free tier) |
| **Totale** | **< €1/mese** |

---

**In Cammino Società Cooperativa Sociale**  
Via dei Mille 8/10 - 20081 Abbiategrasso (MI)
