package com.musicsquare.player.tags

import android.content.Context
import android.net.Uri
import com.mpatric.mp3agic.Mp3File
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v24Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import android.provider.OpenableColumns
import com.musicsquare.player.audio.AudioEngine
import com.musicsquare.player.audio.AudioFileSafety

data class TagChanges(
 val title:String?=null,
 val artist:String?=null,
 val album:String?=null,
 val year:String?=null,
 val genre:String?=null,
 val track:String?=null,
 val comment:String?=null
)
data class TagResult(val uri:Uri,val success:Boolean,val message:String)

/**
 * Edits embedded ID3 tags in MP3 files selected via Android's SAF.
 * Never silently changes MediaStore metadata only: writes the MP3 bytes back.
 * The SAF provider must grant write access. Backups are temporary per file.
 */
class TagEditor(private val context:Context) {
 private val engine=AudioEngine(context)
 private fun format(uri:Uri):AudioEngine.Format? {
  val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
   if(cursor.moveToFirst())cursor.getString(0) else null
  } ?: uri.lastPathSegment.orEmpty()
  return AudioEngine.Format.entries.firstOrNull { name.endsWith(".${it.ext}",ignoreCase=true) }
 }
 suspend fun writeBatch(uris:List<Uri>,changes:TagChanges,onProgress:(Int,Int)->Unit={ _, _ -> }):List<TagResult> =
  withContext(Dispatchers.IO) {
   require(uris.size in 1..200) { "Select 1–200 files for batch tagging" }
   val valid=TagInputValidator.validate(changes).getOrElse { error ->
    return@withContext uris.distinct().map { TagResult(it,false,error.message ?: "Invalid metadata") }
   }
   val unique=uris.distinct()
   unique.mapIndexed { index,uri ->
    coroutineContext.ensureActive()
    val result=writeOne(uri,valid)
    withContext(Dispatchers.Main) { onProgress(index+1,unique.size) }
    result
   }
  }

 private fun sha256(file:File):ByteArray {
  val digest=MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
   val chunk=ByteArray(64*1024)
   while(true) {
    val count=input.read(chunk)
    if(count<0)break
    if(count>0)digest.update(chunk,0,count)
   }
  }
  return digest.digest()
 }

 private fun writeExact(uri:Uri,file:File) {
  val descriptor=context.contentResolver.openFileDescriptor(uri,"rwt")
   ?: error("Document provider does not support write/truncate")
  descriptor.use { pfd ->
   file.inputStream().use { input ->
    FileOutputStream(pfd.fileDescriptor).use { output ->
     input.copyTo(output)
     output.flush()
     output.fd.sync()
    }
   }
  }
 }

 private suspend fun writeOne(uri:Uri,changes:TagChanges):TagResult {
  val fmt=format(uri) ?: return TagResult(uri,false,"Unsupported format; use MP3, WAV, FLAC, OGG or M4A")
  if(fmt!=AudioEngine.Format.MP3) {
   val result=engine.writeTags(uri,uri,fmt,AudioEngine.Metadata(changes.title,changes.artist,changes.album,changes.year,changes.genre,changes.track,changes.comment))
   return result.fold({TagResult(uri,true,"Embedded metadata updated")},{TagResult(uri,false,it.message ?: "Tag write failed")})
  }
  val source=File.createTempFile("tag_source_",".mp3",context.cacheDir)
  val modified=File.createTempFile("tag_modified_",".mp3",context.cacheDir)
  try {
   context.contentResolver.openInputStream(uri)?.use { input ->
    source.outputStream().use { output ->
     AudioFileSafety.copyBounded(input,output) { coroutineContext.ensureActive() }
    }
   } ?: return TagResult(uri,false,"Cannot read source")
   val mp3=Mp3File(source)
   val tag:ID3v2=mp3.id3v2Tag ?: ID3v24Tag()
   changes.title?.let { tag.title=it }
   changes.artist?.let { tag.artist=it }
   changes.album?.let { tag.album=it }
   changes.year?.let { tag.year=it }
   changes.genre?.let { tag.genreDescription=it }
   changes.track?.let { tag.track=it }
   changes.comment?.let { tag.comment=it }
   mp3.id3v2Tag=tag
   modified.delete()
   mp3.save(modified.absolutePath)
   // Validate the generated file before touching the user's original.
   val updated=Mp3File(modified).id3v2Tag
    ?: return TagResult(uri,false,"Generated MP3 has no ID3v2 metadata")
   val fields=listOf(
    changes.title to updated.title, changes.artist to updated.artist,
    changes.album to updated.album, changes.year to updated.year,
    changes.genre to updated.genreDescription, changes.track to updated.track,
    changes.comment to updated.comment
   )
   if(fields.any { (requested,actual) -> requested!=null && requested!=actual })
    return TagResult(uri,false,"Generated tags failed verification; original untouched")
   if(modified.length()<=0L) return TagResult(uri,false,"Empty generated MP3")
   if(modified.length()>AudioFileSafety.MAX_OUTPUT_BYTES)
    return TagResult(uri,false,"Generated MP3 exceeds safe output size")
   // SAF writes are not universally atomic. Keep the original in a private
   // temporary file until the replacement has been verified on the provider.
   var replacementStarted=false
   try {
    replacementStarted=true
    writeExact(uri,modified)
    val expected=sha256(modified)
    val (bytes,actual)=context.contentResolver.openInputStream(uri)?.use { stream ->
     AudioFileSafety.sha256(stream) { coroutineContext.ensureActive() }
    } ?: error("Cannot verify document after saving")
    check(bytes==modified.length() && actual.contentEquals(expected)) {
     "Document read-back did not match"
    }
   } catch(writeError:Exception) {
    if(replacementStarted) {
     try {
      writeExact(uri,source)
     } catch(restoreError:Exception) {
      return TagResult(uri,false,
       "Save failed and automatic restore failed: ${writeError.message}; ${restoreError.message}. Check original document immediately.")
     }
    }
    return TagResult(uri,false,"Save failed; original bytes restored: ${writeError.message}")
   }
   return TagResult(uri,true,"Original MP3 updated and byte-for-byte verified")
  } catch(cancelled:CancellationException) {
   throw cancelled
  } catch(e:Exception) {
   return TagResult(uri,false,e.localizedMessage ?: "Tag write failed")
  } finally {
   source.delete()
   modified.delete()
  }
 }
}
