# WatchRemoteBot ⌚🤖

WatchRemoteBot è un'applicazione Android che trasforma il tuo smartwatch in un telecomando universale per la domotica. L'app "finge" di essere un lettore multimediale, permettendoti di lanciare azioni personalizzate su **Home Assistant**, **MQTT**, **Webhook** o **Intent Android** utilizzando semplicemente i tasti di controllo musica (Play/Pause, Next, Previous) del tuo orologio.

## ✨ Caratteristiche

- **Simulazione Media Player:** Compatibile con quasi tutti gli smartwatch (Wear OS, Tizen, etc.) che supportano i controlli musicali standard.
- **Feedback Visivo:** Il nome della traccia sullo smartwatch cambia temporaneamente in `*** AZIONE ESEGUITA ***` per confermare il comando.
- **Stato in Tempo Reale:** Mostra lo stato aggiornato dell'entità (per Home Assistant) o la conferma di invio direttamente sul display dell'orologio.
- **Feedback Tattile:** Vibrazione personalizzabile del telefono all'invio del comando.
- **Configurazione Flessibile:** Supporta molteplici tipi di azioni con parametri personalizzati.
- **Backup & Ripristino:** Esporta e importa l'intera configurazione tramite file JSON.

---

## 🚀 Come Funziona

1. **Configurazione:** Apri l'app sul telefono e aggiungi le azioni che desideri (es. "Accendi Luce Salotto").
2. **Attivazione:** Premi il pulsante **"Attiva"** nella home dell'app per avviare il servizio in background.
3. **Controllo:**
    - Usa i tasti **Next** (Avanti) e **Previous** (Indietro) sullo smartwatch per scorrere la lista delle azioni. Vedrai il nome dell'azione come titolo della traccia.
    - Premi **Play** o **Pause** per eseguire l'azione selezionata.
    - Riceverai una vibrazione sul telefono e un messaggio di conferma sull'orologio.

---

## 🛠 Scenari di Utilizzo ed Esempi

### 1. Home Assistant (Integrazione Nativa)
Ideale per controllare luci, interruttori o script.
- **Service:** `light.toggle`
- **Entity ID:** `light.salotto`
- **Data (JSON):** `{"brightness_pct": 100}` (opzionale)
- **Risultato:** Dopo la pressione, l'orologio mostrerà lo stato aggiornato (es: `Stato: on`).

### 2. MQTT (Domotica DIY / Tasmota / Shelly)
Invia comandi direttamente al tuo broker.
- **Broker:** `tcp://192.168.1.50:1883`
- **Topic:** `cmnd/tasmota_plug/POWER`
- **Payload:** `TOGGLE`

### 3. Webhook (IFTTT / Zapier / Automazioni Web)
Lancia richieste HTTP POST a servizi esterni.
- **URL:** `https://maker.ifttt.com/trigger/mio_evento/with/key/tuo_token`

### 4. Intent Android (Tasker / MacroDroid / Automate)
Comunica con altre app installate sul tuo smartphone.
- **Action:** `net.dinglisch.android.tasker.ACTION_TASK`
- **Utilizzo:** Puoi far partire macro complesse o attivare profili specifici sul telefono direttamente dal polso.

---

## ⚙️ Impostazioni Generali

Nel menu laterale puoi configurare:
- **Vibrazione:** Abilita/Disabilita la vibrazione tattile.
- **Durata Vibrazione:** Intensità della vibrazione in millisecondi.
- **Durata Feedback:** Per quanto tempo il messaggio "ESEGUITA" deve rimanere visibile sull'orologio prima di tornare al nome dell'azione (in secondi).

---

## 💾 Backup e Ripristino

Non perdere mai la tua configurazione. Usa la funzione **Backup / Ripristino** nel menu laterale per:
- **Esportare:** Salva un file `watch_remote_config.json` nella memoria del telefono o sul cloud.
- **Importare:** Ripristina istantaneamente tutte le azioni e le chiavi API su un nuovo dispositivo.

---

## 📦 Installazione

1. Clona la repository.
2. Apri il progetto con **Android Studio**.
3. Compila e installa l'APK sul tuo smartphone.
4. Assicurati di dare all'app i permessi per le notifiche e di disabilitare l'ottimizzazione batteria per un funzionamento costante in background.

---

## 📄 Licenza

Questo progetto è distribuito sotto licenza MIT. Consulta il file `LICENSE` per ulteriori dettagli.
