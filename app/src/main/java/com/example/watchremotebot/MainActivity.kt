package com.example.watchremotebot

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val listaAzioni = mutableListOf<JSONObject>()
    private lateinit var adapter: AzioneAdapter
    private lateinit var btnToggleService: MaterialButton
    private lateinit var drawerLayout: DrawerLayout

    private val createDocLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportConfig(it) }
    }

    private val openDocLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importConfig(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        val navView = findViewById<NavigationView>(R.id.navigationView)
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_generali -> mostraDialogGenerali()
                R.id.nav_ha -> mostraDialogHA()
                R.id.nav_mqtt -> mostraDialogMQTT()
                R.id.nav_backup -> mostraDialogBackup()
            }
            true
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AzioneAdapter()
        recyclerView.adapter = adapter

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                Collections.swap(listaAzioni, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        }).attachToRecyclerView(recyclerView)

        findViewById<Button>(R.id.btnAggiungi).setOnClickListener { mostraDialogAzione(null, null) }
        findViewById<Button>(R.id.btnSalva).setOnClickListener { salvaAzioni(); Toast.makeText(this, "Salvato!", Toast.LENGTH_SHORT).show() }

        btnToggleService = findViewById(R.id.btnToggleService)
        aggiornaStatoPulsanteServizio()
        btnToggleService.setOnClickListener {
            if (MediaRemoteService.isRunning) stopService(Intent(this, MediaRemoteService::class.java))
            else startForegroundService(Intent(this, MediaRemoteService::class.java))
            Handler(Looper.getMainLooper()).postDelayed({ aggiornaStatoPulsanteServizio() }, 200)
        }
        caricaAzioniSalvate()
    }

    override fun onResume() { super.onResume(); aggiornaStatoPulsanteServizio() }

    private fun aggiornaStatoPulsanteServizio() {
        if (MediaRemoteService.isRunning) {
            btnToggleService.text = "Disattiva"
            btnToggleService.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
        } else {
            btnToggleService.text = "Attiva"
            btnToggleService.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
        }
    }

    private fun mostraDialogGenerali() {
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_generali, null)
        
        val swVibrazione = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchVibrazione)
        val etVibra = view.findViewById<TextInputEditText>(R.id.etDurataVibrazione)
        val etFeed = view.findViewById<TextInputEditText>(R.id.etDurataFeedback)

        swVibrazione.isChecked = prefs.getBoolean("VIBRAZIONE_ATTIVA", true)
        etVibra.setText(prefs.getString("DURATA_VIBRAZIONE", "100"))
        etFeed.setText(prefs.getString("DURATA_FEEDBACK", "3"))

        MaterialAlertDialogBuilder(this)
            .setTitle("Impostazioni Generali")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                prefs.edit()
                    .putBoolean("VIBRAZIONE_ATTIVA", swVibrazione.isChecked)
                    .putString("DURATA_VIBRAZIONE", etVibra.text.toString().trim())
                    .putString("DURATA_FEEDBACK", etFeed.text.toString().trim())
                    .apply()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun mostraDialogHA() {
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 20, 60, 0) }
        val etUrl = EditText(this).apply { hint = "URL Base (es. http://192.168.1.10:8123)"; setText(prefs.getString("HA_URL", "")) }
        val etToken = EditText(this).apply { hint = "Token a lunga vita"; setText(prefs.getString("HA_TOKEN", "")); inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        layout.addView(etUrl); layout.addView(etToken)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Configurazione Home Assistant")
            .setView(layout)
            .setPositiveButton("Salva") { _, _ -> prefs.edit().putString("HA_URL", etUrl.text.toString().trim()).putString("HA_TOKEN", etToken.text.toString().trim()).apply() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun mostraDialogMQTT() {
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val input = EditText(this).apply { hint = "Broker URL (es. tcp://192.168.1.10:1883)"; setText(prefs.getString("MQTT_BROKER", "")) }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Configurazione MQTT")
            .setView(container)
            .setPositiveButton("Salva") { _, _ -> prefs.edit().putString("MQTT_BROKER", input.text.toString().trim()).apply() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun popolaListeHomeAssistant(tvStatus: TextView, actvService: AutoCompleteTextView, actvEntity: AutoCompleteTextView) {
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("HA_URL", "") ?: ""
        val token = prefs.getString("HA_TOKEN", "") ?: ""
        if (baseUrl.isEmpty() || token.isEmpty()) return

        tvStatus.visibility = View.VISIBLE
        thread {
            try {
                val connStates = URL("$baseUrl/api/states").openConnection() as HttpURLConnection
                connStates.setRequestProperty("Authorization", "Bearer $token")
                val statesArray = JSONArray(connStates.inputStream.bufferedReader().readText())
                val entities = (0 until statesArray.length()).map { statesArray.getJSONObject(it).getString("entity_id") }.sorted()

                val connServices = URL("$baseUrl/api/services").openConnection() as HttpURLConnection
                connServices.setRequestProperty("Authorization", "Bearer $token")
                val servicesArray = JSONArray(connServices.inputStream.bufferedReader().readText())
                val services = mutableListOf<String>()
                for (i in 0 until servicesArray.length()) {
                    val domainObj = servicesArray.getJSONObject(i)
                    val domain = domainObj.getString("domain")
                    val serviceMap = domainObj.getJSONObject("services")
                    serviceMap.keys().forEach { svc -> services.add("$domain.$svc") }
                }
                services.sort()

                Handler(Looper.getMainLooper()).post {
                    actvEntity.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, entities))
                    actvService.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, services))
                    tvStatus.visibility = View.GONE
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { tvStatus.text = "Errore connessione a HA" }
            }
        }
    }

    private fun mostraEsploraIntent(onSelected: (String, String, String, String) -> Unit) {
        val pm = packageManager
        val items = mutableListOf<String>()
        val dataMap = mutableMapOf<String, Triple<String, String, String>>()
        val deliveryMap = mutableMapOf<String, String>()

        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Discovery in corso...")
            .setMessage("Scansione di tutte le app installate e dei loro componenti (Activity, Service, Receiver)...")
            .setCancelable(false)
            .show()

        thread {
            try {
                // 1. Azioni di sistema comuni
                val commonActions = mapOf(
                    "Apri URL (VIEW)" to Triple(Intent.ACTION_VIEW, "", ""),
                    "Condividi (SEND)" to Triple(Intent.ACTION_SEND, "", ""),
                    "Chiama (DIAL)" to Triple(Intent.ACTION_DIAL, "", ""),
                    "Impostazioni" to Triple(android.provider.Settings.ACTION_SETTINGS, "", ""),
                    "Tasker" to Triple("net.dinglisch.android.tasker.ACTION_TASK", "", ""),
                    "MacroDroid" to Triple("com.arlosoft.macrodroid.MACRO_ACTION", "", ""),
                    "Automate" to Triple("com.llamalab.automate.intent.ACTION_START_FIBER", "", "")
                )
                commonActions.forEach { (label, data) ->
                    val entry = "🌐 [Azione] $label"
                    items.add(entry)
                    dataMap[entry] = data
                    deliveryMap[entry] = "Broadcast (Standard)"
                }

                // 2. Discovery Profonda: Scansione di tutti i pacchetti
                val packages = pm.getInstalledPackages(
                    android.content.pm.PackageManager.GET_ACTIVITIES or 
                    android.content.pm.PackageManager.GET_SERVICES or 
                    android.content.pm.PackageManager.GET_RECEIVERS
                )

                for (pkgInfo in packages) {
                    val appLabel = pkgInfo.applicationInfo.loadLabel(pm).toString()
                    val pkgName = pkgInfo.packageName

                    // Activities
                    pkgInfo.activities?.forEach { activity ->
                        if (activity.exported) {
                            val entry = "📱 [Activity] $appLabel: ${activity.name.split(".").last()}"
                            items.add(entry)
                            dataMap[entry] = Triple(Intent.ACTION_MAIN, pkgName, activity.name)
                            deliveryMap[entry] = "Avvia Activity (App)"
                        }
                    }

                    // Services
                    pkgInfo.services?.forEach { service ->
                        if (service.exported) {
                            val entry = "⚙️ [Service] $appLabel: ${service.name.split(".").last()}"
                            items.add(entry)
                            dataMap[entry] = Triple("", pkgName, service.name)
                            deliveryMap[entry] = "Avvia Servizio"
                        }
                    }

                    // Receivers
                    pkgInfo.receivers?.forEach { receiver ->
                        if (receiver.exported) {
                            val entry = "📡 [Receiver] $appLabel: ${receiver.name.split(".").last()}"
                            items.add(entry)
                            dataMap[entry] = Triple("", pkgName, receiver.name)
                            deliveryMap[entry] = "Broadcast (Standard)"
                        }
                    }
                }
                items.sort()

                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Esplora Componenti (${items.size})")
                        .setItems(items.toTypedArray()) { _, which ->
                            val selected = items[which]
                            val triple = dataMap[selected]!!
                            onSelected(triple.first, triple.second, triple.third, deliveryMap[selected]!!)
                        }
                        .show()
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Errore durante il discovery", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostraDialogAzione(posizione: Int?, azioneEsistente: JSONObject?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_azione, null)
        
        val etNome = view.findViewById<TextInputEditText>(R.id.etNome)
        val spinTipo = view.findViewById<AutoCompleteTextView>(R.id.spinTipo)
        val layoutWebhook = view.findViewById<LinearLayout>(R.id.layoutWebhook)
        val layoutHA = view.findViewById<LinearLayout>(R.id.layoutHA)
        val layoutMQTT = view.findViewById<LinearLayout>(R.id.layoutMQTT)
        val layoutIntent = view.findViewById<LinearLayout>(R.id.layoutIntent)

        val etHaService = view.findViewById<AutoCompleteTextView>(R.id.etHaService)
        val etHaEntity = view.findViewById<AutoCompleteTextView>(R.id.etHaEntity)
        val etHaData = view.findViewById<TextInputEditText>(R.id.etHaData)
        val tvHaStatus = view.findViewById<TextView>(R.id.tvHaStatus)
        
        val spinIntentDelivery = view.findViewById<AutoCompleteTextView>(R.id.spinIntentDelivery)
        val etIntentAction = view.findViewById<AutoCompleteTextView>(R.id.etIntentAction)
        val etIntentPackage = view.findViewById<TextInputEditText>(R.id.etIntentPackage)
        val etIntentClass = view.findViewById<TextInputEditText>(R.id.etIntentClass)
        val btnEsplora = view.findViewById<Button>(R.id.btnEsploraIntent)

        val tipi = arrayOf("Webhook", "Home Assistant", "MQTT", "Intent")
        spinTipo.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tipi))

        val deliveryTypes = arrayOf("Broadcast (Standard)", "Avvia Activity (App)", "Avvia Servizio")
        spinIntentDelivery.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, deliveryTypes))

        val intentComuni = arrayOf(
            "net.dinglisch.android.tasker.ACTION_TASK", 
            "com.arlosoft.macrodroid.MACRO_ACTION", 
            "com.llamalab.automate.intent.ACTION_START_FIBER",
            "android.intent.action.VIEW", 
            "android.intent.action.SEND",
            "android.intent.action.MAIN"
        )
        etIntentAction.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, intentComuni))

        btnEsplora.setOnClickListener {
            mostraEsploraIntent { action, pkg, cls, delivery ->
                if (action.isNotEmpty()) etIntentAction.setText(action, false)
                etIntentPackage.setText(pkg)
                etIntentClass.setText(cls)
                spinIntentDelivery.setText(delivery, false)
            }
        }

        var haCaricato = false

        spinTipo.setOnItemClickListener { _, _, pos, _ ->
            layoutWebhook.visibility = if (pos == 0) View.VISIBLE else View.GONE
            layoutHA.visibility = if (pos == 1) View.VISIBLE else View.GONE
            layoutMQTT.visibility = if (pos == 2) View.VISIBLE else View.GONE
            layoutIntent.visibility = if (pos == 3) View.VISIBLE else View.GONE
            
            if (pos == 1 && !haCaricato) {
                popolaListeHomeAssistant(tvHaStatus, etHaService, etHaEntity)
                haCaricato = true
            }
        }

        if (azioneEsistente != null) {
            etNome.setText(azioneEsistente.optString("nome", ""))
            val tipoSalvato = azioneEsistente.optString("tipo", "Webhook")
            spinTipo.setText(tipoSalvato, false)
            view.findViewById<TextInputEditText>(R.id.etUrl).setText(azioneEsistente.optString("url", ""))
            etHaService.setText(azioneEsistente.optString("ha_service", ""))
            etHaEntity.setText(azioneEsistente.optString("ha_entity", ""))
            etHaData.setText(azioneEsistente.optString("ha_data", ""))
            view.findViewById<TextInputEditText>(R.id.etMqttTopic).setText(azioneEsistente.optString("mqtt_topic", ""))
            view.findViewById<TextInputEditText>(R.id.etMqttPayload).setText(azioneEsistente.optString("mqtt_payload", ""))
            
            spinIntentDelivery.setText(azioneEsistente.optString("intent_delivery", deliveryTypes[0]), false)
            etIntentAction.setText(azioneEsistente.optString("intent_action", ""))
            etIntentPackage.setText(azioneEsistente.optString("intent_package", ""))
            etIntentClass.setText(azioneEsistente.optString("intent_class", ""))
            
            val pos = tipi.indexOf(tipoSalvato).takeIf { it >= 0 } ?: 0
            layoutWebhook.visibility = if (pos == 0) View.VISIBLE else View.GONE
            layoutHA.visibility = if (pos == 1) View.VISIBLE else View.GONE
            layoutMQTT.visibility = if (pos == 2) View.VISIBLE else View.GONE
            layoutIntent.visibility = if (pos == 3) View.VISIBLE else View.GONE
            
            if (pos == 1) { popolaListeHomeAssistant(tvHaStatus, etHaService, etHaEntity); haCaricato = true }
        } else {
            spinTipo.setText(tipi[0], false)
            spinIntentDelivery.setText(deliveryTypes[0], false)
            layoutWebhook.visibility = View.VISIBLE
        }

        MaterialAlertDialogBuilder(this).setTitle(if (azioneEsistente == null) "Nuova Azione" else "Modifica").setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val obj = JSONObject().apply {
                    put("nome", etNome.text.toString().trim())
                    put("tipo", spinTipo.text.toString())
                    put("url", view.findViewById<TextInputEditText>(R.id.etUrl).text.toString().trim())
                    put("ha_service", etHaService.text.toString().trim())
                    put("ha_entity", etHaEntity.text.toString().trim())
                    put("ha_data", etHaData.text.toString().trim())
                    put("mqtt_topic", view.findViewById<TextInputEditText>(R.id.etMqttTopic).text.toString().trim())
                    put("mqtt_payload", view.findViewById<TextInputEditText>(R.id.etMqttPayload).text.toString().trim())
                    
                    put("intent_delivery", spinIntentDelivery.text.toString())
                    put("intent_action", etIntentAction.text.toString().trim())
                    put("intent_package", etIntentPackage.text.toString().trim())
                    put("intent_class", etIntentClass.text.toString().trim())
                }
                if (posizione != null) { listaAzioni[posizione] = obj; adapter.notifyItemChanged(posizione) } 
                else { listaAzioni.add(obj); adapter.notifyItemInserted(listaAzioni.size - 1) }
            }.setNegativeButton("Annulla", null).show()
    }

    private fun caricaAzioniSalvate() {
        listaAzioni.clear()
        try {
            val arr = JSONArray(getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE).getString("LISTA_AZIONI", "[]"))
            for (i in 0 until arr.length()) listaAzioni.add(arr.getJSONObject(i))
        } catch (e: Exception) {}
        adapter.notifyDataSetChanged()
    }
    private fun salvaAzioni() {
        val arr = JSONArray()
        listaAzioni.forEach { arr.put(it) }
        getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE).edit().putString("LISTA_AZIONI", arr.toString()).apply()

        // Notifica il servizio se è attivo per ricaricare le azioni
        if (MediaRemoteService.isRunning) {
            val intent = Intent(this, MediaRemoteService::class.java)
            startForegroundService(intent)
        }
    }

    private fun mostraDialogBackup() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Backup / Ripristino")
            .setMessage("Esporta la configurazione in un file JSON o importane una esistente.")
            .setPositiveButton("Esporta") { _, _ -> createDocLauncher.launch("watch_remote_config.json") }
            .setNeutralButton("Importa") { _, _ -> openDocLauncher.launch(arrayOf("application/json")) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun exportConfig(uri: android.net.Uri) {
        try {
            val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
            val json = JSONObject()
            prefs.all.forEach { (key, value) -> json.put(key, value) }
            
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toString(2).toByteArray())
            }
            Toast.makeText(this, "Configurazione esportata!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Errore durante l'esportazione", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importConfig(uri: android.net.Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            val json = JSONObject(content ?: "{}")
            val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE).edit()
            
            json.keys().forEach { key ->
                when (val value = json.get(key)) {
                    is String -> prefs.putString(key, value)
                    is Boolean -> prefs.putBoolean(key, value)
                    is Int -> prefs.putInt(key, value)
                    is Long -> prefs.putLong(key, value)
                }
            }
            prefs.apply()
            caricaAzioniSalvate()
            
            // Notifica il servizio per aggiornare le azioni in memoria
            if (MediaRemoteService.isRunning) {
                startForegroundService(Intent(this, MediaRemoteService::class.java))
            }
            
            Toast.makeText(this, "Configurazione importata!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Errore durante l'importazione: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    inner class AzioneAdapter : RecyclerView.Adapter<AzioneAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNome: TextView = view.findViewById(R.id.tvNomeAzione)
            val btnElimina: TextView = view.findViewById(R.id.btnElimina)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_azione, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.tvNome.text = listaAzioni[position].optString("nome", "Senza Nome")
            holder.itemView.setOnClickListener { mostraDialogAzione(holder.adapterPosition, listaAzioni[holder.adapterPosition]) }
            holder.btnElimina.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) { listaAzioni.removeAt(pos); notifyItemRemoved(pos) }
            }
        }
        override fun getItemCount() = listaAzioni.size
    }
}
