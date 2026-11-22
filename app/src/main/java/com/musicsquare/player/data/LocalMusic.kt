package com.musicsquare.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val folder: String = "Unknown folder",
    val genre: String = "Unknown genre",
    val year: Int = 0,
    val modified: Long = 0L
)

suspend fun scanLocalMusic(context: Context): List<LocalSong> = withContext(Dispatchers.IO) {
    val songs = mutableListOf<LocalSong>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val columns = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_MODIFIED,
        // DATA is deprecated and may be unavailable to scoped-storage apps.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.YEAR
    )
    // A null cursor means the provider could not supply a library result. Do
    // not report this as a successful scan of zero songs: surface the failure
    // to the existing refresh error UI so the user can distinguish it from
    // an actually empty music collection.
    val cursor = context.contentResolver.query(
        collection, columns,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    ) ?: throw IllegalStateException("MediaStore audio query returned no cursor")
    var malformedAudioRows = 0
    cursor.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val modified = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
        val pathIndex = cursor.getColumnIndex(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        )
        val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
        while (cursor.moveToNext()) {
            // A malformed MediaStore row must not discard all previously read songs.
            // Cancellation is still propagated immediately.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
            val songId = cursor.getLong(id)
            songs += LocalSong(
                songId,
                cursor.getString(title) ?: "Unknown title",
                cursor.getString(artist) ?: "Unknown artist",
                cursor.getString(album) ?: "Unknown album",
                cursor.getLong(duration),
                ContentUris.withAppendedId(collection, songId),
                folder = (if (pathIndex >= 0 && !cursor.isNull(pathIndex)) cursor.getString(pathIndex) else null)?.let { path ->
                    val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) path.trimEnd('/')
                        else path.substringBeforeLast('/', missingDelimiterValue = "")
                    directory.substringAfterLast('/').ifBlank { "Unknown folder" }
                } ?: "Unknown folder",
                year = if(yearIndex < 0 || cursor.isNull(yearIndex)) 0 else cursor.getInt(yearIndex),
                modified = if(modified < 0 || cursor.isNull(modified)) 0L else cursor.getLong(modified)
            )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                malformedAudioRows++
                android.util.Log.w("MusicSquareLibrary", "Skipping malformed MediaStore audio row", error)
            }
        }
    }
    // A nonempty provider result with zero readable rows is a failed scan,
    // not an empty music library. Preserve the previous library in refresh().
    if (songs.isEmpty() && malformedAudioRows > 0) {
        throw IllegalStateException("MediaStore returned $malformedAudioRows unreadable audio rows")
    }
    val genreMap = mutableMapOf<Long,String>()
    // Genre metadata is optional. Some providers deny or do not implement this
    // collection even when the main audio query succeeds. Keep scanned tracks.
    try {
        context.contentResolver.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID,MediaStore.Audio.Genres.NAME),null,null,null)?.use { genres ->
        val gid=genres.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
        val gname=genres.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
        while(genres.moveToNext()) {
            // Optional metadata must not block the library or swallow cancellation.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                val id = genres.getLong(gid)
                val name = genres.getString(gname) ?: continue
                context.contentResolver.query(MediaStore.Audio.Genres.Members.getContentUri("external", id),
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null)?.use { members ->
                    val aid = members.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    while (members.moveToNext()) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        try {
                            genreMap[members.getLong(aid)] = name
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.util.Log.w("MusicSquareLibrary", "Skipping malformed genre member", error)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MusicSquareLibrary", "Skipping unavailable genre metadata", error)
            }
        }
    }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // Preserve the library when optional genre enrichment fails.
        android.util.Log.w("MusicSquareLibrary", "Genre lookup unavailable", error)
    }
    songs.map { it.copy(genre=genreMap[it.id] ?: "Unknown genre") }
}

class MoodStore(context: Context) {
    private val preferences = context.getSharedPreferences("mood_assignments", Context.MODE_PRIVATE)
    fun cell(id: Long): Int? = try {
        preferences.getInt(id.toString(), -1).takeIf { it in 0..24 }
    } catch (error: ClassCastException) {
        // Legacy/corrupt preference types must not crash the grid, library,
        // or scanning loop. Treat only this track as unclassified.
        android.util.Log.w("MusicSquareMood", "Invalid saved mood cell for track $id", error)
        null
    }
    fun assign(id: Long, cell: Int) {
        require(cell in 0..24)
        preferences.edit().putInt(id.toString(), cell).apply()
    }
}
