package com.musicsquare.player.visual

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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

/** Read artwork from the local audio file, without network access. */
@Composable
fun LocalArtwork(uri: Uri?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var artwork by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        artwork = null
        if (uri != null) {
            artwork = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.embeddedPicture?.let { bytes ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    }
                } catch (_: Exception) {
                    null
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
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
