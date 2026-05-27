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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

data class Azione(var nome: String, var url: String)

class MainActivity : AppCompatActivity() {
    private val listaAzioni = mutableListOf<Azione>()
    private lateinit var adapter: AzioneAdapter
    private lateinit var btnToggleService: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configurazione Menu Hamburger
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<TextView>(R.id.btnImpostaFeedback).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mostraDialogImpostazioni()
        }

        // Configurazione Lista
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AzioneAdapter()
        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                Collections.swap(listaAzioni, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        })
        touchHelper.attachToRecyclerView(recyclerView)

        // Configurazione Bottoni
        findViewById<Button>(R.id.btnAggiungi).setOnClickListener { mostraDialogAzione(null, null) }
        findViewById<Button>(R.id.btnSalva).setOnClickListener { 
            salvaAzioni()
            Toast.makeText(this, "Lista salvata con successo!", Toast.LENGTH_SHORT).show()
        }

        btnToggleService = findViewById(R.id.btnToggleService)
        aggiornaStatoPulsanteServizio()
        btnToggleService.setOnClickListener {
            if (MediaRemoteService.isRunning) {
                stopService(Intent(this, MediaRemoteService::class.java))
            } else {
                startForegroundService(Intent(this, MediaRemoteService::class.java))
            }
            // Piccolo ritardo per permettere al servizio di aggiornare il suo stato prima di ridisegnare il bottone
            Handler(Looper.getMainLooper()).postDelayed({ aggiornaStatoPulsanteServizio() }, 200)
        }

        caricaAzioniSalvate()
    }

    override fun onResume() {
        super.onResume()
        aggiornaStatoPulsanteServizio()
    }

    private fun aggiornaStatoPulsanteServizio() {
        if (MediaRemoteService.isRunning) {
            btnToggleService.text = "Disattiva"
            btnToggleService.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444")) // Rosso
        } else {
            btnToggleService.text = "Attiva"
            btnToggleService.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981")) // Verde
        }
    }

    private fun mostraDialogImpostazioni() {
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val durataAttuale = prefs.getString("FEEDBACK_DURATION", "1500")

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_impostazioni, null)
        val etDurata = view.findViewById<TextInputEditText>(R.id.etDurataFeedback)
        etDurata.setText(durataAttuale)

        MaterialAlertDialogBuilder(this)
            .setTitle("Impostazioni")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val nuovaDurata = etDurata.text.toString().trim()
                if (nuovaDurata.isNotEmpty()) {
                    prefs.edit().putString("FEEDBACK_DURATION", nuovaDurata).apply()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun mostraDialogAzione(posizione: Int?, azioneEsistente: Azione?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_azione, null)
        val etNome = view.findViewById<TextInputEditText>(R.id.etNome)
        val etUrl = view.findViewById<TextInputEditText>(R.id.etUrl)

        if (azioneEsistente != null) {
            etNome.setText(azioneEsistente.nome)
            etUrl.setText(azioneEsistente.url)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (azioneEsistente == null) "Nuova Azione" else "Modifica Azione")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val nome = etNome.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (nome.isNotEmpty()) {
                    if (posizione != null && azioneEsistente != null) {
                        listaAzioni[posizione].nome = nome
                        listaAzioni[posizione].url = url
                        adapter.notifyItemChanged(posizione)
                    } else {
                        listaAzioni.add(Azione(nome, url))
                        adapter.notifyItemInserted(listaAzioni.size - 1)
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun caricaAzioniSalvate() {
        listaAzioni.clear()
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("LISTA_AZIONI", "[]")
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                listaAzioni.add(Azione(obj.getString("nome"), obj.getString("url")))
            }
        } catch (e: Exception) { e.printStackTrace() }
        adapter.notifyDataSetChanged()
    }

    private fun salvaAzioni() {
        val jsonArray = JSONArray()
        for (azione in listaAzioni) {
            val obj = JSONObject().apply {
                put("nome", azione.nome)
                put("url", azione.url)
            }
            jsonArray.put(obj)
        }
        getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE).edit().apply {
            putString("LISTA_AZIONI", jsonArray.toString())
            apply()
        }
    }

    inner class AzioneAdapter : RecyclerView.Adapter<AzioneAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNome: TextView = view.findViewById(R.id.tvNomeAzione)
            val btnElimina: TextView = view.findViewById(R.id.btnElimina)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_azione, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val azione = listaAzioni[position]
            holder.tvNome.text = azione.nome
            holder.itemView.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) mostraDialogAzione(pos, listaAzioni[pos])
            }
            holder.btnElimina.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listaAzioni.removeAt(pos)
                    notifyItemRemoved(pos)
                }
            }
        }
        override fun getItemCount() = listaAzioni.size
    }
}
