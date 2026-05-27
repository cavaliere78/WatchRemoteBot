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
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

class MainActivity : AppCompatActivity() {
    private val listaAzioni = mutableListOf<JSONObject>()
    private lateinit var adapter: AzioneAdapter
    private lateinit var btnToggleService: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        findViewById<TextView>(R.id.btnImpostaFeedback).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mostraDialogImpostazioni()
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
        findViewById<Button>(R.id.btnSalva).setOnClickListener { 
            salvaAzioni()
            Toast.makeText(this, "Salvato!", Toast.LENGTH_SHORT).show()
        }

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

    private fun mostraDialogImpostazioni() {
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_impostazioni, null)
        
        val etDurata = view.findViewById<TextInputEditText>(R.id.etDurataFeedback)
        val etHaUrl = view.findViewById<TextInputEditText>(R.id.etHaUrl)
        val etHaToken = view.findViewById<TextInputEditText>(R.id.etHaToken)
        val etMqttBroker = view.findViewById<TextInputEditText>(R.id.etMqttBroker)

        etDurata.setText(prefs.getString("FEEDBACK_DURATION", "1500"))
        etHaUrl.setText(prefs.getString("HA_URL", ""))
        etHaToken.setText(prefs.getString("HA_TOKEN", ""))
        etMqttBroker.setText(prefs.getString("MQTT_BROKER", ""))

        MaterialAlertDialogBuilder(this).setTitle("Impostazioni Generali").setView(view)
            .setPositiveButton("Salva") { _, _ ->
                prefs.edit().apply {
                    putString("FEEDBACK_DURATION", etDurata.text.toString().trim())
                    putString("HA_URL", etHaUrl.text.toString().trim())
                    putString("HA_TOKEN", etHaToken.text.toString().trim())
                    putString("MQTT_BROKER", etMqttBroker.text.toString().trim())
                    apply()
                }
            }.setNegativeButton("Annulla", null).show()
    }

    private fun mostraDialogAzione(posizione: Int?, azioneEsistente: JSONObject?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_azione, null)
        
        val etNome = view.findViewById<TextInputEditText>(R.id.etNome)
        val spinTipo = view.findViewById<AutoCompleteTextView>(R.id.spinTipo)
        
        val layoutWebhook = view.findViewById<LinearLayout>(R.id.layoutWebhook)
        val layoutHA = view.findViewById<LinearLayout>(R.id.layoutHA)
        val layoutMQTT = view.findViewById<LinearLayout>(R.id.layoutMQTT)
        val layoutIntent = view.findViewById<LinearLayout>(R.id.layoutIntent)

        val tipi = arrayOf("Webhook", "Home Assistant", "MQTT", "Intent (Broadcast)")
        spinTipo.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tipi))

        spinTipo.setOnItemClickListener { _, _, pos, _ ->
            layoutWebhook.visibility = if (pos == 0) View.VISIBLE else View.GONE
            layoutHA.visibility = if (pos == 1) View.VISIBLE else View.GONE
            layoutMQTT.visibility = if (pos == 2) View.VISIBLE else View.GONE
            layoutIntent.visibility = if (pos == 3) View.VISIBLE else View.GONE
        }

        // Pre-carica dati se modifica
        if (azioneEsistente != null) {
            etNome.setText(azioneEsistente.optString("nome", ""))
            val tipoSalvato = azioneEsistente.optString("tipo", "Webhook")
            spinTipo.setText(tipoSalvato, false)
            
            view.findViewById<TextInputEditText>(R.id.etUrl).setText(azioneEsistente.optString("url", ""))
            view.findViewById<TextInputEditText>(R.id.etHaService).setText(azioneEsistente.optString("ha_service", ""))
            view.findViewById<TextInputEditText>(R.id.etHaEntity).setText(azioneEsistente.optString("ha_entity", ""))
            view.findViewById<TextInputEditText>(R.id.etMqttTopic).setText(azioneEsistente.optString("mqtt_topic", ""))
            view.findViewById<TextInputEditText>(R.id.etMqttPayload).setText(azioneEsistente.optString("mqtt_payload", ""))
            view.findViewById<TextInputEditText>(R.id.etIntentAction).setText(azioneEsistente.optString("intent_action", ""))
            
            val pos = tipi.indexOf(tipoSalvato).takeIf { it >= 0 } ?: 0
            layoutWebhook.visibility = if (pos == 0) View.VISIBLE else View.GONE
            layoutHA.visibility = if (pos == 1) View.VISIBLE else View.GONE
            layoutMQTT.visibility = if (pos == 2) View.VISIBLE else View.GONE
            layoutIntent.visibility = if (pos == 3) View.VISIBLE else View.GONE
        } else {
            spinTipo.setText(tipi[0], false)
            layoutWebhook.visibility = View.VISIBLE
        }

        MaterialAlertDialogBuilder(this).setTitle(if (azioneEsistente == null) "Nuova Azione" else "Modifica Azione").setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val obj = JSONObject().apply {
                    put("nome", etNome.text.toString().trim())
                    put("tipo", spinTipo.text.toString())
                    put("url", view.findViewById<TextInputEditText>(R.id.etUrl).text.toString().trim())
                    put("ha_service", view.findViewById<TextInputEditText>(R.id.etHaService).text.toString().trim())
                    put("ha_entity", view.findViewById<TextInputEditText>(R.id.etHaEntity).text.toString().trim())
                    put("mqtt_topic", view.findViewById<TextInputEditText>(R.id.etMqttTopic).text.toString().trim())
                    put("mqtt_payload", view.findViewById<TextInputEditText>(R.id.etMqttPayload).text.toString().trim())
                    put("intent_action", view.findViewById<TextInputEditText>(R.id.etIntentAction).text.toString().trim())
                }
                if (posizione != null) { listaAzioni[posizione] = obj; adapter.notifyItemChanged(posizione) } 
                else { listaAzioni.add(obj); adapter.notifyItemInserted(listaAzioni.size - 1) }
            }.setNegativeButton("Annulla", null).show()
    }

    private fun caricaAzioniSalvate() {
        listaAzioni.clear()
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        try {
            val jsonArray = JSONArray(prefs.getString("LISTA_AZIONI", "[]"))
            for (i in 0 until jsonArray.length()) listaAzioni.add(jsonArray.getJSONObject(i))
        } catch (e: Exception) { e.printStackTrace() }
        adapter.notifyDataSetChanged()
    }

    private fun salvaAzioni() {
        val jsonArray = JSONArray()
        listaAzioni.forEach { jsonArray.put(it) }
        getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE).edit().putString("LISTA_AZIONI", jsonArray.toString()).apply()
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
