package com.musicsquare.player.visual

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ArtworkCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    @Synchronized fun get(key: String): Bitmap? = cache.get(key)
    @Synchronized fun put(key: String, value: Bitmap) { cache.put(key, value) }
}

/** Embedded artwork with bounded bitmap decoding and a small in-memory LRU cache. */
@Composable
fun LocalArtwork(uri: Uri?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = uri?.toString()
    var artwork by remember(key) { mutableStateOf(key?.let { ArtworkCache.get(it) }) }
    LaunchedEffect(key) {
        artwork = key?.let { ArtworkCache.get(it) }
        if (uri != null && key != null && artwork == null) {
            artwork = withContext(Dispatchers.IO) {
                ArtworkCache.get(key) ?: run {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.embeddedPicture?.let { bytes ->
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            var sample = 1
                            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                            val options = BitmapFactory.Options().apply { inSampleSize = sample }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also {
                                ArtworkCache.put(key, it)
                            }
                        }
                    } catch (_: Exception) {
                        null
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    Box(modifier.background(Color(0xFF55418B)), contentAlignment = Alignment.Center) {
        val bitmap = artwork
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.MusicNote, contentDescription = "No album artwork", tint = Color.White)
    }
}
