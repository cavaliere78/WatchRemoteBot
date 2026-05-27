package com.example.watchremotebot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MediaRemoteService : MediaBrowserServiceCompat() {
    companion object { var isRunning = false }
    private lateinit var mediaSession: MediaSessionCompat
    private var listaAzioni = ArrayList<JSONObject>()
    private var indiceCorrente = 0
    private val handlerFeedback = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        mediaSession = MediaSessionCompat(this, "MediaRemoteService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onSkipToNext() { cambiaIndice(1) }
                override fun onSkipToPrevious() { cambiaIndice(-1) }
                override fun onPlay() { eseguiComandoCorrente() }
                override fun onPause() { eseguiComandoCorrente() }
            })
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
        creaNotificationChannel()
        startForeground(101, generaNotification())
    }

    private fun cambiaIndice(delta: Int) {
        if (listaAzioni.isNotEmpty()) {
            indiceCorrente = (indiceCorrente + delta + listaAzioni.size) % listaAzioni.size
            notificaCambioStato()
            aggiornaSchermoOrologio(listaAzioni[indiceCorrente].optString("nome", "Azione"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        listaAzioni.clear()
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        try {
            val jsonArray = JSONArray(prefs.getString("LISTA_AZIONI", "[]"))
            for (i in 0 until jsonArray.length()) listaAzioni.add(jsonArray.getJSONObject(i))
        } catch (e: Exception) { e.printStackTrace() }
        indiceCorrente = 0
        notificaCambioStato()
        aggiornaSchermoOrologio(if (listaAzioni.isNotEmpty()) listaAzioni[0].optString("nome", "Azione") else "Nessuna Azione")
        return super.onStartCommand(intent, flags, startId)
    }

    private fun notificaCambioStato() {
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f).build())
    }

    private fun aggiornaSchermoOrologio(testo: String) {
        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, testo)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "WatchRemote (${indiceCorrente + 1}/${listaAzioni.size})")
            .build())
    }

    private fun eseguiComandoCorrente() {
        if (listaAzioni.isEmpty()) return
        val azione = listaAzioni[indiceCorrente]
        val nome = azione.optString("nome", "Azione")
        val tipo = azione.optString("tipo", "Webhook") // Retrocompatibilità

        // Gestione feedback visivo
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val durata = prefs.getString("FEEDBACK_DURATION", "1500")?.toLongOrNull() ?: 1500L
        aggiornaSchermoOrologio("*** Eseguito: $nome ***")
        handlerFeedback.removeCallbacksAndMessages(null)
        handlerFeedback.postDelayed({ aggiornaSchermoOrologio(nome) }, durata)

        // Esecuzione Ecosistema
        thread {
            try {
                when (tipo) {
                    "Webhook" -> {
                        val urlStr = azione.optString("url", "")
                        if (urlStr.isNotEmpty()) {
                            val conn = URL(urlStr).openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.responseCode
                            conn.disconnect()
                        }
                    }
                    "Home Assistant" -> {
                        val baseUrl = prefs.getString("HA_URL", "") ?: ""
                        val token = prefs.getString("HA_TOKEN", "") ?: ""
                        val service = azione.optString("ha_service", "").replace(".", "/")
                        val entity = azione.optString("ha_entity", "")
                        
                        if (baseUrl.isNotEmpty() && service.isNotEmpty()) {
                            val conn = URL("$baseUrl/api/services/$service").openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Authorization", "Bearer $token")
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.doOutput = true
                            conn.outputStream.write("{\"entity_id\": \"$entity\"}".toByteArray())
                            conn.responseCode
                            conn.disconnect()
                        }
                    }
                    "MQTT" -> {
                        val broker = prefs.getString("MQTT_BROKER", "") ?: ""
                        val topic = azione.optString("mqtt_topic", "")
                        val payload = azione.optString("mqtt_payload", "")
                        if (broker.isNotEmpty() && topic.isNotEmpty()) {
                            val client = MqttClient(broker, MqttClient.generateClientId(), MemoryPersistence())
                            client.connect()
                            client.publish(topic, MqttMessage(payload.toByteArray()))
                            client.disconnect()
                        }
                    }
                    "Intent (Broadcast)" -> {
                        val actionString = azione.optString("intent_action", "")
                        if (actionString.isNotEmpty()) {
                            val broadcastIntent = Intent(actionString)
                            sendBroadcast(broadcastIntent)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun generaNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "watch_remote_channel")
            .setContentTitle("Telecomando Multi-Action Watch").setContentText("In esecuzione...")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pendingIntent).build()
    }

    private fun creaNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("watch_remote_channel", "Watch Remote", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onGetRoot(c: String, u: Int, r: Bundle?): BrowserRoot = BrowserRoot("root", null)
    override fun onLoadChildren(p: String, r: Result<List<MediaBrowserCompat.MediaItem>>) = r.sendResult(null)
    override fun onDestroy() { isRunning = false; mediaSession.release(); super.onDestroy() }
}
