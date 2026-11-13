package com.musicsquare.player.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Sequential SAF batch export. Never changes the selected originals. */
class BatchAudioConverter(private val context:Context,private val engine:AudioEngine) {
    data class Summary(val completed:Int,val failed:Int,val errors:List<String>)
    suspend fun export(
        sources:List<Uri>, tree:Uri, format:AudioEngine.Format, bitrate:Int,
        progress:suspend (Int,Int)->Unit = { _,_-> }
    ):Summary = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty() && sources.size<=200) { "Select 1–200 files" }
        val root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        val uniqueSources=sources.distinct()
        val usedNames=mutableSetOf<String>()
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        // Fail closed if the provider cannot enumerate existing files.
        context.contentResolver.query(children,arrayOf(COLUMN_DISPLAY_NAME),null,null,null)?.use { cursor ->
            val nameIndex=cursor.getColumnIndex(COLUMN_DISPLAY_NAME)
            require(nameIndex>=0) { "Output folder cannot list existing filenames" }
            while(cursor.moveToNext()) {
                coroutineContext.ensureActive()
                if(!cursor.isNull(nameIndex))usedNames+=cursor.getString(nameIndex).lowercase(java.util.Locale.ROOT)
            }
        } ?: error("Output folder cannot be listed safely")
        var completed=0
        var failed=0
        val errors=mutableListOf<String>()
        uniqueSources.forEachIndexed { index,source ->
            coroutineContext.ensureActive()
            withContext(Dispatchers.Main) { progress(index+1,uniqueSources.size) }
            val displayName=runCatching {
                context.contentResolver.query(source,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                    if(cursor.moveToFirst())cursor.getString(0) else null
                }
            }.getOrNull() ?: "audio_${index+1}"
            val candidate=OutputNamePlanner.reserve(displayName,index,format.ext,usedNames)
            var destination:Uri?=null
            try {
                destination=DocumentsContract.createDocument(
                    context.contentResolver,root,format.mime,candidate
                ) ?: error("Cannot create destination document")
                engine.convert(source,destination,format,bitrate).getOrThrow()
                completed++
            } catch(cancelled:CancellationException) {
                destination?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver,it) } }
                throw cancelled
            } catch(error:Exception) {
                failed++
                errors+=("${displayName.take(60)}: ${error.message ?: "conversion failed"}")
                destination?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver,it) } }
            }
        }
        Summary(completed,failed,errors.take(10))
    }
}
