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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.consume
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
import com.musicsquare.player.audio.OfflineAudioAnalyzer
import com.musicsquare.player.audio.AudioEngine
import com.musicsquare.player.audio.WavEditor
import com.musicsquare.player.audio.WavEdit
import com.musicsquare.player.data.AnalysisStore
import com.musicsquare.player.domain.MoodCoordinates
import com.musicsquare.player.data.LibraryBackup
import com.musicsquare.player.data.PlaybackQueueStore
import com.musicsquare.player.data.LibraryExtras
import com.musicsquare.player.visual.DancingCompanion
import com.musicsquare.player.lyrics.LyricsRepository
import com.musicsquare.player.lyrics.Lyrics
import com.musicsquare.player.tags.TagEditor
import com.musicsquare.player.tags.TagChanges
import android.net.Uri
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
    val analysisStore = remember { AnalysisStore(context) }
    val analyzer = remember { OfflineAudioAnalyzer(context) }
    val extras = remember { LibraryExtras(context) }
    val queueStore=remember { PlaybackQueueStore(context) }
    val backup=remember { LibraryBackup(context) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var playlistRename by remember { mutableStateOf("") }
    var restoredQueue by remember { mutableStateOf(false) }
    val tagEditor = remember { TagEditor(context) }
    val wavEditor = remember { WavEditor(context) }
    val audioEngine = remember { AudioEngine(context) }
    val lyricsRepository = remember { LyricsRepository(context) }
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    var lyricsVisible by remember { mutableStateOf(true) }
    var dancerVisible by remember { mutableStateOf(false) }
    var manualLyrics by remember { mutableStateOf("") }
    var lyricsMessage by remember { mutableStateOf<String?>(null) }
    var lyricsOffset by remember { mutableLongStateOf(0L) }
    var converterSource by remember { mutableStateOf<Uri?>(null) }
    var converterFormat by remember { mutableStateOf(AudioEngine.Format.MP3) }
    var converterBitrate by remember { mutableIntStateOf(192) }
    var converterStatus by remember { mutableStateOf<String?>(null) }
    var converterBusy by remember { mutableStateOf(false) }
    var tagFormat by remember { mutableStateOf(AudioEngine.Format.MP3) }
    var tagExportSource by remember { mutableStateOf<Uri?>(null) }
    var tagExportStatus by remember { mutableStateOf<String?>(null) }
    var wavSource by remember { mutableStateOf<Uri?>(null) }
    var cutterFormat by remember { mutableStateOf(AudioEngine.Format.MP3) }
    var cutterBitrate by remember { mutableIntStateOf(192) }
    var cutterPitch by remember { mutableStateOf(true) }
    var wavStart by remember { mutableStateOf("0") }
    var wavEnd by remember { mutableStateOf("") }
    var wavRemoveRanges by remember { mutableStateOf("") }
    var wavSpeed by remember { mutableFloatStateOf(1f) }
    var wavRemoveSilence by remember { mutableStateOf(false) }
    var wavSilenceSeconds by remember { mutableStateOf("1.5") }
    var wavStatus by remember { mutableStateOf<String?>(null) }
    var wavBusy by remember { mutableStateOf(false) }
    val eqPrefs = remember { context.getSharedPreferences("equalizer",android.content.Context.MODE_PRIVATE) }
    var preset by remember { mutableStateOf(eqPrefs.getString("preset","Flat") ?: "Flat") }
    var bass by remember { mutableFloatStateOf(eqPrefs.getInt("bass",0).toFloat()) }
    var grouping by remember { mutableStateOf("All") }
    var selectedGroup by remember { mutableStateOf("All") }
    var playlistName by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var selectedForPlaylist by remember { mutableStateOf<LocalSong?>(null) }
    var playlistVersion by remember { mutableIntStateOf(0) }
    var tagTitle by remember { mutableStateOf("") }
    var tagArtist by remember { mutableStateOf("") }
    var tagAlbum by remember { mutableStateOf("") }
    var tagYear by remember { mutableStateOf("") }
    var tagGenre by remember { mutableStateOf("") }
    var tagStatus by remember { mutableStateOf<String?>(null) }
    var tagBusy by remember { mutableStateOf(false) }
    var selectedTagUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var analysisProgress by remember { mutableStateOf<String?>(null) }
    var analyzed by remember { mutableIntStateOf(0) }
    var songs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf("Square") }
    var selectedCell by remember { mutableIntStateOf(-1) }
    var moodValence by remember { mutableFloatStateOf(0.5f) }
    var moodEnergy by remember { mutableFloatStateOf(0.5f) }
    var queueExpanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<LocalSong?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var sleepRemaining by remember { mutableLongStateOf(0L) }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var assignSong by remember { mutableStateOf<LocalSong?>(null) }
    var assignmentsVersion by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var favoritesVersion by remember { mutableIntStateOf(0) }
    val favorites = remember { context.getSharedPreferences("favorites", android.content.Context.MODE_PRIVATE) }
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
    val tagExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { tagExportSource=it }
    val tagExportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val src=tagExportSource
        if(src!=null && dest!=null)scope.launch {
            tagBusy=true
            val result=audioEngine.writeTags(src,dest,tagFormat,AudioEngine.Metadata(
                title=tagTitle.takeIf{it.isNotBlank()},artist=tagArtist.takeIf{it.isNotBlank()},
                album=tagAlbum.takeIf{it.isNotBlank()},year=tagYear.takeIf{it.isNotBlank()},
                genre=tagGenre.takeIf{it.isNotBlank()}
            ))
            tagExportStatus=result.fold(onSuccess={"Tagged copy saved"},onFailure={it.message ?: "Metadata export failed"})
            tagBusy=false
        }
    }
    val converterPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { converterSource=it }
    val converterSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val src=converterSource
        if(src!=null && dest!=null)scope.launch {
            converterBusy=true
            val result=audioEngine.convert(src,dest,converterFormat,converterBitrate)
            converterStatus=result.fold(onSuccess={"Converted file saved"},onFailure={it.message ?: "Conversion failed"})
            converterBusy=false
            refresh()
        }
    }
    val wavPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { wavSource=it }
    val cutterSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val src=wavSource
        if(src!=null && dest!=null)scope.launch {
            wavBusy=true
            val ranges=wavRemoveRanges.split(",").mapNotNull { part ->
                val t=part.trim().split("-");if(t.size!=2)null else {
                    val a=t[0].trim().toDoubleOrNull();val b=t[1].trim().toDoubleOrNull()
                    if(a!=null && b!=null && a>=0 && b>a)a to b else null
                }
            }
            val result=audioEngine.convert(src,dest,cutterFormat,cutterBitrate,AudioEngine.Edit(
                startSeconds=wavStart.toDoubleOrNull() ?: 0.0,
                endSeconds=wavEnd.toDoubleOrNull(),removeRanges=ranges,
                silenceSeconds=if(wavRemoveSilence)wavSilenceSeconds.toDoubleOrNull() ?: 1.5 else null,
                speed=wavSpeed.toDouble(),preservePitch=cutterPitch
            ))
            wavStatus=result.fold(onSuccess={"Edited audio saved"},onFailure={it.message ?: "Audio edit failed"})
            wavBusy=false
            refresh()
        }
    }
    val wavSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { dest ->
        val src=wavSource
        if(src!=null && dest!=null) scope.launch {
            wavBusy=true
            val result=wavEditor.edit(src,dest,WavEdit(
                startMs=((wavStart.toDoubleOrNull() ?: 0.0)*1000).toLong(),
                endMs=wavEnd.toDoubleOrNull()?.let { (it*1000).toLong() },
                speed=wavSpeed.toDouble(),
                removeSilence=wavRemoveSilence,
                minSilenceMs=((wavSilenceSeconds.toDoubleOrNull() ?: 1.5)*1000).toInt().coerceIn(250,30000),
                removeRangesMs=wavRemoveRanges.split(",").mapNotNull { part ->
                    val times=part.trim().split("-")
                    if(times.size!=2)null else {
                        val start=times[0].trim().toDoubleOrNull()
                        val end=times[1].trim().toDoubleOrNull()
                        if(start==null || end==null || start<0 || end<=start)null else ((start*1000).toLong() to (end*1000).toLong())
                    }
                }
            ))
            wavStatus=result.fold(onSuccess={"WAV file saved successfully"},onFailure={it.message ?: "WAV export failed"})
            wavBusy=false
            refresh()
        }
    }
    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val song=current
        if(uri!=null && song!=null)scope.launch {
            val raw=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if(raw!=null) {
                lyricsRepository.saveLrc(song.title,song.artist,song.duration,raw)
                lyrics=lyricsRepository.saved(song.title,song.artist,song.duration)
                lyricsMessage="LRC imported and saved offline"
            }
        }
    }
    val lrcSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val song=current
        if(uri!=null && song!=null)scope.launch {
            val content=lyricsRepository.exportLrc(song.title,song.artist,song.duration) ?: lyrics?.plain ?: ""
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { it.write(content) }
            }
            lyricsMessage="Lyrics saved as a separate file"
        }
    }
    val backupSaver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null)scope.launch {
            backupStatus=runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(backup.export()) }
                }
                "Backup saved"
            }.getOrElse { it.message ?: "Backup failed" }
        }
    }
    val backupPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)scope.launch {
            backupStatus=runCatching {
                val raw=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                "Restored ${backup.import(raw)} settings; restart to refresh all views"
            }.getOrElse { it.message ?: "Restore failed" }
        }
    }
    val tagPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selectedTagUris = uris.filter { it.toString().isNotBlank() }
        tagStatus = if(selectedTagUris.isEmpty()) "No files selected" else "${selectedTagUris.size} files selected. Blank fields remain unchanged."
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        if (it) refresh() else error = "Music access is required to display local songs."
    }
    LaunchedEffect(allowed) { if (allowed) refresh() }
    val latestSongs by rememberUpdatedState(songs)
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val matched=latestSongs.firstOrNull { it.id.toString()==item?.mediaId }
                if(matched!=null && matched.id!=current?.id) {
                    current=matched
                    extras.played(matched.id)
                }
            }
        }
        controller?.addListener(listener)
        controller?.currentMediaItem?.let { item -> current=latestSongs.firstOrNull { it.id.toString()==item.mediaId } ?: current }
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller, songs) {
        while (true) {
            position = controller?.currentPosition?.coerceAtLeast(0) ?: 0L
            kotlinx.coroutines.delay(500)
        }
    }
    LaunchedEffect(controller,songs) {
        if(!restoredQueue && controller!=null && songs.isNotEmpty()) {
            restoredQueue=true
            val snapshot=queueStore.read()
            if(snapshot!=null && controller.mediaItemCount==0) {
                val restored=snapshot.ids.mapNotNull { id -> songs.firstOrNull { it.id==id } }
                if(restored.isNotEmpty()) {
                    val items=restored.map { song ->
                        MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString())
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).build()).build()
                    }
                    val idx=snapshot.index.coerceIn(0,items.lastIndex)
                    controller.setMediaItems(items,idx,snapshot.positionMs)
                    controller.shuffleModeEnabled=snapshot.shuffle
                    controller.repeatMode=snapshot.repeat
                    controller.prepare()
                    current=restored[idx]
                    shuffleOn=snapshot.shuffle
                    repeatMode=snapshot.repeat
                }
            }
        }
    }
    LaunchedEffect(controller) {
        while(true) {
            kotlinx.coroutines.delay(5000)
            val player=controller ?: continue
            val ids=(0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
            if(ids.isNotEmpty())queueStore.save(ids,player.currentMediaItemIndex,player.currentPosition,player.shuffleModeEnabled,player.repeatMode)
        }
    }
    LaunchedEffect(sleepMinutes) {
        if(sleepMinutes>0) {
            sleepRemaining=sleepMinutes*60L
            while(sleepRemaining>0L) {
                kotlinx.coroutines.delay(1000)
                sleepRemaining--
            }
            controller?.pause()
            sleepMinutes=0
        } else sleepRemaining=0
    }
    LaunchedEffect(current?.id) {
        val song=current
        lyrics=null
        lyricsOffset=if(song!=null)lyricsRepository.offsetMs(song.title,song.artist,song.duration) else 0L
        if(song!=null) {
            lyrics=lyricsRepository.saved(song.title,song.artist,song.duration)
            if(lyrics==null) {
                lyricsLoading=true
                lyrics=lyricsRepository.fetch(song.title,song.artist,song.album,song.duration)
                lyricsLoading=false
            }
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
        extras.played(current!!.id)
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
                    listOf(Triple("Square",Icons.Default.GridView,"Square"),Triple("Library",Icons.Default.LibraryMusic,"Library"),Triple("Now",Icons.Default.PlayCircle,"Player"),Triple("Tools",Icons.Default.Tune,"Tools")).forEach { (key,icon,title) ->
                        NavigationBarItem(selected=screen==key,onClick={screen=key},icon={Icon(icon,title)},label={Text(title)})
                    }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(horizontal=18.dp).then(if(screen=="Settings" || screen=="Now") Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
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
                                        val count=songs.count { store.cell(it.id)==cell } + (assignmentsVersion * 0)
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
                        Text("Energy and BPM are estimated offline; valence is user-labeled, not guessed.",color=muted,fontSize=12.sp)
                        Text("Manual mood mapping: happiness ${(moodValence*100).toInt()}% / energy ${(moodEnergy*100).toInt()}%",color=muted,fontSize=12.sp)
                        Text("Happiness (valence)",color=muted,fontSize=12.sp)
                        Slider(value=moodValence,onValueChange={moodValence=it})
                        Text("Energy",color=muted,fontSize=12.sp)
                        Slider(value=moodEnergy,onValueChange={moodEnergy=it})
                        TextButton(onClick={selectedCell=MoodCoordinates.cell(moodValence,moodEnergy)}) { Text("Select cell from mood coordinates") }
                        Spacer(Modifier.height(12.dp))
                        Button(enabled=selectedCell>=0 && songs.any { store.cell(it.id)==selectedCell } && controller!=null,
                            onClick={play(songs.filter { store.cell(it.id)==selectedCell })},modifier=Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow,null); Text(" Play this mood")
                        }
                        OutlinedButton(onClick={screen="Library"},modifier=Modifier.fillMaxWidth()) { Text("Assign songs in Library") }
                    }
                    "Library" -> {
                        Text("Your Library",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf("All","Folder","Artist","Album","Genre","Year","Most played","Favorites","Playlists").forEach { option ->
                                FilterChip(selected=grouping==option,onClick={grouping=option;selectedGroup="All"},label={Text(option)})
                            }
                        }
                        if(grouping=="Playlists") {
                            OutlinedTextField(value=playlistName,onValueChange={playlistName=it},label={Text("New playlist name")},singleLine=true)
                            Button(onClick={extras.createPlaylist(playlistName);playlistVersion++;playlistName=""}) { Text("Create playlist") }
                            if(selectedGroup!="All") {
                                OutlinedTextField(value=playlistRename,onValueChange={playlistRename=it},label={Text("Rename selected playlist")})
                                Row {
                                    TextButton(onClick={extras.renamePlaylist(selectedGroup,playlistRename);selectedGroup="All";playlistVersion++}) { Text("Rename") }
                                    TextButton(onClick={extras.deletePlaylist(selectedGroup);selectedGroup="All";playlistVersion++}) { Text("Delete playlist") }
                                }
                            }
                        }
                        val groupOptions=when(grouping) {
                            "Folder" -> songs.map { it.folder }.distinct()
                            "Artist" -> songs.map { it.artist }.distinct()
                            "Album" -> songs.map { it.album }.distinct()
                            "Genre" -> songs.map { it.genre }.distinct()
                            "Year" -> songs.map { it.year.toString() }.distinct()
                            "Playlists" -> extras.playlistNames().also { playlistVersion }
                            else -> emptyList()
                        }.sorted()
                        if(groupOptions.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                (listOf("All")+groupOptions).forEach { group -> FilterChip(selected=selectedGroup==group,onClick={selectedGroup=group},label={Text(group)}) }
                            }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("${songs.size} local songs",color=muted,modifier=Modifier.weight(1f))
                            TextButton(onClick={if(allowed)refresh() else request.launch(permission)}) { Text("Refresh") }
                        }
                        if (!allowed) Button(onClick={request.launch(permission)}) { Text("Allow music access") }
                        OutlinedTextField(value=searchQuery,onValueChange={searchQuery=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Search title, artist, album")},leadingIcon={Icon(Icons.Default.Search,null)})
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(enabled=allowed && analysisProgress==null && songs.isNotEmpty(),onClick={
                            scope.launch {
                                analysisProgress="Analyzing local tracks…"
                                analyzed=0
                                for ((index,song) in songs.withIndex()) {
                                    analysisProgress="Analyzing ${index+1}/${songs.size}: ${song.title}"
                                    if (analysisStore.read(song.id,song.duration)==null) {
                                        analyzer.analyze(song.uri)?.let { analysisStore.save(song.id,song.duration,it); analyzed++ }
                                    }
                                }
                                analysisProgress=null
                            }
                        }) { Text("Analyze BPM + energy offline") }
                        analysisProgress?.let { Text(it,color=accent,fontSize=12.sp) }
                        if (analyzed>0) Text("$analyzed songs analyzed",color=muted,fontSize=12.sp)
                        error?.let { Text(it,color=Color(0xFFFF9C9C)) }
                        val filtered=songs.filter { song ->
                            (searchQuery.isBlank() || song.title.contains(searchQuery,true) || song.artist.contains(searchQuery,true) || song.album.contains(searchQuery,true)) && when(grouping) {
                                "Folder" -> selectedGroup=="All" || song.folder==selectedGroup
                                "Artist" -> selectedGroup=="All" || song.artist==selectedGroup
                                "Album" -> selectedGroup=="All" || song.album==selectedGroup
                                "Genre" -> selectedGroup=="All" || song.genre==selectedGroup
                                "Year" -> selectedGroup=="All" || song.year.toString()==selectedGroup
                                "Favorites" -> favorites.getBoolean(song.id.toString(),false)
                                "Playlists" -> selectedGroup=="All" || extras.playlist(selectedGroup).contains(song.id)
                                else -> true
                            }
                        }.let { list -> if(grouping=="Most played")list.sortedByDescending { extras.plays(it.id) } else list }
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            items(filtered,key={it.id}) { song ->
                                Row(Modifier.fillMaxWidth().clickable { play(filtered,filtered.indexOf(song)) }.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote,null,tint=accent,modifier=Modifier.size(36.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text("${song.artist} • ${store.cell(song.id)?.let { names[it] } ?: "Unclassified"} • ${analysisStore.read(song.id,song.duration)?.bpm?.toInt()?.let { "$it BPM" } ?: "BPM unknown"}",fontSize=12.sp,color=muted,maxLines=1)
                                    }
                                    IconButton(onClick={
                                        val key=song.id.toString()
                                        favorites.edit().putBoolean(key,!favorites.getBoolean(key,false)).apply()
                                        favoritesVersion++
                                    }) { Icon(if(favorites.getBoolean(song.id.toString(),false)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favorite",tint=accent) }
                                    IconButton(onClick={
                                        val intent=Intent(Intent.ACTION_SEND).apply {
                                            type="audio/*"; putExtra(Intent.EXTRA_STREAM,song.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            clipData=android.content.ClipData.newUri(context.contentResolver,song.title,song.uri)
                                        }
                                        context.startActivity(Intent.createChooser(intent,"Send audio file"))
                                    }) { Icon(Icons.Default.Share,"Share audio file",tint=accent) }
                                    IconButton(onClick={selectedForPlaylist=song}) { Icon(Icons.Default.PlaylistAdd,"Add to playlist",tint=accent) }
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
                        Spacer(Modifier.height(10.dp))
                        if(lyricsVisible) {
                            Surface(color=surface,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().clickable { lyricsExpanded=!lyricsExpanded }) {
                                Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    val line=lyrics?.currentLine((position+lyricsOffset).coerceAtLeast(0L))
                                    Text(line ?: if(lyricsLoading)"Searching lyrics..." else if(lyrics?.plain!=null)"Lyrics available — tap to read" else "Lyrics not available",color=Color.White,maxLines=if(lyricsExpanded)Int.MAX_VALUE else 1,overflow=TextOverflow.Ellipsis)
                                    if(lyricsExpanded) {
                                        val content=lyrics?.plain ?: lyrics?.lines?.joinToString("\n") { it.text } ?: "No lyrics found"
                                        Text(content,color=muted,fontSize=13.sp,modifier=Modifier.heightIn(max=180.dp).verticalScroll(rememberScrollState()))
                                        Text(lyrics?.source ?: "",color=accent,fontSize=11.sp)
                                        Row(verticalAlignment=Alignment.CenterVertically) {
                                            TextButton(onClick={current?.let { song -> lyricsOffset=(lyricsOffset-500L).coerceAtLeast(-15000L);lyricsRepository.setOffsetMs(song.title,song.artist,song.duration,lyricsOffset) }}) { Text("−0.5s") }
                                            Text("Sync ${lyricsOffset/1000.0}s",color=muted,fontSize=12.sp)
                                            TextButton(onClick={current?.let { song -> lyricsOffset=(lyricsOffset+500L).coerceAtMost(15000L);lyricsRepository.setOffsetMs(song.title,song.artist,song.duration,lyricsOffset) }}) { Text("+0.5s") }
                                        }
                                    }
                                }
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={lyricsVisible=!lyricsVisible}) { Text(if(lyricsVisible)"Hide lyrics" else "Show lyrics") }
                            TextButton(onClick={lrcPicker.launch(arrayOf("text/*","application/octet-stream"))}) { Text("Import LRC") }
                            TextButton(onClick={lrcSaver.launch("${current?.title ?: "lyrics"}.lrc")}) { Text("Save lyrics") }
                            TextButton(onClick={dancerVisible=!dancerVisible}) { Text(if(dancerVisible)"Hide dancer" else "Dance") }
                        }
                        lyricsMessage?.let { Text(it,color=muted,fontSize=12.sp) }
                        Spacer(Modifier.height(8.dp))
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
                                    val intent=Intent(Intent.ACTION_SEND).apply { type="audio/*"; putExtra(Intent.EXTRA_STREAM,song.uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);clipData=android.content.ClipData.newUri(context.contentResolver,song.title,song.uri) }
                                    context.startActivity(Intent.createChooser(intent,"Share song"))
                                }
                            }) { Icon(Icons.Default.Share,"Share",tint=Color.White) }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={shuffleOn=!shuffleOn;controller?.shuffleModeEnabled=shuffleOn}) { Text(if(shuffleOn)"Shuffle On" else "Shuffle Off") }
                            TextButton(onClick={repeatMode=when(repeatMode) { Player.REPEAT_MODE_OFF->Player.REPEAT_MODE_ALL;Player.REPEAT_MODE_ALL->Player.REPEAT_MODE_ONE;else->Player.REPEAT_MODE_OFF };controller?.repeatMode=repeatMode}) { Text(when(repeatMode){Player.REPEAT_MODE_ONE->"Repeat One";Player.REPEAT_MODE_ALL->"Repeat All";else->"Repeat Off"}) }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                            Text("Sleep timer",color=muted)
                            listOf(0,15,30,45,60).forEach { min ->
                                FilterChip(selected=sleepMinutes==min,onClick={sleepMinutes=min},label={Text(if(min==0)"Off" else "${min}m")})
                            }
                        }
                        if(sleepRemaining>0)Text("Stopping in ${sleepRemaining/60}m ${sleepRemaining%60}s",color=muted,fontSize=12.sp)
                        TextButton(onClick={queueExpanded=!queueExpanded}) { Text(if(queueExpanded)"Hide play queue" else "Show play queue") }
                        if(queueExpanded && controller!=null) {
                            Text("${controller.mediaItemCount} tracks in queue",color=muted,fontSize=12.sp)
                            Column(Modifier.heightIn(max=280.dp).verticalScroll(rememberScrollState())) {
                                repeat(controller.mediaItemCount) { index ->
                                    val item=controller.getMediaItemAt(index)
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(modifier=Modifier.weight(1f),onClick={controller.seekToDefaultPosition(index);controller.play()}) {
                                            Text("${index+1}. ${item.mediaMetadata.title ?: item.mediaId}",maxLines=1,overflow=TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick={
                                            if(index>0)controller.moveMediaItem(index,index-1)
                                        },enabled=index>0) { Icon(Icons.Default.KeyboardArrowUp,"Move up",tint=accent) }
                                        IconButton(onClick={controller.removeMediaItem(index)}) { Icon(Icons.Default.Close,"Remove from queue",tint=accent) }
                                    }
                                }
                            }
                        }
                        Text("Playback speed: ${"%.2f".format(java.util.Locale.US,playbackSpeed)}×",color=muted)
                        Slider(value=playbackSpeed,onValueChange={playbackSpeed=it;controller?.setPlaybackSpeed(it)},valueRange=0.5f..2f)
                        if(lyricsExpanded) {
                            OutlinedTextField(value=manualLyrics,onValueChange={manualLyrics=it},label={Text("Paste or edit lyrics")},modifier=Modifier.fillMaxWidth())
                            TextButton(onClick={current?.let { song -> lyricsRepository.savePlain(song.title,song.artist,song.duration,manualLyrics);lyrics=lyricsRepository.saved(song.title,song.artist,song.duration);lyricsMessage="Lyrics saved offline" }}) { Text("Save manual lyrics") }
                        }
                        current?.let { song -> Text("Mood: ${store.cell(song.id)?.let { names[it] } ?: "Unclassified"}",color=muted) }
                    }
                    "Tools" -> {
                        Text("Audio Studio",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("Equalizer",color=accent,fontWeight=FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf("Flat","Bass Boost","Treble Boost","Vocal","Rock","Jazz","Classical","Dance").forEach { option ->
                                    FilterChip(selected=preset==option,onClick={preset=option;eqPrefs.edit().putString("preset",option).apply()},label={Text(option)})
                                }
                            }
                            Text("Bass boost: ${bass.toInt()}%",color=muted)
                            Slider(value=bass,onValueChange={bass=it;eqPrefs.edit().putInt("bass",it.toInt()).apply()},valueRange=0f..100f)
                            HorizontalDivider()
                            Text("Batch tag editor • MP3, FLAC, WAV, OGG, M4A",color=accent,fontWeight=FontWeight.Bold)
                            OutlinedButton(onClick={tagPicker.launch(arrayOf("audio/*","application/ogg","application/octet-stream"))}) { Text("Select audio files") }
                            Text("${selectedTagUris.size} selected",color=muted)
                            OutlinedTextField(value=tagTitle,onValueChange={tagTitle=it},label={Text("Title (blank = unchanged)")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagArtist,onValueChange={tagArtist=it},label={Text("Artist")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagAlbum,onValueChange={tagAlbum=it},label={Text("Album")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagYear,onValueChange={tagYear=it},label={Text("Year")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagGenre,onValueChange={tagGenre=it},label={Text("Genre")},modifier=Modifier.fillMaxWidth())
                            Button(enabled=!tagBusy && selectedTagUris.isNotEmpty(),onClick={
                                scope.launch {
                                    tagBusy=true
                                    val results=tagEditor.writeBatch(selectedTagUris,TagChanges(
                                        title=tagTitle.takeIf { it.isNotBlank() },
                                        artist=tagArtist.takeIf { it.isNotBlank() },
                                        album=tagAlbum.takeIf { it.isNotBlank() },
                                        year=tagYear.takeIf { it.isNotBlank() },
                                        genre=tagGenre.takeIf { it.isNotBlank() }
                                    ))
                                    tagStatus="${results.count { it.success }}/${results.size} files updated; " +
                                        results.filterNot { it.success }.take(3).joinToString { it.message }
                                    tagBusy=false
                                    refresh()
                                }
                            }) { Text(if(tagBusy)"Writing..." else "Apply tags to selected files") }
                            tagStatus?.let { Text(it,color=muted) }
                            HorizontalDivider()
                            Text("Audio converter",color=accent,fontWeight=FontWeight.Bold)
                            OutlinedButton(onClick={converterPicker.launch(arrayOf("audio/*","application/octet-stream"))}) { Text("Select source audio") }
                            Text(converterSource?.lastPathSegment ?: "No source",color=muted,maxLines=1)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                AudioEngine.Format.entries.forEach { fmt -> FilterChip(selected=converterFormat==fmt,onClick={converterFormat=fmt},label={Text(fmt.ext.uppercase())}) }
                            }
                            Text("Bitrate: ${converterBitrate} kbps",color=muted)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                listOf(96,128,192,256,320).forEach { rate -> FilterChip(selected=converterBitrate==rate,onClick={converterBitrate=rate},label={Text("${rate}k")}) }
                            }
                            Button(enabled=converterSource!=null && !converterBusy,onClick={
                                converterSaver.launch("converted.${converterFormat.ext}")
                            }) { Text(if(converterBusy)"Converting..." else "Convert and save") }
                            converterStatus?.let { Text(it,color=muted) }
                            HorizontalDivider()
                            Text("Audio cutter / silence / speed",color=accent,fontWeight=FontWeight.Bold)
                            OutlinedButton(onClick={wavPicker.launch(arrayOf("audio/*","application/octet-stream"))}) { Text("Select source to edit") }
                            Text(wavSource?.lastPathSegment ?: "No source",color=muted,maxLines=1)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                AudioEngine.Format.entries.forEach { fmt -> FilterChip(selected=cutterFormat==fmt,onClick={cutterFormat=fmt},label={Text(fmt.ext.uppercase())}) }
                            }
                            OutlinedTextField(value=wavStart,onValueChange={wavStart=it},label={Text("Start seconds")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=wavEnd,onValueChange={wavEnd=it},label={Text("End seconds (blank = end)")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=wavRemoveRanges,onValueChange={wavRemoveRanges=it},label={Text("Remove ranges: 10-15, 25-30")},modifier=Modifier.fillMaxWidth())
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=wavRemoveSilence,onCheckedChange={wavRemoveSilence=it})
                                Text("Remove long silence",color=muted)
                            }
                            if(wavRemoveSilence)OutlinedTextField(value=wavSilenceSeconds,onValueChange={wavSilenceSeconds=it},label={Text("Minimum silence seconds")})
                            Text("Speed: ${"%.2f".format(java.util.Locale.US,wavSpeed)}×",color=muted)
                            Slider(value=wavSpeed,onValueChange={wavSpeed=it},valueRange=0.5f..2f)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=cutterPitch,onCheckedChange={cutterPitch=it})
                                Text("Preserve pitch",color=muted)
                            }
                            Button(enabled=wavSource!=null && !wavBusy,onClick={cutterSaver.launch("edited.${cutterFormat.ext}")}) {
                                Text(if(wavBusy)"Processing..." else "Process and save")
                            }
                            wavStatus?.let { Text(it,color=muted) }
                        }
                    }
                    else -> {
                        Text("Settings",fontSize=30.sp,color=Color.White,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        Text("Offline-first • Local playback",color=muted)
                        Text("Favorites: ${songs.count { favorites.getBoolean(it.id.toString(),false) } + (favoritesVersion * 0)}",color=muted)
                        Text("No account or internet required.",color=muted)
                        Spacer(Modifier.height(12.dp))
                        Text("MusicSquare Player 0.3.0",color=Color.White)
                        Text("Offline backup of playlists, favorites, moods and queue",color=muted)
                        Row {
                            TextButton(onClick={backupSaver.launch("musicsquare-backup.json")}) { Text("Export backup") }
                            TextButton(onClick={backupPicker.launch(arrayOf("application/json","text/*"))}) { Text("Restore backup") }
                        }
                        backupStatus?.let { Text(it,color=muted) }
                        Spacer(Modifier.height(16.dp))

                    }
                }
            }
            if(dancerVisible && screen=="Now") {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density=androidx.compose.ui.platform.LocalDensity.current
                    val dancerWidth=with(density) { 170.dp.toPx() }
                    val dancerHeight=with(density) { 270.dp.toPx() }
                    val areaWidth=with(density) { maxWidth.toPx() }
                    val areaHeight=with(density) { maxHeight.toPx() }
                    val maxX=(areaWidth-dancerWidth).coerceAtLeast(0f)
                    val maxY=(areaHeight-dancerHeight).coerceAtLeast(0f)
                    var dancerX by remember { mutableFloatStateOf(maxX) }
                    var dancerY by remember { mutableFloatStateOf(maxY) }
                    LaunchedEffect(maxX,maxY) {
                        dancerX=dancerX.coerceIn(0f,maxX)
                        dancerY=dancerY.coerceIn(0f,maxY)
                    }
                    Box(
                        Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(dancerX.toInt(),dancerY.toInt()) }
                            .pointerInput(maxX,maxY) {
                                detectDragGestures { change,drag ->
                                    change.consume()
                                    dancerX=(dancerX+drag.x).coerceIn(0f,maxX)
                                    dancerY=(dancerY+drag.y).coerceIn(0f,maxY)
                                }
                            }
                    ) {
                        DancingCompanion(playing=playing,onDismiss={dancerVisible=false})
                    }
                }
            }
            }
        }
        selectedForPlaylist?.let { song ->
            AlertDialog(onDismissRequest={selectedForPlaylist=null},title={Text("Add to playlist")},text={
                Column { extras.playlistNames().forEach { name -> TextButton(onClick={extras.add(name,song.id);playlistVersion++;selectedForPlaylist=null}) { Text(name) } } }
            },confirmButton={TextButton(onClick={selectedForPlaylist=null}) { Text("Close") }})
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
