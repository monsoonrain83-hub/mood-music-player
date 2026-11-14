package com.musicsquare.player.audio

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Durable WorkManager entry point for sequential SAF audio conversion.
 * The UI must persist read grants for sources and write grants for the output tree.
 */
class BatchConversionWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    companion object {
        const val KEY_SOURCES="sources"
        const val KEY_TREE="tree"
        const val KEY_FORMAT="format"
        const val KEY_BITRATE="bitrate"
        const val KEY_DONE="completed"
        const val KEY_FAILED="failed"
        const val KEY_INDEX="index"
        const val KEY_TOTAL="total"
        const val KEY_ERROR="error"
        const val UNIQUE_NAME="musicsquare_batch_conversion"

        fun request(sources:List<Uri>,tree:Uri,format:AudioEngine.Format,bitrate:Int):Data {
            BatchSourcePolicy.checkSelection(sources.size,sources.distinct().size)
            require(bitrate in listOf(64,96,128,160,192,256,320))
            return Data.Builder()
                .putStringArray(KEY_SOURCES,sources.map(Uri::toString).toTypedArray())
                .putString(KEY_TREE,tree.toString())
                .putString(KEY_FORMAT,format.name)
                .putInt(KEY_BITRATE,bitrate)
                .build()
        }
    }

    override suspend fun doWork():Result {
        val sourceStrings=inputData.getStringArray(KEY_SOURCES) ?: return Result.failure(
            Data.Builder().putString(KEY_ERROR,"Missing source documents").build())
        val treeString=inputData.getString(KEY_TREE) ?: return Result.failure(
            Data.Builder().putString(KEY_ERROR,"Missing output folder").build())
        val format=runCatching { AudioEngine.Format.valueOf(inputData.getString(KEY_FORMAT).orEmpty()) }
            .getOrNull() ?: return Result.failure(Data.Builder().putString(KEY_ERROR,"Invalid format").build())
        val sources=sourceStrings.map(Uri::parse)
        val tree=Uri.parse(treeString)
        val bitrate=inputData.getInt(KEY_BITRATE,192)
        return try {
            BatchSourcePolicy.checkSelection(sources.size,sources.distinct().size)
            val converter=BatchAudioConverter(applicationContext,AudioEngine(applicationContext))
            val summary=converter.export(sources,tree,format,bitrate) { index,total ->
                coroutineContext.ensureActive()
                setProgress(Data.Builder().putInt(KEY_INDEX,index).putInt(KEY_TOTAL,total).build())
            }
            ConversionHistory(applicationContext).add(format,summary.completed,summary.failed)
            val output=Data.Builder().putInt(KEY_DONE,summary.completed)
                .putInt(KEY_FAILED,summary.failed)
                .putString(KEY_ERROR,summary.errors.take(3).joinToString("; "))
                .build()
            if(summary.failed==0)Result.success(output) else Result.failure(output)
        } catch(cancelled:CancellationException) {
            throw cancelled
        } catch(error:Exception) {
            Result.failure(Data.Builder().putString(KEY_ERROR,error.message ?: "Batch conversion failed").build())
        }
    }
}
