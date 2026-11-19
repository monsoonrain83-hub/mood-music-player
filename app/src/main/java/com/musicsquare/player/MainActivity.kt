package com.musicsquare.player

import kotlinx.coroutines.ensureActive

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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
import com.musicsquare.player.audio.AcousticMoodEstimate
import com.musicsquare.player.audio.OfflineAudioAnalyzer
import com.musicsquare.player.ml.PersonalMoodModel
import com.musicsquare.player.ml.MoodTrainingStore
import com.musicsquare.player.audio.AudioEditInput
import com.musicsquare.player.audio.AudioExportPreset
import com.musicsquare.player.audio.AudioFileInfo
import com.musicsquare.player.audio.DecodedWaveform
import com.musicsquare.player.audio.WavWaveform
import com.musicsquare.player.audio.ConversionHistory
import com.musicsquare.player.audio.AudioClipPreview
import com.musicsquare.player.audio.BatchPreflight
import com.musicsquare.player.audio.BatchAudioConverter
import com.musicsquare.player.audio.BatchConversionWorker
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.collectLatest
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
import com.musicsquare.player.tags.TagInspector
import com.musicsquare.player.tags.TagEditor
import com.musicsquare.player.tags.TagChanges
import android.net.Uri
import com.musicsquare.player.data.LocalSong
import com.musicsquare.player.data.MoodStore
import com.musicsquare.player.data.scanLocalMusic
import com.musicsquare.player.playback.MusicPlaybackService
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private fun normalizeLibraryText(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
        .replace('ي','ی').replace('ك','ک')
        .replace("\\u0640","")
        .lowercase(java.util.Locale.ROOT).trim()

private fun libraryLetter(text: String): String =
    normalizeLibraryText(text).firstOrNull()?.uppercaseChar()?.toString() ?: "#"

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
    val appearancePrefs = remember { context.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE) }
    var skin by remember { mutableStateOf(appearancePrefs.getString("skin","Midnight") ?: "Midnight") }
    val bg = when(skin) { "Daylight" -> Color(0xFFF7F5FC); "Ocean" -> Color(0xFF071D2B); "Rose" -> Color(0xFF251522); else -> Color(0xFF0C0C18) }
    val surface = when(skin) { "Daylight" -> Color(0xFFE5DFF1); "Ocean" -> Color(0xFF143B50); "Rose" -> Color(0xFF493044); else -> Color(0xFF20203A) }
    val accent = when(skin) { "Daylight" -> Color(0xFF7145B0); "Ocean" -> Color(0xFF69D9E9); "Rose" -> Color(0xFFFFA1C7); else -> Color(0xFFC6A4FF) }
    val ink = if(skin=="Daylight") Color(0xFF21152F) else Color.White
    val muted = when(skin) { "Daylight" -> Color(0xFF655D76); "Ocean" -> Color(0xFFAFD0DF); "Rose" -> Color(0xFFD8B6CC); else -> Color(0xFFB5B2D3) }

    val store = remember { MoodStore(context) }
    val analysisStore = remember { AnalysisStore(context) }
    val trainingStore = remember { MoodTrainingStore(context) }
    val analyzer = remember { OfflineAudioAnalyzer(context) }
    val extras = remember { LibraryExtras(context) }
    val queueStore=remember { PlaybackQueueStore(context) }
    val backup=remember { LibraryBackup(context) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var playlistRename by remember { mutableStateOf("") }
    var restoredQueue by remember { mutableStateOf(false) }
    val sleepPrefs=remember { context.getSharedPreferences("sleep_timer",android.content.Context.MODE_PRIVATE) }
    var sleepDeadline by remember { mutableLongStateOf(sleepPrefs.getLong("deadline_elapsed",0L)) }
    var sleepRemaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sleepDeadline,controller) {
        if(sleepDeadline>0L) {
            while(sleepDeadline>0L) {
                val remaining=(sleepDeadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                sleepRemaining=remaining
                if(remaining==0L) {
                    sleepPrefs.edit().remove("deadline_elapsed").apply()
                    sleepDeadline=0L
                    break
                }
                delay(minOf(1000L,remaining))
            }
        } else sleepRemaining=0L
    }

    val tagEditor = remember { TagEditor(context) }
    val wavEditor = remember { WavEditor(context) }
    val audioEngine = remember { AudioEngine(context) }
    val batchConverter = remember { BatchAudioConverter(context,audioEngine) }
    val batchPreflight = remember { BatchPreflight(context) }
    val clipPreview = remember { AudioClipPreview(context) }
    val wavWaveform = remember { WavWaveform(context) }
    val decodedWaveform = remember { DecodedWaveform(context) }
    val audioInspector = remember { AudioFileInfo(context) }
    val tagInspector = remember { TagInspector(context) }
    val conversionHistory = remember { ConversionHistory(context) }
    var historyVersion by remember { mutableIntStateOf(0) }
    val lyricsRepository = remember { LyricsRepository(context) }
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    var lyricsVisible by remember { mutableStateOf(true) }
    val dancerPrefs = remember { context.getSharedPreferences("dancer_settings",android.content.Context.MODE_PRIVATE) }
    var dancerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(dancerVisible) { dancerPrefs.edit().putBoolean("visible",dancerVisible).apply() }
    var manualLyrics by remember { mutableStateOf("") }
    var lyricsMessage by remember { mutableStateOf<String?>(null) }
    var lyricsOffset by remember { mutableLongStateOf(0L) }
    var converterSource by remember { mutableStateOf<Uri?>(null) }
    var converterInfo by remember { mutableStateOf<AudioFileInfo.Details?>(null) }
    LaunchedEffect(converterSource) {
        converterInfo=null
        converterSource?.let { src -> converterInfo=runCatching { audioInspector.inspect(src) }.getOrNull() }
    }
    var converterFormat by remember { mutableStateOf(AudioEngine.Format.MP3) }
    var converterBitrate by remember { mutableIntStateOf(192) }
    var converterStatus by remember { mutableStateOf<String?>(null) }
    var converterBusy by remember { mutableStateOf(false) }
    var batchSources by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var batchBusy by remember { mutableStateOf(false) }
    var batchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var batchProgress by remember { mutableStateOf("") }
    var batchStatus by remember { mutableStateOf<String?>(null) }
    var preflightStatus by remember { mutableStateOf<String?>(null) }
    var preflightBusy by remember { mutableStateOf(false) }
    var preflightPassed by remember { mutableStateOf(false) }
    LaunchedEffect(batchSources) {
        preflightStatus=null
        preflightPassed=false
        if(batchSources.isNotEmpty()) {
            preflightBusy=true
            try {
                val report=batchPreflight.inspect(batchSources)
                preflightPassed=report.readable==batchSources.size && report.problems.isEmpty()
                preflightStatus="${report.readable}/${batchSources.size} readable · ${"%.1f".format(java.util.Locale.US,report.totalBytes/1048576.0)} MB known input · ${report.unknownSizes} unknown sizes" +
                    report.problems.take(2).joinToString(separator="; ",prefix=if(report.problems.isEmpty())"" else " · Issues: ")
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(e:Exception) { preflightStatus=e.message ?: "Preflight failed" }
            finally { preflightBusy=false }
        }
    }
    var tagFormat by remember { mutableStateOf(AudioEngine.Format.MP3) }
    var tagExportSource by remember { mutableStateOf<Uri?>(null) }
    var tagExportStatus by remember { mutableStateOf<String?>(null) }
    var wavSource by remember { mutableStateOf<Uri?>(null) }
    var cutterInfo by remember { mutableStateOf<AudioFileInfo.Details?>(null) }
    LaunchedEffect(wavSource) {
        cutterInfo=null
        wavSource?.let { src -> cutterInfo=runCatching { audioInspector.inspect(src) }.getOrNull() }
    }
    var cutterFormat by remember { mutableStateOf(AudioEngine.Format.MP3) }
    var cutterBitrate by remember { mutableIntStateOf(192) }
    var cutterPitch by remember { mutableStateOf(true) }
    var wavStart by remember { mutableStateOf("0") }
    var wavEnd by remember { mutableStateOf("") }
    var wavFadeIn by remember { mutableStateOf("0") }
    var wavFadeOut by remember { mutableStateOf("0") }
    var wavNormalize by remember { mutableStateOf(false) }
    var wavRemoveRanges by remember { mutableStateOf("") }
    var wavSpeed by remember { mutableFloatStateOf(1f) }
    var wavRemoveSilence by remember { mutableStateOf(false) }
    var wavSilenceSeconds by remember { mutableStateOf("1.5") }
    var wavStatus by remember { mutableStateOf<String?>(null) }
    var wavBusy by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var previewPosition by remember { mutableLongStateOf(0L) }
    var previewDuration by remember { mutableLongStateOf(0L) }
    var waveformPeaks by remember { mutableStateOf<WavWaveform.Peaks?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { previewJob?.cancel();clipPreview.stop() } }
    LaunchedEffect(wavSource) {
        previewJob?.cancel();clipPreview.stop();previewPosition=0L;previewDuration=0L;previewError=null;waveformPeaks=null
        val source=wavSource ?: return@LaunchedEffect
        waveformPeaks=runCatching { wavWaveform.load(source) ?: decodedWaveform.load(source) }.getOrNull()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val retriever=android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context,source)
                val duration=retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { previewDuration=duration }
            } catch(e:Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { previewError="Preview duration unavailable" }
            } finally { retriever.release() }
        }
    }
    val eqPrefs = remember { context.getSharedPreferences("equalizer",android.content.Context.MODE_PRIVATE) }
    var preset by remember { mutableStateOf(eqPrefs.getString("preset","Flat") ?: "Flat") }
    var bass by remember { mutableFloatStateOf(eqPrefs.getInt("bass",0).toFloat()) }
    var grouping by remember { mutableStateOf("All") }
    var studioPage by remember { mutableStateOf("Home") }
    var lyricsSearchVersion by remember { mutableIntStateOf(0) }
    val lyricsPrivacyPrefs = remember { context.getSharedPreferences("lyrics_privacy",android.content.Context.MODE_PRIVATE) }
    var onlineLyricsEnabled by remember { mutableStateOf(lyricsPrivacyPrefs.getBoolean("online_search",false)) }
    var lastLyricsSearchVersion by remember { mutableIntStateOf(0) }
    var selectedGroup by remember { mutableStateOf("All") }
    var selectedArtistAlbum by remember { mutableStateOf<String?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var selectedForPlaylist by remember { mutableStateOf<LocalSong?>(null) }
    var playlistVersion by remember { mutableIntStateOf(0) }
    var tagTitle by remember { mutableStateOf("") }
    var tagArtist by remember { mutableStateOf("") }
    var tagAlbum by remember { mutableStateOf("") }
    var tagYear by remember { mutableStateOf("") }
    var tagGenre by remember { mutableStateOf("") }
    var tagTrack by remember { mutableStateOf("") }
    var tagComment by remember { mutableStateOf("") }
    var tagStatus by remember { mutableStateOf<String?>(null) }
    var tagBusy by remember { mutableStateOf(false) }
    var tagReadBusy by remember { mutableStateOf(false) }
    var selectedTagUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var analysisJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var analysisProgress by remember { mutableStateOf<String?>(null) }
    var moodAnalysisSummary by remember { mutableStateOf<String?>(null) }
    var analyzed by remember { mutableIntStateOf(0) }
    var songs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf("Home") }
    var selectedCell by remember { mutableIntStateOf(-1) }
    var moodValence by remember { mutableFloatStateOf(0.5f) }
    var moodEnergy by remember { mutableFloatStateOf(0.5f) }
    var queueExpanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<LocalSong?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var repeatFolder by remember { mutableStateOf(false) }
    var assignSong by remember { mutableStateOf<LocalSong?>(null) }
    var assignmentsVersion by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var favoritesVersion by remember { mutableIntStateOf(0) }
    val favorites = remember { context.getSharedPreferences("favorites", android.content.Context.MODE_PRIVATE) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    fun refresh() {
        // Avoid overlapping MediaStore scans: an older result must not replace
        // a newer library snapshot when refresh is tapped repeatedly.
        if (!allowed || loading) return
        loading = true
        scope.launch {
            try {
                // scanLocalMusic already switches to Dispatchers.IO internally.
                songs = scanLocalMusic(context)
                error = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Unable to scan music"
            } finally {
                loading = false
            }
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
                genre=tagGenre.takeIf{it.isNotBlank()},track=tagTrack.takeIf{it.isNotBlank()},
                comment=tagComment.takeIf{it.isNotBlank()}
            ))
            tagExportStatus=result.fold(onSuccess={"Tagged copy saved"},onFailure={it.message ?: "Metadata export failed"})
            tagBusy=false
        }
    }
    val batchPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        val unique=picked.distinct()
        batchSources=if(unique.size<=200)unique else emptyList()
        unique.take(200).forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        batchStatus=if(unique.size>200)"Too many files selected: maximum 200. No files were queued; select a smaller batch." else null
    }
    val workManager=remember { WorkManager.getInstance(context) }
    LaunchedEffect(workManager) {
        workManager.getWorkInfosForUniqueWorkFlow(BatchConversionWorker.UNIQUE_NAME).collectLatest { infos ->
            val work=infos.firstOrNull() ?: return@collectLatest
            val running=work.state==WorkInfo.State.ENQUEUED || work.state==WorkInfo.State.RUNNING ||
                work.state==WorkInfo.State.BLOCKED
            batchBusy=running
            batchProgress=when(work.state) {
                WorkInfo.State.RUNNING -> "Processing ${work.progress.getInt(BatchConversionWorker.KEY_INDEX,0)} of ${work.progress.getInt(BatchConversionWorker.KEY_TOTAL,0)}"
                WorkInfo.State.ENQUEUED -> "Queued for background conversion"
                WorkInfo.State.BLOCKED -> "Waiting for earlier work"
                else -> ""
            }
            if(!running) {
                batchStatus=when(work.state) {
                    WorkInfo.State.SUCCEEDED -> "${work.outputData.getInt(BatchConversionWorker.KEY_DONE,0)} converted successfully"
                    WorkInfo.State.FAILED -> "Batch failed: ${work.outputData.getString(BatchConversionWorker.KEY_ERROR).orEmpty()}"
                    WorkInfo.State.CANCELLED -> "Batch cancelled"
                    else -> batchStatus
                }
            }
        }
    }
    val batchFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if(tree!=null && batchSources.isNotEmpty() && preflightPassed && !batchBusy) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    tree,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val request=OneTimeWorkRequestBuilder<BatchConversionWorker>()
                    .setInputData(BatchConversionWorker.request(batchSources,tree,converterFormat,converterBitrate))
                    .build()
                workManager.enqueueUniqueWork(BatchConversionWorker.UNIQUE_NAME,ExistingWorkPolicy.KEEP,request)
                batchStatus="Batch submitted to background queue"
            } catch(error:Exception) {
                batchStatus=error.message ?: "Could not schedule background conversion"
            }
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
            try {
                val edit=AudioEditInput.parse(wavStart,wavEnd,wavRemoveRanges,wavRemoveSilence,wavSilenceSeconds,wavSpeed.toDouble(),cutterPitch,wavFadeIn,wavFadeOut,wavNormalize,previewDuration.takeIf { it>0L }?.div(1000.0))
                wavStatus=edit.fold(
                    onSuccess={ valid ->
                        val result=audioEngine.convert(src,dest,cutterFormat,cutterBitrate,valid)
                        result.fold(onSuccess={"Edited audio saved and verified"},onFailure={it.message ?: "Audio edit failed"})
                    },
                    onFailure={it.message ?: "Invalid edit parameters"}
                )
                refresh()
            } catch(e:kotlinx.coroutines.CancellationException) {
                throw e
            } catch(e:Exception) {
                wavStatus=e.message ?: "Audio processing failed"
            } finally { wavBusy=false }
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
    LaunchedEffect(current?.id,lyricsSearchVersion,onlineLyricsEnabled) {
        val song=current
        lyricsLoading=false
        lyrics=null
        lyricsOffset=if(song!=null)lyricsRepository.offsetMs(song.title,song.artist,song.duration) else 0L
        if(song!=null) {
            val cached=lyricsRepository.saved(song.title,song.artist,song.duration)
            lyrics=cached
            // A user-triggered search must check online even when plain lyrics are cached.
            val manualRefresh=lyricsSearchVersion>lastLyricsSearchVersion
            lastLyricsSearchVersion=lyricsSearchVersion
            if(onlineLyricsEnabled && (cached==null || manualRefresh)) {
                lyricsLoading=true
                try {
                    val fetched=lyricsRepository.fetch(song.title,song.artist,song.album,song.duration,forceRefresh=manualRefresh)
                    if(onlineLyricsEnabled && fetched!=null) lyrics=fetched
                } finally { lyricsLoading=false }
            }
        }
    }
    fun play(list: List<LocalSong>, index: Int = 0) {
        if (list.isEmpty()) return
        val items = list.map { song ->
            MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).build()).build()
        }
        val player = controller
        if (player == null) {
            error = "Audio service is still connecting. Try again in a moment."
            return
        }
        try {
            player.setMediaItems(items, index.coerceIn(0,items.lastIndex), 0)
            player.prepare()
            player.play()
            repeatFolder=false
            current = list[index.coerceIn(0,list.lastIndex)]
            extras.played(current!!.id)
            error = null
            screen = "Now"
        } catch (e: Exception) {
            error = "Could not start playback: " + (e.localizedMessage ?: e.javaClass.simpleName)
            android.util.Log.e("MusicSquarePlayback", "Unable to play selected audio", e)
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            android.util.Log.w("MusicSquarePlayback", error!!)
        }
    }
    fun openCurrentSongTags() {

                            current?.let { song ->
                                selectedTagUris=listOf(song.uri)
                                tagTitle=song.title
                                tagArtist=song.artist
                                tagAlbum=song.album
                                tagYear=""
                                tagGenre=""
                                tagTrack=""
                                tagComment=""
                                tagStatus="Editing currently playing track. Writing may require granting access through Select audio files."
                                studioPage="Tag Editor"
                                screen="Tools"
                                scope.launch {
                                    tagReadBusy=true
                                    try {
                                        // Embedded metadata may require slow document-provider IO. Never block Compose playback controls.
                                        val tags=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            tagInspector.read(song.uri)
                                        }
                                        tagTitle=tags.title ?: song.title
                                        tagArtist=tags.artist ?: song.artist
                                        tagAlbum=tags.album ?: song.album
                                        tagYear=tags.year.orEmpty()
                                        tagGenre=tags.genre.orEmpty()
                                        tagTrack=tags.track.orEmpty()
                                    } catch(e:Exception) {
                                        tagStatus="Could not read embedded tags: ${e.message}. Select the original file if editing fails."
                                    } finally { tagReadBusy=false }
                                }
                            }
                            }
    val editCurrentSongTags: () -> Unit = { openCurrentSongTags() }
    MaterialTheme(colorScheme = darkColorScheme(primary=accent,background=bg,surface=surface)) {
        Scaffold(containerColor=bg, bottomBar={
            Column {
                current?.let { song ->
                    Row(Modifier.fillMaxWidth().background(surface).clickable { screen="Now" }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        com.musicsquare.player.visual.LocalArtwork(song.uri,Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,color=ink)
                            Text(song.artist,fontSize=12.sp,color=muted,maxLines=1)
                        }
                        IconButton(enabled=!tagBusy,onClick=editCurrentSongTags) {
                            Icon(Icons.Default.Edit,"Edit playing song tags",tint=accent)
                        }
                        IconButton(onClick={controller?.seekToPreviousMediaItem()}) {
                            Icon(Icons.Default.SkipPrevious,"Previous track",tint=ink)
                        }
                        IconButton(onClick={if(playing) controller?.pause() else controller?.play()}) {
                            Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Play or pause",tint=ink)
                        }
                        IconButton(onClick={controller?.seekToNextMediaItem()}) {
                            Icon(Icons.Default.SkipNext,"Next track",tint=ink)
                        }
                    }
                    val miniDuration=(controller?.duration ?: 0L).coerceAtLeast(0L)
                    LinearProgressIndicator(
                        progress={if(miniDuration>0) (position.toFloat()/miniDuration).coerceIn(0f,1f) else 0f},
                        modifier=Modifier.fillMaxWidth().height(2.dp),
                        color=accent,trackColor=surface
                    )
                }
                NavigationBar(containerColor=surface) {
                    listOf(Triple("Home",Icons.Default.Home,"Home"),Triple("Library",Icons.Default.LibraryMusic,"Library"),Triple("Now",Icons.Default.PlayCircle,"Player"),Triple("Square",Icons.Default.GridView,"Mood"),Triple("Tools",Icons.Default.Tune,"Studio")).forEach { (key,icon,title) ->
                        val tabColor=if(screen==key) ink else muted
                        NavigationBarItem(selected=screen==key,onClick={screen=key},
                            icon={Icon(icon,contentDescription=title,tint=tabColor)},
                            label={Text(title,color=tabColor)},
                            colors=NavigationBarItemDefaults.colors(indicatorColor=accent.copy(alpha=0.20f)))
                    }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(horizontal=18.dp).then(if(screen=="Settings" || screen=="Now" || screen=="Home" || screen=="Square") Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                Spacer(Modifier.height(18.dp))
                when(screen) {
                    "Home" -> {
                        Text("MUSIC SQUARE",fontSize=13.sp,fontWeight=FontWeight.Bold,color=accent)
                        Text("Your music, your mood",fontSize=29.sp,fontWeight=FontWeight.Bold,color=ink)
                        TextButton(onClick={screen="Settings"}) {
                            Icon(Icons.Default.Settings,contentDescription="Settings",tint=accent)
                            Text("Settings & home screen widgets")
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("${songs.size} songs in your library",color=muted)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick={screen="Library"},modifier=Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.LibraryMusic,null)
                            Spacer(Modifier.width(8.dp))
                            Text("Browse your library")
                        }
                        val unclassifiedCount=songs.count { store.cell(it.id) !in 0..24 }
                        if(unclassifiedCount>0) {
                            Spacer(Modifier.height(8.dp))
                            Text("$unclassifiedCount songs need acoustic grouping",color=accent,fontWeight=FontWeight.SemiBold)
                            Text("Scanning finds songs; offline analysis assigns them to the mood grid. This estimates listening vibe, not verified emotion.",color=muted,fontSize=12.sp)
                            Button(onClick={screen="Square"},modifier=Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.GridView,null)
                                Spacer(Modifier.width(8.dp))
                                Text("Analyze music for mood grid")
                            }
                        }
                        OutlinedButton(onClick={screen="Square"},modifier=Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.GridView,null)
                            Spacer(Modifier.width(8.dp))
                            Text("Explore the 5 × 5 mood grid")
                        }
                        OutlinedButton(onClick={screen="Tools"},modifier=Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Tune,null)
                            Spacer(Modifier.width(8.dp))
                            Text("Audio Studio")
                        }
                        if(current!=null) {
                            Spacer(Modifier.height(16.dp))
                            Text("Continue listening",color=ink,fontWeight=FontWeight.Bold)
                            current?.let { song ->
                                Row(Modifier.fillMaxWidth().clickable { screen="Now" }.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    com.musicsquare.player.visual.LocalArtwork(song.uri,Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title,color=ink,maxLines=1)
                                        Text(song.artist,color=muted,maxLines=1)
                                    }
                                    Icon(Icons.Default.ChevronRight,null,tint=accent)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Recently scanned",color=ink,fontWeight=FontWeight.Bold)
                        songs.take(8).forEach { song ->
                            Row(Modifier.fillMaxWidth().clickable { play(songs,songs.indexOf(song)) }.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                                com.musicsquare.player.visual.LocalArtwork(song.uri,Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title,color=ink,maxLines=1)
                                    Text(song.artist,color=muted,fontSize=12.sp,maxLines=1)
                                }
                                Icon(Icons.Default.PlayArrow,null,tint=accent)
                            }
                        }
                    }
                    "Settings" -> {
                        Text("Settings",fontSize=28.sp,fontWeight=FontWeight.Bold,color=ink)
                        Text("Appearance & skins",fontSize=20.sp,color=accent)
                        Text("Choose a look. Your selection is saved for next launch.",color=muted)
                        Row(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            listOf("Midnight","Daylight","Ocean","Rose").forEach { option ->
                                OutlinedButton(onClick={skin=option;appearancePrefs.edit().putString("skin",option).apply()}) {
                                    Text(if(skin==option)"✓ $option" else option)
                                }
                            }
                        }
                        Text("Privacy",fontSize=20.sp,color=accent)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked=onlineLyricsEnabled,onCheckedChange={
                                onlineLyricsEnabled=it
                                lyricsPrivacyPrefs.edit().putBoolean("online_search",it).apply()
                            })
                            Column {
                                Text("Allow online lyrics lookup",color=ink)
                                Text("Sends song title, artist, album and duration to LRCLIB over HTTPS. Off by default.",color=muted,fontSize=12.sp)
                            }
                        }
                        Text("Home screen widgets",fontSize=20.sp,color=accent)
                        Text("Choose a widget size below. On Android 8 and later, the system can ask to pin it to your home screen. You can also add all three from your phone's Widgets menu.",color=muted)
                        listOf(
                            Triple("4 × 1 • Compact controls",com.musicsquare.player.widgets.Widget1x4::class.java,1),
                            Triple("4 × 2 • Player",com.musicsquare.player.widgets.Widget2x4::class.java,2),
                            Triple("4 × 4 • Large player",com.musicsquare.player.widgets.Widget4x4::class.java,4)
                        ).forEach { (label,widgetClass,_) ->
                            OutlinedButton(onClick={
                                if(Build.VERSION.SDK_INT>=26) {
                                    val manager=context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                                    val provider=ComponentName(context,widgetClass)
                                    if(manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(provider,null,null)
                                    else android.widget.Toast.makeText(context,"Open your launcher Widgets menu to add MusicSquare",android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    android.widget.Toast.makeText(context,"Open your launcher Widgets menu to add MusicSquare",android.widget.Toast.LENGTH_LONG).show()
                                }
                            },modifier=Modifier.fillMaxWidth()) { Text(label) }
                        }
                        TextButton(onClick={screen="Home"}) { Text("Back to Home") }
                    }
                    "Square" -> {
                        Text("MUSIC SQUARE",fontSize=13.sp,fontWeight=FontWeight.Bold,color=muted)
                        Text("Find your mood",fontSize=32.sp,fontWeight=FontWeight.Bold,color=ink)
                        Text("Energy →   •   Estimated mood ↑",color=muted,fontSize=13.sp)
                         Text("${songs.size} scanned • ${songs.count { store.cell(it.id) in 0..24 } + assignmentsVersion*0} classified • ${songs.count { store.cell(it.id) !in 0..24 }} unclassified",color=muted,fontSize=12.sp)
                        Button(enabled=allowed && songs.any { store.cell(it.id) !in 0..24 } && analysisProgress==null,
                            onClick={
                                // Mark the operation busy synchronously: rapid taps must not
                                // enqueue multiple full-library decoders before launch starts.
                                analysisProgress="Starting offline analysis"
                                moodAnalysisSummary=null
                                analysisJob=scope.launch {
                                    // Freeze the target library for this run. A refresh can replace
                                    // Compose's song list while decoding is in progress.
                                    val batch = songs.toList()
                                    var successes=0
                                    var assigned=0
                                    var unreadable=0
                                    var readableUnclassified=0
                                    var classifiedAlready=0
                                    var reusedCached=0
                                    var decodedFresh=0
                                    try {
                                        // Loading training examples may read cached audio features for the entire library.
                                        // Keep that disk work off the UI thread before starting the scan.
                                        // A malformed training preference or cached feature must not
                                        // prevent acoustic fallback for the entire music library.
                                        // Preserve cancellation; only isolate recoverable training errors.
                                        val personalExamples = try {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                trainingStore.examples(batch, analysisStore)
                                            }
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            android.util.Log.w("MusicSquareMood", "Personal training unavailable; using acoustic fallback", error)
                                            emptyList()
                                        }
                                        batch.forEachIndexed { index,song ->
                                            // Yield on every track, including skipped ones, so cancellation
                                            // remains responsive during large cached libraries.
                                            kotlinx.coroutines.yield()
                                            // Cache reads, classifier predictions and storage can also
                                            // fail. Keep processing the remaining tracks.
                                            try {
                                            if (store.cell(song.id) in 0..24) {
                                                analysisProgress="Checking ${index+1}/${batch.size}: already grouped"
                                                classifiedAlready++
                                                return@forEachIndexed
                                            }
                                            analysisProgress="Analyzing ${index+1}/${batch.size}: ${song.title}"
                                            // A corrupt cached energy value must not permanently
                                            // strand a readable track in the unclassified bucket.
                                            // Retry decoding once and replace the cache only when
                                            // the fresh analysis contains usable energy.
                                            // A corrupt cache entry must not strand a readable track.
                                            val cached = try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    analysisStore.read(song.id, song.modified)
                                                }
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                android.util.Log.w("MusicSquareMood", "Cache read failed for track ${song.id}; decoding audio", error)
                                                null
                                            }
                                            val result = if (cached != null && cached.energy.isFinite() &&
                                                cached.energy in 0f..1f) {
                                                cached.also { reusedCached++ }
                                            } else {
                                                // One unreadable or malformed media file must not abort
                                                // the remaining library. Preserve coroutine cancellation.
                                                try {
                                                    // Decoding can block for seconds on large or damaged files.
                                                    // Keep it off Compose's main thread so playback and Cancel
                                                    // controls remain responsive throughout a library scan.
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        analyzer.analyze(song.uri)?.also { fresh ->
                                                            decodedFresh++
                                                            if (fresh.energy.isFinite() && fresh.energy in 0f..1f) {
                                                                // Cache persistence is best-effort: a full or
                                                                // unwritable cache must not discard decoded audio
                                                                // and prevent this track from being classified.
                                                                try {
                                                                    analysisStore.save(song.id, song.modified, fresh)
                                                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                                    throw cancelled
                                                                } catch (error: Exception) {
                                                                    android.util.Log.w("MusicSquareMood", "Could not cache decoded track ${song.id}; continuing with fresh features", error)
                                                                }
                                                            }
                                                        }
                                                    }
                                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                    throw cancelled
                                                } catch (error: Exception) {
                                                    android.util.Log.w("MusicSquareMood", "Analysis failed for track ${song.id}", error)
                                                    null
                                                }
                                            }
                                            // Reject invalid decoder energy before counting the track as analyzed.
                                            // Otherwise an unusable result is reported as readable but unclassified.
                                            if(result != null && result.energy.isFinite() && result.energy in 0f..1f) {
                                                if(store.cell(song.id) !in 0..24) {
                                                    // User-trained supervised model abstains when evidence is insufficient.
                                                    val prediction=PersonalMoodModel.predict(result.energy,result.bpm,personalExamples,song.id)
                                                    // Never fabricate neutral valence: abstain unless a
                                                    // supervised prediction or actual valence is available.
                                                    // Reject corrupt cached analysis instead of passing NaN
                                                    // into MoodCoordinates.cell (which throws on non-finite input).
                                                    val inferredCell = if (result.energy.isFinite() && result.energy in 0f..1f) {
                                                        prediction?.cell?.takeIf { it in 0..24 } ?: (result.valence?.takeIf { it.isFinite() && it in 0f..1f }
                                                            ?: AcousticMoodEstimate.estimate(result))?.let { value ->
                                                            MoodCoordinates.cell(value, result.energy)
                                                        }
                                                    } else null
                                                    if(inferredCell!=null) {
                                                        store.assign(song.id,inferredCell)
                                                        assignmentsVersion++
                                                        assigned++
                                                    } else {
                                                        readableUnclassified++
                                                    }
                                                }
                                                successes++
                                            } else unreadable++
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                unreadable++
                                                android.util.Log.w("MusicSquareMood", "Mood grouping failed for track ${song.id}", error)
                                            }
                                        }
                                        analyzed=successes
                                        moodAnalysisSummary="Finished: $assigned newly grouped, $classifiedAlready already grouped (skipped), $readableUnclassified readable but unclassified, $unreadable decode failures. $successes readable processed ($reusedCached cached, $decodedFresh freshly decoded)."
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        moodAnalysisSummary="Analysis cancelled. $assigned newly grouped, $classifiedAlready already grouped (skipped), $unreadable decode failures before cancellation."
                                        throw cancelled
                                    } finally { analysisProgress=null }
                                }
                            },modifier=Modifier.fillMaxWidth()) { Text("Analyze unclassified songs") }
                        analysisProgress?.let { progress ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(progress,color=accent,fontSize=12.sp,modifier=Modifier.weight(1f))
                                TextButton(onClick={analysisJob?.cancel()}) { Text("Cancel") }
                            }
                        }
                        moodAnalysisSummary?.let { Text(it,color=muted,fontSize=12.sp) }
                        Text("Personal learning: ${trainingStore.exampleCount()} manual labels. Cached features are checked off the UI thread when analysis starts; at least 5 diverse usable labels are needed.",color=muted,fontSize=11.sp)
                        Text("Automatic placements are acoustic listening groups, NOT verified happiness or emotion. If tempo is unavailable, readable songs are grouped by energy in the middle column. Unreadable files remain unclassified.",color=muted,fontSize=11.sp)
                        TextButton(onClick={trainingStore.clear();assignmentsVersion++}) { Text("Reset personal training labels") }
                        Spacer(Modifier.height(20.dp))
                        // Read assignments once per grid refresh instead of querying persistent
                        // storage 25 times per track on every recomposition.
                        val moodCounts = remember(songs, assignmentsVersion) {
                            IntArray(25).also { counts ->
                                songs.forEach { song ->
                                    store.cell(song.id)?.takeIf { it in 0..24 }?.let { counts[it]++ }
                                }
                            }
                        }
                        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            repeat(5) { row ->
                                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                    repeat(5) { col ->
                                        val cell=row*5+col
                                        val count=moodCounts[cell]
                                        Column(Modifier.weight(1f).height(58.dp)
                                            .background(palette[(col+row)%5].copy(alpha=if(selectedCell==cell)1f else .72f),RoundedCornerShape(10.dp))
                                            .clickable { selectedCell=cell },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                            Text(names[cell],fontSize=9.sp,color=ink,maxLines=1)
                                            Text("$count",fontSize=14.sp,color=ink,fontWeight=FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(if(selectedCell<0)"Select a mood cell" else names[selectedCell],fontSize=22.sp,color=ink,fontWeight=FontWeight.Bold)
                        if(selectedCell>=0) {
                            val moodSongs=songs.filter { store.cell(it.id)==selectedCell }
                            Text("${moodSongs.size} tracks in this mood",color=muted,fontSize=12.sp)
                            if(moodSongs.isEmpty()) Text("No tracks assigned yet. Choose another mood or assign songs from Library.",color=muted,fontSize=12.sp)
                            moodSongs.take(40).forEachIndexed { index,song ->
                                Row(Modifier.fillMaxWidth().clickable { play(moodSongs,index) }.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                                    com.musicsquare.player.visual.LocalArtwork(song.uri,Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title,color=ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text(song.artist,color=muted,fontSize=12.sp,maxLines=1)
                                    }
                                    Icon(Icons.Default.PlayArrow,"Play track",tint=accent)
                                }
                            }
                            if(moodSongs.size>40) Text("Showing first 40 tracks. Use Play this mood for all.",color=muted,fontSize=12.sp)
                        }
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
                        Text("Your Library",fontSize=30.sp,fontWeight=FontWeight.Bold,color=ink)
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf("All","Folder","Artist","Album","Genre","Year","Most played","Favorites","Playlists","Energetic","Relaxed","Fast tempo","Unanalyzed").forEach { option ->
                                FilterChip(selected=grouping==option,onClick={grouping=option;selectedGroup="All";selectedArtistAlbum=null},label={Text(option)})
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
                        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { normalizeLibraryText(it) })
                        if(groupOptions.isNotEmpty() && selectedGroup!="All" && searchQuery.isBlank()) {
                            TextButton(onClick={selectedGroup="All";selectedArtistAlbum=null}) { Text("‹ Back to $grouping",color=accent) }
                            Text(selectedGroup,fontSize=22.sp,color=ink,fontWeight=FontWeight.Bold)
                        }
                        if(grouping=="Artist" && selectedGroup!="All" && searchQuery.isBlank()) {
                            val artistAlbums=songs.filter { it.artist==selectedGroup }.map { it.album }.distinct().sorted()
                            if(selectedArtistAlbum!=null) {
                                TextButton(onClick={selectedArtistAlbum=null}) { Text("‹ All albums by $selectedGroup") }
                                Text(selectedArtistAlbum ?: "",fontSize=18.sp,color=accent)
                            } else {
                                Text("Albums",fontSize=18.sp,color=accent,fontWeight=FontWeight.Bold)
                                artistAlbums.forEach { album ->
                                    Row(Modifier.fillMaxWidth().clickable { selectedArtistAlbum=album }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Icon(Icons.Default.Album,null,tint=accent)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(album,color=ink)
                                            Text("${songs.count { it.artist==selectedGroup && it.album==album }} tracks",color=muted,fontSize=12.sp)
                                        }
                                        Icon(Icons.Default.ChevronRight,null,tint=accent)
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("${songs.size} local songs",color=muted,modifier=Modifier.weight(1f))
                            TextButton(onClick={if(allowed)refresh() else request.launch(permission)}) { Text("Refresh") }
                        }
                        if (!allowed) Button(onClick={request.launch(permission)}) { Text("Allow music access") }
                        OutlinedTextField(value=searchQuery,onValueChange={searchQuery=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Search title, artist, album")},leadingIcon={Icon(Icons.Default.Search,null)})
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(enabled=allowed && analysisProgress==null && songs.any { store.cell(it.id) !in 0..24 },onClick={
                            // Claim the shared analysis slot before launching the coroutine.
                            // Otherwise rapid taps can enqueue multiple library decoders.
                            analysisProgress="Analyzing local tracks…"
                            moodAnalysisSummary=null
                            analysisJob=scope.launch {
                                val batch=songs.toList()
                                analyzed=0
                                var newlyGrouped=0
                                var decodeFailures=0
                                var stillUnclassified=0
                                try {
                                    // Match the Mood screen's classification priority: learned
                                    // labels first, then valid valence, then acoustic proxy.
                                    // Training examples may read many cached feature files; keep disk IO off Compose.
                                    // A damaged training cache must not abort acoustic grouping.
                                    val personalExamples = try {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            trainingStore.examples(batch, analysisStore)
                                        }
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        android.util.Log.w("MusicSquareMood", "Library training unavailable; using acoustic fallback", error)
                                        emptyList()
                                    }
                                    for ((index,song) in batch.withIndex()) {
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        analysisProgress="Analyzing ${index+1}/${batch.size}: ${song.title}"
                                        // Already-grouped tracks do not need another decode or cache read.
                                        // Match the incremental behavior of the Mood screen and
                                        // preserve existing manual assignments.
                                        if (store.cell(song.id) in 0..24) continue
                                        // Feature scans should populate the grid, not just cache BPM.
                                        // Reuse valid cache; decode invalid or missing features on IO.
                                        try {
                                            // A corrupt cache entry must not strand a readable track.
                                            val cached = try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    analysisStore.read(song.id, song.modified)
                                                }
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                android.util.Log.w("MusicSquareMood", "Cache read failed for track ${song.id}; decoding audio", error)
                                                null
                                            }
                                            val features = if (cached != null && cached.energy.isFinite() && cached.energy in 0f..1f) {
                                                cached
                                            } else {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    analyzer.analyze(song.uri)
                                                }?.takeIf { it.energy.isFinite() && it.energy in 0f..1f }
                                                    ?.also { features ->
                                                        // A cache write failure must not turn a successful
                                                        // decode into an unclassified track.
                                                        try {
                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                                analysisStore.save(song.id, song.modified, features)
                                                            }
                                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                            throw cancelled
                                                        } catch (error: Exception) {
                                                            android.util.Log.w("MusicSquareMood", "Could not cache library track ${song.id}; continuing with fresh features", error)
                                                        }
                                                    }
                                            }
                                            if (features != null) {
                                                analyzed++
                                                if (store.cell(song.id) !in 0..24) {
                                                    val prediction = PersonalMoodModel.predict(
                                                        features.energy, features.bpm, personalExamples, song.id
                                                    )
                                                    val inferredCell = prediction?.cell?.takeIf { it in 0..24 }
                                                        ?: (features.valence?.takeIf { it.isFinite() && it in 0f..1f }
                                                            ?: AcousticMoodEstimate.estimate(features))?.let {
                                                            MoodCoordinates.cell(it, features.energy)
                                                        }
                                                    if (inferredCell != null) {
                                                        store.assign(song.id, inferredCell)
                                                        assignmentsVersion++
                                                        newlyGrouped++
                                                    } else stillUnclassified++
                                                }
                                            } else decodeFailures++
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            decodeFailures++
                                            android.util.Log.w("MusicSquareMood", "Library feature scan failed for track ${song.id}", error)
                                        }
                                    }
                                    moodAnalysisSummary="Library analysis: $newlyGrouped newly grouped, $stillUnclassified readable but unclassified, $decodeFailures decode failures."
                                } finally {
                                    analysisProgress=null
                                }
                            }
                        }) { Text("Analyze audio + group on mood grid") }
                        analysisProgress?.let { progress ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(progress,color=accent,fontSize=12.sp,modifier=Modifier.weight(1f))
                                TextButton(onClick={analysisJob?.cancel()}) { Text("Cancel analysis") }
                            }
                        }
                        if (songs.isNotEmpty()) Text("${songs.size} scanned • ${songs.count { store.cell(it.id) in 0..24 }} grouped • ${songs.count { store.cell(it.id) !in 0..24 }} unclassified",color=muted,fontSize=12.sp)
                        moodAnalysisSummary?.let { Text(it,color=muted,fontSize=12.sp) }
                        error?.let { Text(it,color=Color(0xFFFF9C9C)) }
                        val filtered=songs.filter { song ->
                            (searchQuery.isBlank() || listOf(song.title,song.artist,song.album,song.folder,song.genre).any { normalizeLibraryText(it).contains(normalizeLibraryText(searchQuery)) }) && (searchQuery.isNotBlank() || when(grouping) {
                                "Folder" -> selectedGroup=="All" || song.folder==selectedGroup
                                "Artist" -> (selectedGroup=="All" || song.artist==selectedGroup) && (selectedArtistAlbum==null || song.album==selectedArtistAlbum)
                                "Album" -> selectedGroup=="All" || song.album==selectedGroup
                                "Genre" -> selectedGroup=="All" || song.genre==selectedGroup
                                "Year" -> selectedGroup=="All" || song.year.toString()==selectedGroup
                                "Favorites" -> favorites.getBoolean(song.id.toString(),false)
                                "Energetic","Relaxed","Fast tempo","Unanalyzed" ->
                                    com.musicsquare.player.data.SmartLibraryFilters.matches(grouping,analysisStore.read(song.id, song.modified))
                                "Playlists" -> selectedGroup=="All" || extras.playlist(selectedGroup).contains(song.id)
                                else -> true
                            })
                        }.let { list -> if(grouping=="Most played" && searchQuery.isBlank()) list.sortedByDescending { extras.plays(it.id) } else list.sortedWith(compareBy { normalizeLibraryText(it.title) }) }
                        if(grouping in listOf("Energetic","Relaxed","Fast tempo","Unanalyzed") && searchQuery.isBlank()) {
                            Text("${filtered.size} tracks • On-device audio features only",color=muted,fontSize=12.sp)
                            if(filtered.isEmpty()) Text("No matching analyzed tracks. Run offline BPM + energy analysis to populate this view.",color=muted,fontSize=12.sp)
                        }
                        if(searchQuery.isNotBlank()) {
                            Text("${filtered.size} matching songs",color=accent,fontSize=12.sp)
                            if(filtered.isEmpty()) Text("No matching audio found. Try a different title or artist.",color=muted,fontSize=12.sp)
                        }
                        val azGroups = groupOptions.isNotEmpty() && selectedGroup=="All" && searchQuery.isBlank()
                        val azSource = if(azGroups) groupOptions else filtered.map { it.title }
                        val azLetters = azSource.map(::libraryLetter).distinct().sorted()
                        val azState = androidx.compose.foundation.lazy.rememberLazyListState()
                        Row(Modifier.fillMaxWidth()) {
                        LazyColumn(state=azState,modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            if(groupOptions.isNotEmpty() && selectedGroup=="All" && searchQuery.isBlank()) {
                                items(groupOptions) { group ->
                                    Row(Modifier.fillMaxWidth().clickable { selectedGroup=group }.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Icon(Icons.Default.FolderOpen,null,tint=accent)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(group.ifBlank { "Unknown" },color=ink,fontSize=17.sp)
                                            Text("${songs.count { song -> when(grouping) { "Folder"->song.folder==group; "Artist"->song.artist==group; "Album"->song.album==group; "Genre"->song.genre==group; "Year"->song.year.toString()==group; "Playlists"->extras.playlist(group).contains(song.id); else->false } }} tracks",color=muted,fontSize=12.sp)
                                        }
                                        Icon(Icons.Default.ChevronRight,null,tint=accent)
                                    }
                                }
                            } else if(searchQuery.isNotBlank() || grouping!="Artist" || selectedArtistAlbum!=null) items(filtered,key={it.id}) { song ->
                                Row(Modifier.fillMaxWidth().clickable { play(filtered,filtered.indexOf(song)) }.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                    com.musicsquare.player.visual.LocalArtwork(song.uri,Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title,color=ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text("${song.artist} • ${store.cell(song.id)?.takeIf { it in names.indices }?.let { names[it] } ?: "Unclassified"} • ${analysisStore.read(song.id, song.modified)?.bpm?.toInt()?.let { "$it BPM" } ?: "BPM unknown"}",fontSize=12.sp,color=muted,maxLines=1)
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
                                        try {
                                            context.startActivity(Intent.createChooser(intent,"Send audio file"))
                                        } catch (e: Exception) {
                                            android.util.Log.e("MusicSquareShare","Cannot share audio",e)
                                            android.widget.Toast.makeText(context,"Unable to share this audio file",android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }) { Icon(Icons.Default.Share,"Share audio file",tint=accent) }
                                    IconButton(onClick={selectedForPlaylist=song}) { Icon(Icons.Default.PlaylistAdd,"Add to playlist",tint=accent) }
                                    TextButton(onClick={assignSong=song}) { Text("Mood") }
                                }
                            }
                        }
                        if(azLetters.size>1) {
                            Column(Modifier.width(30.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                                azLetters.forEach { letter ->
                                    TextButton(onClick={
                                        val position=azSource.indexOfFirst { libraryLetter(it)==letter }
                                        if(position>=0) scope.launch { azState.scrollToItem(position) }
                                    },contentPadding=PaddingValues(0.dp),modifier=Modifier.height(28.dp)) {
                                        Text(letter,color=accent,fontSize=11.sp)
                                    }
                                }
                            }
                        }
                        }
                    }
                    "Now" -> {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Now Playing",fontSize=24.sp,fontWeight=FontWeight.Bold,color=ink)
                            TextButton(onClick={dancerVisible=!dancerVisible}) {
                                Icon(Icons.Default.Person,contentDescription="Toggle dancer",tint=accent)
                                Text(if(dancerVisible)"Hide dancer" else "Show dancer")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        com.musicsquare.player.visual.LocalArtwork(
                            uri=current?.uri,
                            modifier=Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(20.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(current?.title ?: "No song selected",fontSize=20.sp,fontWeight=FontWeight.Bold,color=ink,maxLines=2)
                        Text(current?.artist ?: "Choose music from your library",color=muted)
                        OutlinedButton(enabled=current!=null && !tagBusy,onClick=editCurrentSongTags) { Icon(Icons.Default.Edit,"Edit tags",tint=ink) }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                            var sleepMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick={sleepMenuExpanded=true}) {
                                    Icon(Icons.Default.Bedtime,contentDescription="Sleep timer",tint=if(sleepDeadline>0L)accent else muted)
                                    Text(if(sleepDeadline>0L)"${(sleepRemaining+59999L)/60000L}m" else "Timer",fontSize=11.sp,color=ink)
                                }
                                DropdownMenu(expanded=sleepMenuExpanded,onDismissRequest={sleepMenuExpanded=false}) {
                                    listOf(5,10,15,30,45,60,90,120).forEach { minutes ->
                                        DropdownMenuItem(text={Text("$minutes minutes")},onClick={
                                            sleepDeadline=android.os.SystemClock.elapsedRealtime()+minutes*60_000L
                                            sleepPrefs.edit().putLong("deadline_elapsed",sleepDeadline).apply()
                                            sleepMenuExpanded=false
                                        })
                                    }
                                    if(sleepDeadline>0L)DropdownMenuItem(text={Text("Cancel timer")},onClick={
                                        sleepDeadline=0L
                                        sleepPrefs.edit().remove("deadline_elapsed").apply()
                                        sleepMenuExpanded=false
                                    })
                                }
                            }
                            IconButton(onClick={shuffleOn=!shuffleOn;controller?.shuffleModeEnabled=shuffleOn}) {
                                Icon(Icons.Default.Shuffle,contentDescription="Shuffle",tint=if(shuffleOn)accent else muted)
                            }
                            TextButton(onClick={
                                repeatFolder=false
                                repeatMode=when(repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                controller?.repeatMode=repeatMode
                            }) {
                                Icon(Icons.Default.Repeat,contentDescription="Repeat mode",tint=if(repeatMode==Player.REPEAT_MODE_OFF)muted else accent)
                                Text(when(repeatMode) { Player.REPEAT_MODE_ONE -> "1"; Player.REPEAT_MODE_ALL -> "All"; else -> "Off" },fontSize=11.sp)
                            }
                            TextButton(enabled=current!=null,onClick={
                                val song=current
                                if(song!=null) {
                                    val folderSongs=songs.filter { it.folder==song.folder }
                                    if(folderSongs.isNotEmpty()) {
                                        repeatFolder=true
                                        repeatMode=Player.REPEAT_MODE_ALL
                                        val index=folderSongs.indexOfFirst { it.id==song.id }.coerceAtLeast(0)
                                        val items=folderSongs.map {
                                            MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString())
                                                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).build()).build()
                                        }
                                        controller?.apply {
                                            setMediaItems(items,index,position)
                                            this.repeatMode=Player.REPEAT_MODE_ALL
                                            shuffleModeEnabled=shuffleOn
                                            prepare()
                                            if(playing)play()
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Folder,contentDescription="Repeat folder",tint=if(repeatFolder)accent else muted)
                                Text("Folder",fontSize=11.sp,color=if(repeatFolder)accent else muted)
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                            Text("Sleep timer",color=muted)
                            listOf(0,15,30,45,60).forEach { min ->
                                FilterChip(selected=if(min==0)sleepDeadline==0L else sleepDeadline>0L && (sleepRemaining+59999L)/60000L<=min && (sleepRemaining+59999L)/60000L>min-15,onClick={
                                    sleepDeadline=if(min==0)0L else android.os.SystemClock.elapsedRealtime()+min*60_000L
                                    if(min==0)sleepPrefs.edit().remove("deadline_elapsed").apply()
                                    else sleepPrefs.edit().putLong("deadline_elapsed",sleepDeadline).apply()
                                },label={Text(if(min==0)"Off" else "${min}m")})
                            }
                        }
                        if(sleepRemaining>0)Text("Stopping in ${sleepRemaining/60000}m ${(sleepRemaining/1000)%60}s",color=muted,fontSize=12.sp)
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
                                        IconButton(onClick={controller.moveMediaItem(index,index+1)},enabled=index<controller.mediaItemCount-1) {
                                            Icon(Icons.Default.KeyboardArrowDown,"Move down",tint=accent)
                                        }
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
                        current?.let { song -> Text("Mood: ${store.cell(song.id)?.takeIf { it in names.indices }?.let { names[it] } ?: "Unclassified"}",color=muted) }
                    }
                    "Tools" -> {
                        Text("Audio Studio",fontSize=30.sp,fontWeight=FontWeight.Bold,color=ink)
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            if(studioPage=="Home") {
                                Text("Edit, convert and organize your local audio",color=muted)
                                val sections=listOf(
                                    Triple("Equalizer","Tune your listening experience",Icons.Default.Equalizer),
                                    Triple("Tag Editor","Edit title, artist, album and more",Icons.Default.Edit),
                                    Triple("Converter","Export MP3, WAV, FLAC, OGG or M4A",Icons.Default.Transform),
                                    Triple("Cutter","Trim, remove sections, silence and change speed",Icons.Default.ContentCut)
                                )
                                sections.forEach { (page,description,icon) ->
                                    Card(onClick={studioPage=page},modifier=Modifier.fillMaxWidth(),
                                        colors=CardDefaults.cardColors(containerColor=surface),
                                        shape=RoundedCornerShape(18.dp)) {
                                        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Icon(icon,null,tint=accent,modifier=Modifier.size(28.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(page,color=ink,fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                                                Text(description,color=muted,fontSize=12.sp)
                                            }
                                            Icon(Icons.Default.ChevronRight,null,tint=muted)
                                        }
                                    }
                                }
                                Text("Files are processed locally. Keep a backup of irreplaceable recordings.",color=muted,fontSize=12.sp)
                            } else {
                                TextButton(onClick={studioPage="Home"}) {
                                    Icon(Icons.Default.ArrowBack,null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("All audio tools")
                                }
                                Text(studioPage,fontSize=24.sp,color=ink,fontWeight=FontWeight.Bold)
                            }
                            if(studioPage=="Equalizer") {
                            Text("Equalizer",color=accent,fontWeight=FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf("Flat","Bass Boost","Treble Boost","Vocal","Rock","Jazz","Classical","Dance").forEach { option ->
                                    FilterChip(selected=preset==option,onClick={preset=option;eqPrefs.edit().putString("preset",option).apply()},label={Text(option)})
                                }
                            }
                            Text("Bass boost: ${bass.toInt()}%",color=muted)
                            Slider(value=bass,onValueChange={bass=it;eqPrefs.edit().putInt("bass",it.toInt()).apply()},valueRange=0f..100f)
                            HorizontalDivider()
                            }
                            if(studioPage=="Tag Editor") {
                            Text("Batch tag editor • MP3, FLAC, WAV, OGG, M4A",color=accent,fontWeight=FontWeight.Bold)
                            OutlinedButton(onClick={tagPicker.launch(arrayOf("audio/*","application/ogg","application/octet-stream"))}) { Text("Select audio files") }
                            Text("${selectedTagUris.size} selected",color=muted)
                            OutlinedButton(enabled=selectedTagUris.size==1 && !tagBusy && !tagReadBusy,onClick={
                                val uri=selectedTagUris.singleOrNull() ?: return@OutlinedButton
                                scope.launch {
                                    tagReadBusy=true
                                    try {
                                        val current=tagInspector.read(uri)
                                        tagTitle=current.title.orEmpty()
                                        tagArtist=current.artist.orEmpty()
                                        tagAlbum=current.album.orEmpty()
                                        tagYear=current.year.orEmpty()
                                        tagGenre=current.genre.orEmpty()
                                        tagTrack=current.track.orEmpty()
                                        tagComment=""
                                        tagStatus="Loaded available embedded tags. Review fields before saving; blank fields stay unchanged."
                                    } catch(e:Exception) {
                                        tagStatus=e.message ?: "Cannot read embedded tags"
                                    } finally { tagReadBusy=false }
                                }
                            }) {
                                Icon(Icons.Default.Download,null)
                                Spacer(Modifier.width(8.dp))
                                Text(if(tagReadBusy)"Reading tags..." else "Load existing tags (single file)")
                            }

                            OutlinedTextField(value=tagTitle,onValueChange={tagTitle=it},label={Text("Title (blank = unchanged)")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagArtist,onValueChange={tagArtist=it},label={Text("Artist")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagAlbum,onValueChange={tagAlbum=it},label={Text("Album")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagYear,onValueChange={tagYear=it},label={Text("Year")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagGenre,onValueChange={tagGenre=it},label={Text("Genre")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagTrack,onValueChange={tagTrack=it},label={Text("Track number (blank = unchanged)")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=tagComment,onValueChange={tagComment=it},label={Text("Comment (blank = unchanged)")},modifier=Modifier.fillMaxWidth(),minLines=2)
                            Text("Batch editing changes the selected original files. Back up important recordings first.",color=muted,fontSize=12.sp)
                            Button(enabled=!tagBusy && selectedTagUris.isNotEmpty(),onClick={
                                scope.launch {
                                    tagBusy=true
                                    val results=tagEditor.writeBatch(selectedTagUris,TagChanges(
                                        title=tagTitle.takeIf { it.isNotBlank() },
                                        artist=tagArtist.takeIf { it.isNotBlank() },
                                        album=tagAlbum.takeIf { it.isNotBlank() },
                                        year=tagYear.takeIf { it.isNotBlank() },
                                        genre=tagGenre.takeIf { it.isNotBlank() },
                                        track=tagTrack.takeIf { it.isNotBlank() },
                                        comment=tagComment.takeIf { it.isNotBlank() }
                                    ))
                                    tagStatus="${results.count { it.success }}/${results.size} files updated; " +
                                        results.filterNot { it.success }.take(3).joinToString { it.message }
                                    tagBusy=false
                                    refresh()
                                }
                            }) { Text(if(tagBusy)"Writing..." else "Apply tags to selected files") }
                            tagStatus?.let { Text(it,color=muted) }
                            HorizontalDivider()
                            }
                            if(studioPage=="Converter") {
                            Text("Audio converter",color=accent,fontWeight=FontWeight.Bold)
                            Text("Quick export presets",color=ink,fontWeight=FontWeight.SemiBold)
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                AudioExportPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected=converterFormat==preset.format && converterBitrate==preset.bitrateKbps,
                                        onClick={converterFormat=preset.format;converterBitrate=preset.bitrateKbps},
                                        label={Text(preset.label)}
                                    )
                                }
                            }

                            OutlinedButton(onClick={converterPicker.launch(arrayOf("audio/*","application/octet-stream"))}) { Text("Select source audio") }
                            Text(converterSource?.lastPathSegment ?: "No source",color=muted,maxLines=1)
                            converterInfo?.let { info ->
                                val durationText=info.durationMs?.let { "%.1f s".format(java.util.Locale.US,it/1000.0) } ?: "Unknown duration"
                                val sizeText=info.bytes?.let { "%.2f MB".format(java.util.Locale.US,it/1048576.0) } ?: "Unknown size"
                                val bitrateText=info.bitrate?.let { "${it/1000} kbps" } ?: "Unknown bitrate"
                                Text("${info.name} · ${durationText} · ${sizeText} · ${bitrateText}",color=muted,fontSize=12.sp)
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                AudioEngine.Format.entries.forEach { fmt -> FilterChip(selected=converterFormat==fmt,onClick={converterFormat=fmt},label={Text(fmt.ext.uppercase())}) }
                            }
                            if(converterFormat in listOf(AudioEngine.Format.MP3,AudioEngine.Format.OGG,AudioEngine.Format.M4A)) Text("Bitrate: ${converterBitrate} kbps",color=muted)
                            else Text("Lossless output: bitrate is codec-controlled",color=muted)
                            Text("Lossless WAV and FLAC ignore the bitrate setting.",color=muted,fontSize=12.sp)
                            HorizontalDivider()
                            Text("Batch conversion",color=ink,fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                            Text("Select up to 200 audio files, then choose an output folder. Originals stay unchanged.",color=muted,fontSize=12.sp)
                            OutlinedButton(enabled=!batchBusy,onClick={batchPicker.launch(arrayOf("audio/*","application/ogg","application/octet-stream"))}) {
                                Icon(Icons.Default.QueueMusic,null)
                                Spacer(Modifier.width(8.dp))
                                Text("Select multiple audio files")
                            }
                            Text("${batchSources.size} files selected",color=muted)
                            if(preflightBusy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                            preflightStatus?.let { Text(it,color=muted,fontSize=12.sp) }
                            Button(enabled=batchSources.isNotEmpty() && preflightPassed && !preflightBusy && !batchBusy && !converterBusy,onClick={batchFolderPicker.launch(null)}) {
                                Text(if(batchBusy)"Converting batch..." else "Choose output folder and convert")
                            }
                            if(batchBusy) {
                                LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                                Text(batchProgress,color=muted)
                                OutlinedButton(onClick={workManager.cancelUniqueWork(BatchConversionWorker.UNIQUE_NAME)}) { Text("Cancel batch") }
                            }
                            batchStatus?.let { Text(it,color=muted) }
                            HorizontalDivider()
                            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                                Text("Recent batches",color=ink,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                                TextButton(onClick={conversionHistory.clear();historyVersion++}) { Text("Clear history") }
                            }
                            val recentBatches=remember(historyVersion) { conversionHistory.recent() }
                            if(recentBatches.isEmpty())Text("No completed batches yet",color=muted,fontSize=12.sp)
                            recentBatches.take(5).forEach { entry ->
                                val date=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(entry.timestamp))
                                Text("${date} · ${entry.format.uppercase()} · ${entry.completed} saved / ${entry.failed} failed",color=muted,fontSize=12.sp)
                            }
                            Text("Single-file conversion",color=ink,fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                listOf(96,128,192,256,320).forEach { rate -> FilterChip(enabled=converterFormat in listOf(AudioEngine.Format.MP3,AudioEngine.Format.OGG,AudioEngine.Format.M4A),selected=converterBitrate==rate,onClick={converterBitrate=rate},label={Text("${rate}k")}) }
                            }
                            Button(enabled=converterSource!=null && !converterBusy,onClick={
                                converterSaver.launch("converted.${converterFormat.ext}")
                            }) { Text(if(converterBusy)"Converting..." else "Convert and save") }
                            converterStatus?.let { Text(it,color=muted) }
                            HorizontalDivider()
                            }
                            if(studioPage=="Cutter") {
                            Text("Audio cutter / silence / speed",color=accent,fontWeight=FontWeight.Bold)
                            OutlinedButton(onClick={wavPicker.launch(arrayOf("audio/*","application/octet-stream"))}) { Text("Select source to edit") }
                            Text(wavSource?.lastPathSegment ?: "No source",color=muted,maxLines=1)
                            cutterInfo?.let { info ->
                                val durationText=info.durationMs?.let { "%.1f s".format(java.util.Locale.US,it/1000.0) } ?: "Unknown duration"
                                val sizeText=info.bytes?.let { "%.2f MB".format(java.util.Locale.US,it/1048576.0) } ?: "Unknown size"
                                val bitrateText=info.bitrate?.let { "${it/1000} kbps" } ?: "Unknown bitrate"
                                Text("${info.name} · ${durationText} · ${sizeText} · ${bitrateText}",color=muted,fontSize=12.sp)
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                AudioEngine.Format.entries.forEach { fmt -> FilterChip(selected=cutterFormat==fmt,onClick={cutterFormat=fmt},label={Text(fmt.ext.uppercase())}) }
                            }
                            if(cutterFormat in listOf(AudioEngine.Format.MP3,AudioEngine.Format.OGG,AudioEngine.Format.M4A)) {
                                Text("Export bitrate: ${cutterBitrate} kbps",color=muted)
                                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    listOf(96,128,192,256,320).forEach { rate ->
                                        FilterChip(selected=cutterBitrate==rate,onClick={cutterBitrate=rate},label={Text("${rate}k")})
                                    }
                                }
                            }
                            if(previewDuration>0L) {
                                Text("Audio timeline · ${"%.1f".format(java.util.Locale.US,previewDuration/1000.0)} seconds",color=ink)
                                waveformPeaks?.let { waveform ->
                                    Text("Decoded audio waveform · peak envelope",color=muted,fontSize=12.sp)
                                    Canvas(Modifier.fillMaxWidth().height(108.dp).background(surface, RoundedCornerShape(12.dp))
                                        .pointerInput(waveform.durationSeconds,wavStart,wavEnd) {
                                            detectTapGestures { tap ->
                                                val duration=waveform.durationSeconds
                                                val selected=(tap.x/size.width.toFloat()).coerceIn(0f,1f)*duration
                                                val start=wavStart.toDoubleOrNull() ?: 0.0
                                                val end=wavEnd.toDoubleOrNull() ?: duration
                                                if(kotlin.math.abs(selected-start)<=kotlin.math.abs(selected-end)) {
                                                    wavStart="%.2f".format(java.util.Locale.US,selected.coerceAtMost((end-0.01).coerceAtLeast(0.0)))
                                                } else {
                                                    wavEnd="%.2f".format(java.util.Locale.US,selected.coerceAtLeast(start+0.01).coerceAtMost(duration))
                                                }
                                            }
                                        }.padding(8.dp)) {
                                        val peaks=waveform.amplitudes
                                        val center=size.height/2f
                                        peaks.forEachIndexed { index, amplitude ->
                                            val x=(index+0.5f)*size.width/peaks.size
                                            val extent=(amplitude.coerceIn(0f,1f)*center).coerceAtLeast(1f)
                                            drawLine(accent,Offset(x,center-extent),Offset(x,center+extent),strokeWidth=(size.width/peaks.size).coerceAtLeast(1f))
                                        }
                                        val duration=waveform.durationSeconds.coerceAtLeast(0.001)
                                        val a=((wavStart.toDoubleOrNull() ?: 0.0)/duration).coerceIn(0.0,1.0).toFloat()*size.width
                                        val b=((wavEnd.toDoubleOrNull() ?: duration)/duration).coerceIn(0.0,1.0).toFloat()*size.width
                                        drawLine(Color.White,Offset(a,0f),Offset(a,size.height),strokeWidth=3.dp.toPx())
                                        drawLine(Color(0xFFFFB86C),Offset(b,0f),Offset(b,size.height),strokeWidth=3.dp.toPx())
                                    }
                                }
                                Text("Trim start",color=muted,fontSize=12.sp)
                                Slider(value=(wavStart.toFloatOrNull() ?: 0f).coerceIn(0f,previewDuration/1000f),
                                    onValueChange={wavStart="%.2f".format(java.util.Locale.US,it)},
                                    valueRange=0f..(previewDuration/1000f).coerceAtLeast(0.01f))
                                Text("Trim end",color=muted,fontSize=12.sp)
                                Slider(value=(wavEnd.toFloatOrNull() ?: previewDuration/1000f).coerceIn(0f,previewDuration/1000f),
                                    onValueChange={wavEnd="%.2f".format(java.util.Locale.US,it)},
                                    valueRange=0f..(previewDuration/1000f).coerceAtLeast(0.01f))
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(enabled=wavSource!=null && previewJob?.isActive!=true,onClick={
                                        val src=wavSource ?: return@Button
                                        previewError=null
                                        previewJob=scope.launch {
                                            try {
                                                clipPreview.play(src,((wavStart.toDoubleOrNull() ?: 0.0)*1000).toLong(),
                                                    wavEnd.toDoubleOrNull()?.let { (it*1000).toLong() }) { previewPosition=it }
                                            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
                                            catch(e:Exception) { previewError=e.message ?: "Preview failed" }
                                        }
                                    }) { Icon(Icons.Default.PlayArrow,null);Text("Preview selection") }
                                    OutlinedButton(onClick={previewJob?.cancel();clipPreview.stop()}) {
                                        Icon(Icons.Default.Stop,null);Text("Stop")
                                    }
                                }
                                Text("Preview: ${"%.1f".format(java.util.Locale.US,previewPosition/1000.0)} s",color=muted)
                                previewError?.let { Text(it,color=Color(0xFFFF9C9C)) }
                            }
                            OutlinedTextField(value=wavStart,onValueChange={wavStart=it},label={Text("Start seconds")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=wavEnd,onValueChange={wavEnd=it},label={Text("End seconds (blank = end)")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=wavRemoveRanges,onValueChange={wavRemoveRanges=it},label={Text("Remove ranges: 10-15, 25-30")},modifier=Modifier.fillMaxWidth())
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(value=wavFadeIn,onValueChange={wavFadeIn=it},label={Text("Fade in (s)")},modifier=Modifier.weight(1f),singleLine=true)
                                OutlinedTextField(value=wavFadeOut,onValueChange={wavFadeOut=it},label={Text("Fade out (s)")},modifier=Modifier.weight(1f),singleLine=true)
                            }
                            Text("Fade-out requires an end time and cannot be combined with removing ranges or silence.",color=muted,fontSize=12.sp)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=wavNormalize,onCheckedChange={wavNormalize=it})
                                Column { Text("Normalize loudness (−16 LUFS target)",color=ink)
                                    Text("Single-pass EBU R128; results may vary with content.",color=muted,fontSize=12.sp) }
                            }
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
                            val cutterInput=AudioEditInput.parse(wavStart,wavEnd,wavRemoveRanges,wavRemoveSilence,wavSilenceSeconds,wavSpeed.toDouble(),cutterPitch)
                            cutterInput.exceptionOrNull()?.message?.let { Text(it,color=Color(0xFFFF9C9C),fontSize=12.sp) }
                            Text("Time values are in seconds. All selected cuts are removed before exporting.",color=muted,fontSize=12.sp)
                            Button(enabled=wavSource!=null && !wavBusy && cutterInput.isSuccess,onClick={cutterSaver.launch("edited.${cutterFormat.ext}")}) {
                                Text(if(wavBusy)"Processing..." else "Process and save")
                            }
                            wavStatus?.let { Text(it,color=muted) }
                            }
                        }
                    }
                    else -> {
                        Text("Settings",fontSize=30.sp,color=ink,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        Text("Offline-first • Local playback",color=muted)
                        Text("Favorites: ${songs.count { favorites.getBoolean(it.id.toString(),false) } + (favoritesVersion * 0)}",color=muted)
                        Text("No account or internet required.",color=muted)
                        Spacer(Modifier.height(12.dp))
                        Text("MusicSquare Player 0.3.0",color=ink)
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
                        val danceAnalysis=current?.let { analysisStore.read(it.id,it.modified) }
                        DancingCompanion(
                            playing=playing,
                            onDismiss={dancerVisible=false},
                            bpm=danceAnalysis?.bpm,
                            energy=danceAnalysis?.energy ?: 0.5f
                        )
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
                            TextButton(onClick={store.assign(song.id,index);trainingStore.label(song.id,index);assignmentsVersion++;assignSong=null},modifier=Modifier.fillMaxWidth()) {
                                Text("${index+1}. $name")
                            }
                        }
                    }
                },confirmButton={TextButton(onClick={assignSong=null}){Text("Close")}})
        }
    }
}
