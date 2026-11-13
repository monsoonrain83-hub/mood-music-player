package com.musicsquare.player.audio

/** Named presets to make encoder choices consistent across Studio tools. */
enum class AudioExportPreset(
    val label:String,
    val format:AudioEngine.Format,
    val bitrateKbps:Int,
    val description:String
) {
    VOICE("Voice / podcast",AudioEngine.Format.MP3,96,"Compact MP3 for speech"),
    BALANCED("Balanced music",AudioEngine.Format.MP3,192,"Good size and compatibility"),
    HIGH_QUALITY("High quality",AudioEngine.Format.MP3,320,"High bitrate MP3"),
    LOSSLESS("Lossless archive",AudioEngine.Format.FLAC,192,"Lossless compression"),
    PCM("Uncompressed PCM",AudioEngine.Format.WAV,192,"16-bit PCM WAV"),
    MOBILE("Mobile AAC",AudioEngine.Format.M4A,192,"AAC in an M4A container");

    companion object {
        fun forFormat(format:AudioEngine.Format):List<AudioExportPreset> =
            entries.filter { it.format==format }
    }
}
