package com.musicsquare.player.audio

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Runs FFmpeg without blocking a coroutine thread; cancels native work on job cancellation. */
object CancellableFfmpeg {
    suspend fun execute(arguments:Array<String>):FFmpegSession =
        suspendCancellableCoroutine { continuation ->
            val cancelled=AtomicBoolean(false)
            val current=AtomicReference<FFmpegSession?>(null)
            continuation.invokeOnCancellation {
                cancelled.set(true)
                current.get()?.cancel()
            }
            try {
                val session=FFmpegKit.executeWithArgumentsAsync(arguments) { completed ->
                    if(continuation.isActive)continuation.resume(completed)
                }
                current.set(session)
                if(cancelled.get())session.cancel()
            } catch(error:Exception) {
                if(continuation.isActive)continuation.resumeWith(Result.failure(error))
            }
        }
}
