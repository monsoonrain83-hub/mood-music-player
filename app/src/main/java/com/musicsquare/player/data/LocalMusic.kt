package com.musicsquare.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri
)

suspend fun scanLocalMusic(context: Context): List<LocalSong> = withContext(Dispatchers.IO) {
    val songs = mutableListOf<LocalSong>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val columns = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION
    )
    context.contentResolver.query(
        collection, columns,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    )?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cursor.moveToNext()) {
            val songId = cursor.getLong(id)
            songs += LocalSong(
                songId,
                cursor.getString(title) ?: "Unknown title",
                cursor.getString(artist) ?: "Unknown artist",
                cursor.getString(album) ?: "Unknown album",
                cursor.getLong(duration),
                ContentUris.withAppendedId(collection, songId)
            )
        }
    }
    songs
}

class MoodStore(context: Context) {
    private val preferences = context.getSharedPreferences("mood_assignments", Context.MODE_PRIVATE)
    fun cell(id: Long): Int? = preferences.getInt(id.toString(), -1).takeIf { it in 0..24 }
    fun assign(id: Long, cell: Int) {
        require(cell in 0..24)
        preferences.edit().putInt(id.toString(), cell).apply()
    }
}
