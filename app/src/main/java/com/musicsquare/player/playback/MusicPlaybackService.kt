package com.musicsquare.player.playback

import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicsquare.player.MainActivity
import com.musicsquare.player.audio.EqualizerManager
import android.content.SharedPreferences
import androidx.media3.common.Player
import com.musicsquare.player.widgets.MusicWidgetProvider

class MusicPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val timerHandler=Handler(Looper.getMainLooper())
    private var timerRunnable:Runnable?=null
    private var timerPreferences:SharedPreferences?=null
    private var timerListener:SharedPreferences.OnSharedPreferenceChangeListener?=null
    private fun scheduleSleepTimer(player:Player) {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable=null
        val prefs=timerPreferences ?: return
        val deadline=prefs.getLong("deadline_elapsed",0L)
        if(deadline<=0L)return
        val remaining=deadline-SystemClock.elapsedRealtime()
        if(remaining<=0L) {
            player.pause()
            prefs.edit().remove("deadline_elapsed").apply()
            return
        }
        val action=Runnable {
            player.pause()
            prefs.edit().remove("deadline_elapsed").apply()
        }
        timerRunnable=action
        timerHandler.postDelayed(action,remaining)
    }

    private val equalizer = EqualizerManager()
    private val playbackFailurePolicy = PlaybackFailurePolicy()
    private var preferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        }
        val sleepPrefs=getSharedPreferences("sleep_timer",MODE_PRIVATE)
        timerPreferences=sleepPrefs
        timerListener=SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
            if(key=="deadline_elapsed")scheduleSleepTimer(player)
        }
        sleepPrefs.registerOnSharedPreferenceChangeListener(timerListener)
        scheduleSleepTimer(player)
        val prefs = getSharedPreferences("equalizer", MODE_PRIVATE)
        preferences = prefs
        fun updateEffects() {
            // OEM audio effect implementations can throw even on valid sessions.
            // EQ failure must never interrupt playback.
            runCatching {
                equalizer.attach(player.audioSessionId)
                equalizer.apply(prefs.getString("preset","Flat") ?: "Flat",prefs.getInt("bass",0))
            }.onFailure { android.util.Log.w("MusicSquareEffects", "EQ unavailable", it) }
        }
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { updateEffects() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playbackFailurePolicy.onReady()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("MusicSquarePlayback", "Playback failed: ${error.errorCodeName}", error)
                // A bad file must not crash the MediaSession service or loop forever.
                try {
                    val shouldResume = player.playWhenReady
                    if (playbackFailurePolicy.onFailure(player.hasNextMediaItem())) {
                        player.seekToNextMediaItem()
                        player.prepare()
                        if (shouldResume) player.play()
                    } else {
                        player.pause()
                    }
                } catch (recoveryError: Exception) {
                    android.util.Log.e("MusicSquarePlayback", "Playback recovery failed", recoveryError)
                    runCatching { player.pause() }
                }
                runCatching { syncWidgets(player) }
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { runCatching { syncWidgets(player) } }
            override fun onIsPlayingChanged(isPlaying: Boolean) { runCatching { syncWidgets(player) } }
        })
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> updateEffects() }
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        updateEffects()
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(pending).build()
    }
    private fun syncWidgets(player: Player) {
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString()
        getSharedPreferences("widget_state", MODE_PRIVATE).edit()
            .putString("title", title).putBoolean("playing", player.isPlaying).apply()
        MusicWidgetProvider.refreshAll(this, title, player.isPlaying)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable=null
        timerPreferences?.let { prefs -> timerListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) } }
        preferences?.let { p -> preferenceListener?.let { p.unregisterOnSharedPreferenceChangeListener(it) } }
        runCatching { equalizer.release() }
        session?.let { active ->
            try {
                active.player.release()
            } catch (error: Exception) {
                android.util.Log.w("MusicSquarePlayback", "Player release failed", error)
            } finally {
                runCatching { active.release() }
            }
        }
        session = null
        super.onDestroy()
    }
}
