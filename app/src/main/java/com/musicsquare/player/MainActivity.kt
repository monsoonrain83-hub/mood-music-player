package com.musicsquare.player

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musicsquare.player.data.LocalSong
import com.musicsquare.player.data.MoodStore
import com.musicsquare.player.data.scanLocalMusic
import com.musicsquare.player.playback.MusicPlaybackService
import kotlinx.coroutines.launch

private val bg = Color(0xFF0C0C18)
private val surface = Color(0xFF20203A)
private val accent = Color(0xFFC6A4FF)
private val muted = Color(0xFFB5B2D3)
private val palette = listOf(Color(0xFF294B85),Color(0xFF246D91),Color(0xFF288D88),Color(0xFF8C689F),Color(0xFFBB755C))
private val names = listOf("Dreamy","Soft","Warm","Bright","Euphoric","Blue","Gentle","Easy","Sunny","Dance","Deep","Focus","Balanced","Groove","Drive","Somber","Reflective","Moody","Intense","Rush","Melancholy","Quiet","Dark","Tense","Wild")

class MainActivity : ComponentActivity() {
    private var future: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        future = f
        f.addListener({
            try { controller = f.get() } catch (_: Exception) { controller = null }
        }, ContextCompat.getMainExecutor(this))
        setContent { PlayerApp(controller) }
    }
    override fun onDestroy() {
        future?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
private fun PlayerApp(controller: MediaController?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { MoodStore(context) }
    var songs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf("Square") }
    var selectedCell by remember { mutableIntStateOf(-1) }
    var current by remember { mutableStateOf<LocalSong?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var assignSong by remember { mutableStateOf<LocalSong?>(null) }
    var assignmentsVersion by remember { mutableIntStateOf(0) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    fun refresh() {
        if (!allowed) return
        scope.launch {
            loading = true
            try { songs = scanLocalMusic(context); error = null }
            catch (e: Exception) { error = e.localizedMessage ?: "Unable to scan music" }
            finally { loading = false }
        }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        if (it) refresh() else error = "Music access is required to display local songs."
    }
    LaunchedEffect(allowed) { if (allowed) refresh() }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                current = songs.firstOrNull { it.uri.toString() == item?.localConfiguration?.uri.toString() }
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller, songs) {
        while (true) {
            position = controller?.currentPosition?.coerceAtLeast(0) ?: 0L
            kotlinx.coroutines.delay(500)
        }
    }
    fun play(list: List<LocalSong>, index: Int = 0) {
        if (list.isEmpty()) return
        val items = list.map { song ->
            MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).build()).build()
        }
        controller?.apply { setMediaItems(items, index.coerceIn(0,items.lastIndex), 0); prepare(); play() }
        current = list[index.coerceIn(0,list.lastIndex)]
        screen = "Now"
    }
    MaterialTheme(colorScheme = darkColorScheme(primary=accent,background=bg,surface=surface)) {
        Scaffold(containerColor=bg, bottomBar={
            Column {
                current?.let { song ->
                    Row(Modifier.fillMaxWidth().background(surface).clickable { screen="Now" }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote,null,tint=accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.White)
                            Text(song.artist,fontSize=12.sp,color=muted,maxLines=1)
                        }
                        IconButton(onClick={if(playing) controller?.pause() else controller?.play()}) {
                            Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Play or pause",tint=Color.White)
                        }
                    }
                }
                NavigationBar(containerColor=Color(0xFF171728)) {
                    listOf(Triple("Square",Icons.Default.GridView,"Square"),Triple("Library",Icons.Default.LibraryMusic,"Library"),Triple("Now",Icons.Default.PlayCircle,"Player"),Triple("Settings",Icons.Default.Settings,"Settings")).forEach { (key,icon,title) ->
                        NavigationBarItem(selected=screen==key,onClick={screen=key},icon={Icon(icon,title)},label={Text(title)})
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp)) {
                Spacer(Modifier.height(18.dp))
                when(screen) {
                    "Square" -> {
                        Text("MUSIC SQUARE",fontSize=13.sp,fontWeight=FontWeight.Bold,color=muted)
                        Text("Find your mood",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        Text("Energy →   •   Happiness ↑",color=muted,fontSize=13.sp)
                        Spacer(Modifier.height(20.dp))
                        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            repeat(5) { row ->
                                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                    repeat(5) { col ->
                                        val cell=row*5+col
                                        val count=songs.count { store.cell(it.id)==cell }
                                        Column(Modifier.weight(1f).aspectRatio(0.82f)
                                            .background(palette[(col+row)%5].copy(alpha=if(selectedCell==cell)1f else .72f),RoundedCornerShape(10.dp))
                                            .clickable { selectedCell=cell },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                            Text(names[cell],fontSize=9.sp,color=Color.White,maxLines=1)
                                            Text("$count",fontSize=14.sp,color=Color.White,fontWeight=FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(if(selectedCell<0)"Select a mood cell" else names[selectedCell],fontSize=22.sp,color=Color.White,fontWeight=FontWeight.Bold)
                        Text("Songs are assigned manually until reliable offline mood analysis is available.",color=muted,fontSize=12.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(enabled=selectedCell>=0 && songs.any { store.cell(it.id)==selectedCell } && controller!=null,
                            onClick={play(songs.filter { store.cell(it.id)==selectedCell })},modifier=Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow,null); Text(" Play this mood")
                        }
                        OutlinedButton(onClick={screen="Library"},modifier=Modifier.fillMaxWidth()) { Text("Assign songs in Library") }
                    }
                    "Library" -> {
                        Text("Your Library",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("${songs.size} local songs",color=muted,modifier=Modifier.weight(1f))
                            TextButton(onClick={if(allowed)refresh() else request.launch(permission)}) { Text("Refresh") }
                        }
                        if (!allowed) Button(onClick={request.launch(permission)}) { Text("Allow music access") }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        error?.let { Text(it,color=Color(0xFFFF9C9C)) }
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            items(songs,key={it.id}) { song ->
                                Row(Modifier.fillMaxWidth().clickable { play(songs,songs.indexOf(song)) }.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote,null,tint=accent,modifier=Modifier.size(36.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text("${song.artist} • ${store.cell(song.id)?.let { names[it] } ?: "Unclassified"}",fontSize=12.sp,color=muted,maxLines=1)
                                    }
                                    TextButton(onClick={assignSong=song}) { Text("Mood") }
                                }
                            }
                        }
                    }
                    "Now" -> {
                        Text("Now Playing",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        Spacer(Modifier.height(32.dp))
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF55418B),RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center) {
                            Icon(Icons.Default.MusicNote,"Album art placeholder",tint=Color.White,modifier=Modifier.size(120.dp))
                        }
                        Spacer(Modifier.height(26.dp))
                        Text(current?.title ?: "No song selected",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=2)
                        Text(current?.artist ?: "Choose music from your library",color=muted)
                        Spacer(Modifier.height(24.dp))
                        val duration=(controller?.duration ?: 0L).coerceAtLeast(0L)
                        Slider(value=if(duration>0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f,
                            onValueChange={controller?.seekTo((it*duration).toLong())},enabled=duration>0)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                            IconButton(onClick={controller?.seekToPreviousMediaItem()}) { Icon(Icons.Default.SkipPrevious,"Previous",tint=Color.White) }
                            FilledIconButton(onClick={if(playing)controller?.pause() else controller?.play()},modifier=Modifier.size(68.dp)) {
                                Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Play or pause")
                            }
                            IconButton(onClick={controller?.seekToNextMediaItem()}) { Icon(Icons.Default.SkipNext,"Next",tint=Color.White) }
                            IconButton(onClick={
                                current?.let { song ->
                                    val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,"${song.title} — ${song.artist}") }
                                    context.startActivity(Intent.createChooser(intent,"Share song"))
                                }
                            }) { Icon(Icons.Default.Share,"Share",tint=Color.White) }
                        }
                        current?.let { song -> Text("Mood: ${store.cell(song.id)?.let { names[it] } ?: "Unclassified"}",color=muted) }
                    }
                    else -> {
                        Text("Settings",fontSize=30.sp,color=Color.White,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        Text("Offline-first • Local playback",color=muted)
                        Text("No account or internet required.",color=muted)
                        Spacer(Modifier.height(12.dp))
                        Text("MusicSquare Player 0.2.0",color=Color.White)
                    }
                }
            }
        }
        assignSong?.let { song ->
            AlertDialog(onDismissRequest={assignSong=null},title={Text("Choose mood for ${song.title}")},
                text={
                    Column(Modifier.heightIn(max=390.dp).verticalScroll(rememberScrollState())) {
                        names.forEachIndexed { index, name ->
                            TextButton(onClick={store.assign(song.id,index);assignmentsVersion++;assignSong=null},modifier=Modifier.fillMaxWidth()) {
                                Text("${index+1}. $name")
                            }
                        }
                    }
                },confirmButton={TextButton(onClick={assignSong=null}){Text("Close")}})
        }
    }
}
