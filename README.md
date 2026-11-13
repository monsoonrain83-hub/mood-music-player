# MusicSquare Player

Offline-first Android music player written in Kotlin and Jetpack Compose, using Media3 ExoPlayer and MediaSessionService. Android minSdk 24, targetSdk 34.

## Implemented in source

- MediaStore local library: songs, artist, album, folder, genre, year, favorites, most-played, user playlists and an alphabet rail.
- Persian/Arabic character-normalized search and alphabetical sorting.
- Local playback and background MediaSession controls; seek, shuffle, repeat, queue and sleep timer.
- Direct **audio file** sharing via `ACTION_SEND`, `EXTRA_STREAM` and temporary URI read grants (not a link).
- SAF-based embedded tag writing to selected source documents, including MP3 ID3 verification and rollback attempts. Other supported formats use FFmpegKit; provider permissions and codec/container support vary. SAF writes are not atomic.
- Offline BPM and RMS-derived energy analysis, with cancellation and bounded decoding time; supported decoder outputs include 8-bit, 16-bit and float PCM.
- Four explainable acoustic library views: Energetic, Relaxed, Fast tempo and Unanalyzed. These are heuristics, **not** validated emotion classifiers.
- On-device personalized mood nearest-neighbour predictions from explicitly labeled tracks, with abstention for insufficient evidence.
- A responsive 5×5 mood grid, editable user labels, a lightweight 2.5D animated dancer linked to BPM and energy, and local artwork.
- EQ presets, local lyrics with import/export and optional LRCLIB online search, appearance skins and three widget sizes.
- Online lyrics lookup is **off by default** and requires opt-in; when enabled, title, artist, album and duration are transmitted to LRCLIB over HTTPS.

## Important limitations and verification

This repository is **not a certified production release**. A 2.5D Compose dancer is not a skeletal 3D model. Audio-derived energy is a loudness proxy; automatic emotional valence recognition has not been scientifically validated. Streaming catalogs and Spotify/SoundCloud integrations are not implemented.

Automated CI runs static security checks, Python regression tests, JVM unit tests, debug APK compilation, and an Android emulator startup test. A passing build **does not** establish zero crashes or acceptable battery life on physical phones. Multi-hour playback, Bluetooth/headset/audio-focus handling, power profiling, OEM EQ compatibility, file-tag recovery, accessibility audits, and independent security testing require physical-device evidence.

## Build and tests

Use JDK 17 and Android SDK 35, then run:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
python3 tools/security_audit.py
python3 -m unittest discover -s tools/tests -v
```

GitHub Actions publishes `MusicSquare-debug-apk` for successful debug builds. Do not treat debug artifacts as signed production releases.

## Privacy

No cloud upload is required for local library discovery, playback, BPM/energy analysis or mood predictions. The app requests Android audio access at runtime, disables cleartext traffic and disables Android app-data backup. External lyrics requests are opt-in. File sharing occurs only when initiated by the user through Android's share sheet.

## Design

Editable Figma mockups: https://www.figma.com/design/Uh6pWN1HDrNlOb9TpBQvtV
