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

    private fun sameDocument(a:Uri,b:Uri):Boolean {
        if(a==b)return true
        if(a.scheme!="content" || b.scheme!="content" || a.authority!=b.authority)return false
        return runCatching {
            DocumentsContract.isDocumentUri(context,a) && DocumentsContract.isDocumentUri(context,b) &&
                DocumentsContract.getDocumentId(a)==DocumentsContract.getDocumentId(b)
        }.getOrDefault(false)
    }

    private fun cleanupOutput(destination:Uri?,source:Uri):String? {
        if(destination==null)return null
        if(sameDocument(destination,source))
            return "Output URI aliases source; original was not deleted"
        return runCatching {
            if(!DocumentsContract.deleteDocument(context.contentResolver,destination))
                "Could not remove incomplete output"
            else null
        }.getOrElse { "Could not remove incomplete output: ${it.message}" }
    }
    suspend fun export(
        sources:List<Uri>, tree:Uri, format:AudioEngine.Format, bitrate:Int,
        progress:suspend (Int,Int)->Unit = { _,_-> }
    ):Summary = withContext(Dispatchers.IO) {
        BatchSourcePolicy.checkSelection(sources.size,sources.distinct().size)
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
            var destination:Uri?=null
            try {
                // The picker-time preflight is only advisory: SAF access can change.
                // Recheck each source before creating an output document.
                val size=context.contentResolver.query(source,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { cursor ->
                    val column=cursor.getColumnIndex(OpenableColumns.SIZE)
                    if(column>=0 && cursor.moveToFirst() && !cursor.isNull(column))
                        cursor.getLong(column) else null
                }
                BatchSourcePolicy.checkKnownSize(size)
                context.contentResolver.openInputStream(source)?.use { input ->
                    coroutineContext.ensureActive()
                    require(input.read()>=0) { "Source file is empty" }
                } ?: error("Source is no longer readable")
                coroutineContext.ensureActive()
                val candidate=OutputNamePlanner.reserve(displayName,index,format.ext,usedNames)
                destination=DocumentsContract.createDocument(
                    context.contentResolver,root,format.mime,candidate
                ) ?: error("Cannot create destination document")
                require(!sameDocument(destination,source)) { "Provider returned the source as output" }
                val createdName=context.contentResolver.query(destination,arrayOf(COLUMN_DISPLAY_NAME),null,null,null)?.use { cursor ->
                    if(cursor.moveToFirst())cursor.getString(0) else null
                }
                require(createdName==candidate) {
                    "Provider changed output name; refusing ambiguous destination"
                }
                engine.convert(source,destination,format,bitrate).getOrThrow()
                completed++
            } catch(cancelled:CancellationException) {
                cleanupOutput(destination,source)
                throw cancelled
            } catch(error:Exception) {
                failed++
                val cleanup=cleanupOutput(destination,source)
                errors+=("${displayName.take(60)}: ${error.message ?: "conversion failed"}" +
                    (cleanup?.let { "; ${it}" } ?: ""))
            }
        }
        Summary(completed,failed,errors.take(10))
    }
}
