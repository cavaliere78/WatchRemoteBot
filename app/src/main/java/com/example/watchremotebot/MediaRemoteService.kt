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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MediaRemoteService : MediaBrowserServiceCompat() {
    companion object {
        var isRunning = false
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val CHANNEL_ID = "watch_remote_channel"
    private val NOTIFICATION_ID = 101
    
    private var listaAzioni = ArrayList<JSONObject>()
    private var indiceCorrente = 0
    private val handlerFeedback = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        mediaSession = MediaSessionCompat(this, "MediaRemoteService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onSkipToNext() {
                    if (listaAzioni.isNotEmpty()) {
                        indiceCorrente = (indiceCorrente + 1) % listaAzioni.size
                        notificaCambioStato()
                        aggiornaSchermoOrologio()
                    }
                }
                override fun onSkipToPrevious() {
                    if (listaAzioni.isNotEmpty()) {
                        indiceCorrente = if (indiceCorrente - 1 < 0) listaAzioni.size - 1 else indiceCorrente - 1
                        notificaCambioStato()
                        aggiornaSchermoOrologio()
                    }
                }
                override fun onPlay() { eseguiComandoCorrente() }
                override fun onPause() { eseguiComandoCorrente() }
            })
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
        creaNotificationChannel()
        startForeground(NOTIFICATION_ID, generaNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        caricaAzioniDaMemoria()
        indiceCorrente = 0
        notificaCambioStato()
        aggiornaSchermoOrologio()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun caricaAzioniDaMemoria() {
        listaAzioni.clear()
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("LISTA_AZIONI", "[]")
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                listaAzioni.add(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun notificaCambioStato() {
        val state = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
            .build()
        mediaSession.setPlaybackState(state)
    }

    private fun aggiornaSchermoOrologio() {
        val titoloTraccia = if (listaAzioni.isNotEmpty() && indiceCorrente < listaAzioni.size) {
            listaAzioni[indiceCorrente].getString("nome")
        } else {
            "Nessuna Azione"
        }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titoloTraccia)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Selezionato (${indiceCorrente + 1}/${listaAzioni.size})")
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun eseguiComandoCorrente() {
        if (listaAzioni.isEmpty() || indiceCorrente >= listaAzioni.size) return

        val azioneObj = listaAzioni[indiceCorrente]
        val nome = azioneObj.getString("nome")
        val urlString = azioneObj.getString("url")

        val metadataFeedback = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "*** Azione $nome eseguita ***")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "WatchRemoteBot")
            .build()
        mediaSession.setMetadata(metadataFeedback)

        // LEGGE IL PARAMETRO DALLE IMPOSTAZIONI O USA 1500ms DI DEFAULT
        val prefs = getSharedPreferences("RemotePrefs", Context.MODE_PRIVATE)
        val durata = prefs.getString("FEEDBACK_DURATION", "1500")?.toLongOrNull() ?: 1500L

        handlerFeedback.removeCallbacksAndMessages(null)
        handlerFeedback.postDelayed({ aggiornaSchermoOrologio() }, durata)

        if (!urlString.isNullOrEmpty() && urlString.startsWith("http")) {
            thread {
                try {
                    val url = URL(urlString)
                    val connessione = url.openConnection() as HttpURLConnection
                    connessione.requestMethod = "POST" 
                    connessione.connectTimeout = 5000
                    connessione.responseCode
                    connessione.disconnect()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun generaNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telecomando Multi-Action Watch")
            .setContentText("Il telecomando domotico è attivo")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent).build()
    }

    private fun creaNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Watch Remote Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? { return BrowserRoot("root", null) }
    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) { result.sendResult(null) }
    
    override fun onDestroy() { 
        isRunning = false
        handlerFeedback.removeCallbacksAndMessages(null)
        mediaSession.release()
        super.onDestroy() 
    }
}
