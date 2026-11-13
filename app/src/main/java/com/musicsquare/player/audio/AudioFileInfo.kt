package com.musicsquare.player.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only media inspector; never mutates the selected document. */
class AudioFileInfo(private val context:Context) {
    data class Details(
        val name:String,val bytes:Long?,val durationMs:Long?,
        val bitrate:Int?,val sampleRate:Int?,val mime:String?
    )
    suspend fun inspect(uri:Uri):Details=withContext(Dispatchers.IO) {
        var name="Selected audio"
        var bytes:Long?=null
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst()) {
                val titleIndex=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex=cursor.getColumnIndex(OpenableColumns.SIZE)
                if(titleIndex>=0 && !cursor.isNull(titleIndex))name=cursor.getString(titleIndex)
                if(sizeIndex>=0 && !cursor.isNull(sizeIndex))bytes=cursor.getLong(sizeIndex).takeIf { it>=0 }
            }
        }
        val retriever=MediaMetadataRetriever()
        var duration:Long?=null
        var bitrate:Int?=null
        var sampleRate:Int?=null
        try {
            retriever.setDataSource(context,uri)
            duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            bitrate=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            if(android.os.Build.VERSION.SDK_INT>=31)
                sampleRate=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
        } catch(_:Exception) {
            // Unsupported formats can still be converted by FFmpeg.
        } finally {
            runCatching { retriever.release() }
        }
        Details(name,bytes,duration,bitrate,sampleRate,context.contentResolver.getType(uri))
    }
}
