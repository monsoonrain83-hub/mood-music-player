package com.musicsquare.player.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Checks document metadata and available read access before beginning batch work.
 * This is advisory; a remote provider can revoke access during conversion.
 */
class BatchPreflight(private val context:Context) {
    data class Report(val readable:Int,val totalBytes:Long,val unknownSizes:Int,val problems:List<String>)
    suspend fun inspect(sources:List<Uri>):Report=withContext(Dispatchers.IO) {
        require(sources.size in 1..200)
        var readable=0
        var total=0L
        var unknown=0
        val errors=mutableListOf<String>()
        sources.distinct().forEachIndexed { index,uri ->
            coroutineContext.ensureActive()
            try {
                val size=context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { cursor ->
                    val column=cursor.getColumnIndex(OpenableColumns.SIZE)
                    if(cursor.moveToFirst() && column>=0 && !cursor.isNull(column))cursor.getLong(column) else null
                }
                if(size==null || size<0)unknown++
                else total=if(Long.MAX_VALUE-total<size)Long.MAX_VALUE else total+size
                context.contentResolver.openInputStream(uri)?.use { input ->
                    if(input.read()==-1)error("Empty audio document")
                } ?: error("Document is unreadable")
                readable++
            } catch(e:Exception) {
                errors+="File ${index+1}: ${e.message ?: "not readable"}"
            }
        }
        Report(readable,total,unknown,errors.take(10))
    }
}
