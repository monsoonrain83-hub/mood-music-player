package com.musicsquare.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

/**
 * Android platform effects, attached to the current Media3 audio session.
 * Availability and supported bands vary by device and audio route.
 */
class EqualizerManager {
 private var equalizer:Equalizer?=null
 private var bass:BassBoost?=null
 val presets get() = listOf("Flat","Bass Boost","Treble Boost","Vocal","Rock","Jazz","Classical","Dance")
 fun attach(sessionId:Int) {
  release()
  if(sessionId<=0)return
  equalizer=runCatching { Equalizer(0,sessionId).apply { enabled=true } }.getOrNull()
  bass=runCatching { BassBoost(0,sessionId).apply { enabled=true } }.getOrNull()
 }
 fun apply(name:String,bassStrength:Int=0) {
  val eq=equalizer
  if(eq!=null) {
   val min=eq.bandLevelRange[0].toInt()
   val max=eq.bandLevelRange[1].toInt()
   val count=eq.numberOfBands.toInt()
   for(i in 0 until count) {
    val normalized=if(count<=1)0f else i.toFloat()/(count-1)
    val gain=when(name) {
     "Bass Boost" -> if(normalized<0.35f)0.7f else -0.05f
     "Treble Boost" -> if(normalized>0.55f)0.7f else -0.05f
     "Vocal" -> if(normalized in 0.3f..0.8f)0.55f else -0.2f
     "Rock" -> if(normalized<0.25f || normalized>0.75f)0.5f else -0.1f
     "Jazz" -> if(normalized<0.3f)0.3f else if(normalized>0.65f)0.35f else 0.05f
     "Classical" -> if(normalized>0.6f)0.4f else 0.1f
     "Dance" -> if(normalized<0.4f)0.6f else if(normalized>0.7f)0.3f else -0.1f
     else -> 0f
    }
    eq.setBandLevel(i.toShort(),(gain*max).toInt().coerceIn(min,max).toShort())
   }
  }
  bass?.setStrength((bassStrength.coerceIn(0,100)*10).toShort())
 }
 fun release() {
  runCatching { equalizer?.release() }
  runCatching { bass?.release() }
  equalizer=null
  bass=null
 }
}
