package com.example.watchremotebot

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlin.concurrent.thread

class MediaRemoteService : Service() {
    private lateinit var mediaSession: MediaSession
    private var listaAzioni = JSONArray()
    private var indiceCorrente = 0

    companion object {
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        creaNotificationChannel()
        startForeground(1, odfNotifica())
        caricaAzioni()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        caricaAzioni()
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun caricaAzioni() {
        try {
            val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
            listaAzioni = JSONArray(prefs.getString("LISTA_AZIONI", "[]"))
            indiceCorrente = 0
            aggiornaMetadati()
        } catch (e: Exception) {
            Log.e("Service", "Errore caricamento azioni", e)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "WatchRemoteBotSession")
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        
        val state = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
            .build()
        mediaSession.setPlaybackState(state)

        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { super.onPlay(); eseguiComandoCorrente() }
            override fun onPause() { super.onPause(); eseguiComandoCorrente() }
            override fun onSkipToNext() {
                super.onSkipToNext()
                if (listaAzioni.length() > 0) {
                    indiceCorrente = (indiceCorrente + 1) % listaAzioni.length()
                    vibrate()
                    aggiornaMetadati()
                }
            }
            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                if (listaAzioni.length() > 0) {
                    indiceCorrente = (indiceCorrente - 1 + listaAzioni.length()) % listaAzioni.length()
                    vibrate()
                    aggiornaMetadati()
                }
            }
        })
        mediaSession.isActive = true
    }

    private fun aggiornaMetadati() {
        if (listaAzioni.length() == 0) {
            val meta = android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "Nessuna Azione")
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Configura l'app")
                .build()
            mediaSession.setMetadata(meta)
            return
        }
        val azione = listaAzioni.getJSONObject(indiceCorrente)
        val nome = azione.optString("nome", "Senza Nome")
        val tipo = azione.optString("tipo", "")
        val meta = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, nome)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Selezionato ($tipo)")
            .build()
        mediaSession.setMetadata(meta)
    }

    private fun eseguiComandoCorrente() {
        if (listaAzioni.length() == 0) return
        val azione = listaAzioni.getJSONObject(indiceCorrente)
        val tipo = azione.optString("tipo", "")

        vibrate()

        thread {
            try {
                when (tipo) {
                    "Webhook" -> {
                        val urlStr = azione.optString("url")
                        val conn = URL(urlStr).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.responseCode
                    }
                    "Home Assistant" -> {
                        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
                        val baseUrl = prefs.getString("HA_URL", "") ?: ""
                        val token = prefs.getString("HA_TOKEN", "") ?: ""
                        val service = azione.optString("ha_service").replace(".", "/")
                        val entity = azione.optString("ha_entity")
                        val customDataStr = azione.optString("ha_data", "")

                        val conn = URL("$baseUrl/api/services/$service").openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Authorization", "Bearer $token")
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true

                        val body = JSONObject()
                        if (entity.isNotEmpty()) body.put("entity_id", entity)
                        
                        // Se l'utente ha compilato il campo JSON Data, estraiamo le chiavi e le uniamo al body
                        if (customDataStr.isNotEmpty()) {
                            try {
                                val customJson = JSONObject(customDataStr)
                                customJson.keys().forEach { key ->
                                    body.put(key, customJson.get(key))
                                }
                            } catch (e: Exception) { 
                                Log.e("Service", "Errore parsing JSON addizionale", e) 
                            }
                        }

                        conn.outputStream.use { os -> os.write(body.toString().toByteArray()) }
                        conn.responseCode
                    }
                    "MQTT" -> {
                        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
                        val broker = prefs.getString("MQTT_BROKER", "") ?: ""
                        val topic = azione.optString("mqtt_topic")
                        val payload = azione.optString("mqtt_payload")
                        if (broker.isNotEmpty() && topic.isNotEmpty()) {
                            val client = MqttClient(broker, MqttClient.generateClientId(), MemoryPersistence())
                            client.connect()
                            client.publish(topic, MqttMessage(payload.toByteArray()))
                            client.disconnect()
                        }
                    }
                    "Intent (Broadcast)" -> {
                        val actionStr = azione.optString("intent_action")
                        if (actionStr.isNotEmpty()) {
                            val intent = Intent(actionStr)
                            sendBroadcast(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Service", "Errore esecuzione comando", e)
            }
        }
    }

    private fun vibrate() {
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val duration = prefs.getString("FEEDBACK_DURATION", "1500")?.toLongOrNull() ?: 1500L
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun odfNotifica(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "WatchRemoteChannel")
            .setContentTitle("Watch Remote Attivo")
            .setContentText("Usa i controlli multimediali per attivare i comandi")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun creaNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("WatchRemoteChannel", "Watch Remote Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
