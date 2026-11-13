package com.musicsquare.player.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Short-lived independent audio preview. Does not replace the main Media3 queue. */
class AudioClipPreview(private val context:Context) {
    private var player:MediaPlayer?=null
    private var generation=0

    fun stop() {
        generation++
        val old=player
        player=null
        runCatching { old?.stop() }
        runCatching { old?.release() }
    }

    suspend fun play(uri:Uri,startMs:Long,endMs:Long?,onPosition:(Long)->Unit) {
        withContext(Dispatchers.Main) {
            stop()
            require(startMs>=0 && (endMs==null || endMs>startMs))
            val token=generation
            val media=MediaPlayer()
            player=media
            try {
                media.setDataSource(context,uri)
                media.setOnCompletionListener { stop() }
                media.prepare()
                val duration=media.duration.toLong()
                require(startMs<duration) { "Start is beyond the audio duration" }
                media.seekTo(startMs.toInt())
                media.start()
                while(player===media && token==generation && media.isPlaying) {
                    coroutineContext.ensureActive()
                    val position=media.currentPosition.toLong()
                    onPosition(position)
                    if(endMs!=null && position>=endMs)break
                    delay(100)
                }
            } catch(cancelled:CancellationException) {
                throw cancelled
            } finally {
                if(player===media)stop()
                else runCatching { media.release() }
            }
        }
    }
}
