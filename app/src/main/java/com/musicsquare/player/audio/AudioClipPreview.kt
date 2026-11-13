package com.musicsquare.player.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Independent clip playback. Asynchronous preparation never blocks the UI thread. */
class AudioClipPreview(private val context:Context) {
    private var player:MediaPlayer?=null
    private var generation=0L

    fun stop() {
        generation++
        val old=player
        player=null
        runCatching { old?.setOnCompletionListener(null) }
        runCatching { old?.setOnPreparedListener(null) }
        runCatching { old?.setOnErrorListener(null) }
        runCatching { old?.stop() }
        runCatching { old?.release() }
    }

    suspend fun play(uri:Uri,startMs:Long,endMs:Long?,onPosition:(Long)->Unit) {
        withContext(Dispatchers.Main.immediate) {
            stop()
            require(startMs>=0 && (endMs==null || endMs>startMs))
            require(startMs<=Int.MAX_VALUE.toLong()) { "Preview offset is too large" }
            val token=generation
            val media=MediaPlayer()
            player=media
            var finished=false
            try {
                media.setDataSource(context,uri)
                suspendCancellableCoroutine<Unit> { continuation ->
                    media.setOnPreparedListener {
                        if(continuation.isActive)continuation.resume(Unit)
                    }
                    media.setOnErrorListener { _,what,extra ->
                        if(continuation.isActive)
                            continuation.resumeWithException(IllegalStateException("Cannot prepare preview ($what/$extra)"))
                        true
                    }
                    continuation.invokeOnCancellation {
                        if(player===media)stop()
                    }
                    try { media.prepareAsync() }
                    catch(e:Exception) {
                        if(continuation.isActive)continuation.resumeWithException(e)
                    }
                }
                coroutineContext.ensureActive()
                require(player===media && token==generation) { "Preview was replaced" }
                val duration=media.duration.toLong()
                require(startMs<duration) { "Start is beyond the audio duration" }
                media.setOnCompletionListener { finished=true }
                // Seek before starting so playback never begins at the wrong offset.
                suspendCancellableCoroutine<Unit> { continuation ->
                    media.setOnSeekCompleteListener {
                        if(continuation.isActive)continuation.resume(Unit)
                    }
                    continuation.invokeOnCancellation {
                        if(player===media)stop()
                    }
                    try { media.seekTo(startMs.toInt()) }
                    catch(e:Exception) {
                        if(continuation.isActive)continuation.resumeWithException(e)
                    }
                }
                coroutineContext.ensureActive()
                require(player===media && token==generation) { "Preview was replaced" }
                media.start()
                while(player===media && token==generation && !finished) {
                    coroutineContext.ensureActive()
                    val position=media.currentPosition.toLong()
                    onPosition(position)
                    if(endMs!=null && position>=endMs)break
                    delay(50)
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
