package com.musicsquare.player.tags

import android.content.Context
import android.net.Uri
import com.mpatric.mp3agic.Mp3File
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v24Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.provider.OpenableColumns
import com.musicsquare.player.audio.AudioEngine

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
   uris.mapIndexed { index,uri ->
    val result=writeOne(uri,changes)
    onProgress(index+1,uris.size)
    result
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
    source.outputStream().use { input.copyTo(it) }
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
   // Opening in write-truncate mode must succeed before copying; providers
   // without truncate semantics are rejected to avoid trailing old bytes.
   val descriptor=context.contentResolver.openFileDescriptor(uri,"rwt")
    ?: return TagResult(uri,false,"Provider does not allow truncate/write")
   descriptor.use { pfd ->
    File(modified.absolutePath).inputStream().use { input ->
     java.io.FileOutputStream(pfd.fileDescriptor).use { output ->
      input.copyTo(output)
      output.flush()
      output.fd.sync()
     }
    }
   }
   return TagResult(uri,true,"Embedded ID3 tags saved")
  } catch(e:Exception) {
   return TagResult(uri,false,e.localizedMessage ?: "Tag write failed")
  } finally {
   source.delete()
   modified.delete()
  }
 }
}
