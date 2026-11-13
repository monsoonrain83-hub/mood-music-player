package com.musicsquare.player.tags

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only embedded metadata inspection; never writes to the source file. */
class TagInspector(private val context:Context) {
    data class Tags(
        val title:String?,val artist:String?,val album:String?,
        val year:String?,val genre:String?,val track:String?
    )
    suspend fun read(uri:Uri):Tags=withContext(Dispatchers.IO) {
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(context,uri)
            fun field(key:Int)=retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
            Tags(
                field(MediaMetadataRetriever.METADATA_KEY_TITLE),
                field(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                field(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                field(MediaMetadataRetriever.METADATA_KEY_YEAR),
                field(MediaMetadataRetriever.METADATA_KEY_GENRE),
                field(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            )
        } finally {
            retriever.release()
        }
    }
}
