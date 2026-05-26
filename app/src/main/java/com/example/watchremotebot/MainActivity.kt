package com.example.watchremotebot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var containerAzioni: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        rootLayout.addView(TextView(this).apply { 
            text = "Configurazione Azioni WatchRemote"
            textSize = 20f
            setPadding(0, 0, 0, 30)
        })

        // ScrollView per gestire tante azioni a schermo senza bloccare la vista
        val scrollView = ScrollView(this)
        containerAzioni = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(containerAzioni)
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Carica le azioni precedentemente salvate
        caricaAzioniSalvate()

        // Pulsante per aggiungere una riga dinamicamente
        val btnAggiungi = Button(this).apply {
            text = "+ Aggiungi Azione"
            setOnClickListener { aggiungiRigaAzione("", "") }
        }
        rootLayout.addView(btnAggiungi)

        // Pulsante per salvare e avviare
        val btnSalva = Button(this).apply {
            text = "Salva e Attiva Telecomando"
            setOnClickListener {
                salvaAzioni()
                val intent = Intent(this@MainActivity, MediaRemoteService::class.java)
                startForegroundService(intent)
            }
        }
        rootLayout.addView(btnSalva)

        setContentView(rootLayout)
    }

    private fun aggiungiRigaAzione(nome: String, url: String) {
        val riga = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }

        val etNome = EditText(this).apply { 
            hint = "Nome Azione (es. Accendi TV)"
            setText(nome)
        }
        val etUrl = EditText(this).apply { 
            hint = "URL Webhook HTTP"
            setText(url)
        }

        val btnRimuovi = Button(this).apply {
            text = "Elimina questa"
            textSize = 12f
            setOnClickListener { containerAzioni.removeView(riga) }
        }

        riga.addView(etNome)
        riga.addView(etUrl)
        riga.addView(btnRimuovi)
        
        // Separatore visivo
        val linea = View(this).apply {
            setBackgroundColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 10, 0, 10) }
        }
        riga.addView(linea)

        containerAzioni.addView(riga)
    }

    private fun caricaAzioniSalvate() {
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("LISTA_AZIONI", "[]")
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                aggiungiRigaAzione(obj.getString("nome"), obj.getString("url"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Se non ci sono azioni salvate, aggiungine almeno una vuota di default
        if (containerAzioni.childCount == 0) {
            aggiungiRigaAzione("Azione 1", "http://")
        }
    }

    private fun salvaAzioni() {
        val jsonArray = JSONArray()
        for (i in 0 until containerAzioni.childCount) {
            val riga = containerAzioni.getChildAt(i) as? LinearLayout ?: continue
            val etNome = riga.getChildAt(0) as? EditText ?: continue
            val etUrl = riga.getChildAt(1) as? EditText ?: continue

            val nome = etNome.text.toString().trim()
            val url = etUrl.text.toString().trim()

            if (nome.isNotEmpty()) {
                val obj = JSONObject().apply {
                    put("nome", nome)
                    put("url", url)
                }
                jsonArray.put(obj)
            }
        }

        getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE).edit().apply {
            putString("LISTA_AZIONI", jsonArray.toString())
            apply()
        }
    }
}
