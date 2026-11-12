package com.musicsquare.player.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musicsquare.player.MainActivity
import com.musicsquare.player.R
import com.musicsquare.player.playback.MusicPlaybackService

/** Home screen widgets share one playback session and never instantiate a second player. */
abstract class MusicWidgetProvider(private val layoutId: Int) : AppWidgetProvider() {
    companion object {
        private const val ACTION_PREFIX = "com.musicsquare.player.widgets."
        private const val PREVIOUS = ACTION_PREFIX + "PREVIOUS"
        private const val TOGGLE = ACTION_PREFIX + "TOGGLE"
        private const val NEXT = ACTION_PREFIX + "NEXT"
        fun refreshAll(context: Context, title: String?, playing: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            for ((klass, layout) in listOf(
                Widget1x4::class.java to R.layout.widget_music_compact,
                Widget2x4::class.java to R.layout.widget_music_medium,
                Widget4x4::class.java to R.layout.widget_music_large
            )) {
                val component = ComponentName(context, klass)
                manager.getAppWidgetIds(component).forEach { id ->
                    val views = RemoteViews(context.packageName, layout)
                    views.setTextViewText(R.id.widget_title, title ?: "MusicSquare")
                    views.setImageViewResource(R.id.widget_play,
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                    fun action(viewId: Int, command: String, code: Int) {
                        val intent = Intent(context, klass).setAction(command)
                        views.setOnClickPendingIntent(viewId, PendingIntent.getBroadcast(
                            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    }
                    action(R.id.widget_prev, PREVIOUS, id * 10 + 1)
                    action(R.id.widget_play, TOGGLE, id * 10 + 2)
                    action(R.id.widget_next, NEXT, id * 10 + 3)
                    val open = Intent(context, MainActivity::class.java)
                    views.setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(
                        context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        refreshAll(context, context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
            .getString("title", null), context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
            .getBoolean("playing", false))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in setOf(PREVIOUS, TOGGLE, NEXT)) return
        val pending = goAsync()
        val appContext = context.applicationContext
        try {
            val token = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            future.addListener({
                try {
                    val controller = future.get()
                    when (action) {
                        PREVIOUS -> controller.seekToPreviousMediaItem()
                        NEXT -> controller.seekToNextMediaItem()
                        TOGGLE -> if (controller.isPlaying) controller.pause() else controller.play()
                    }
                    refreshAll(appContext, controller.currentMediaItem?.mediaMetadata?.title?.toString(), controller.isPlaying)
                } catch (e: Exception) {
                    android.util.Log.e("MusicSquareWidget", "Widget playback failed", e)
                } finally {
                    MediaController.releaseFuture(future)
                    pending.finish()
                }
            }, ContextCompat.getMainExecutor(appContext))
        } catch (e: Exception) {
            android.util.Log.e("MusicSquareWidget", "Unable to connect widget to session", e)
            pending.finish()
        }
    }
}
class Widget1x4 : MusicWidgetProvider(R.layout.widget_music_compact)
class Widget2x4 : MusicWidgetProvider(R.layout.widget_music_medium)
class Widget4x4 : MusicWidgetProvider(R.layout.widget_music_large)
