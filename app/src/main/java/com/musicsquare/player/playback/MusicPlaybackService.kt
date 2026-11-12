package com.musicsquare.player.playback

import android.app.PendingIntent
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
        val prefs = getSharedPreferences("equalizer", MODE_PRIVATE)
        preferences = prefs
        fun updateEffects() {
            equalizer.attach(player.audioSessionId)
            equalizer.apply(prefs.getString("preset","Flat") ?: "Flat",prefs.getInt("bass",0))
        }
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { updateEffects() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playbackFailurePolicy.onReady()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Do not let a broken local item stall the queue indefinitely.
                if (playbackFailurePolicy.onFailure(player.hasNextMediaItem())) {
                    val shouldResume = player.playWhenReady
                    player.seekToNextMediaItem()
                    player.prepare()
                    if (shouldResume) player.play()
                } else {
                    player.pause()
                }
                syncWidgets(player)
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { syncWidgets(player) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { syncWidgets(player) }
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
        preferences?.let { p -> preferenceListener?.let { p.unregisterOnSharedPreferenceChangeListener(it) } }
        equalizer.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
