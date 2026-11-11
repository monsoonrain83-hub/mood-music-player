# MusicSquare Player

Offline-first Android music player built with Kotlin, Jetpack Compose Material 3, Media3 ExoPlayer, and MediaSessionService.

## Current capabilities
- Browse local music using Android MediaStore (Android 7–14).
- Play local audio in a Media3 background service with system media session controls.
- Navigate between a 5×5 mood-energy grid, local library, now-playing screen, and settings.
- Assign songs to mood cells offline and save assignments on device.
- Play songs from a selected mood cell.
- Seek, skip, pause, resume, and share song metadata.

## New library tools (November 2026)
- Group tracks by folder, artist, album, genre, year, favorites, and play count.
- Create local playlists and add songs to them.
- Share original audio bytes using Android ACTION_SEND with EXTRA_STREAM and URI read grants, not a URL.
- Android Equalizer presets (Flat, Bass Boost, Treble Boost, Vocal, Rock, Jazz, Classical, Dance) and bass strength slider. Audio effects are hardware-dependent.
- Batch MP3 embedded ID3 tag editor via Android Storage Access Framework: select one or more writable MP3 documents, enter only fields to change, then write the modified files back to their original URIs. This is NOT yet a general-purpose FLAC/OGG/M4A editor.

## Important limitations
This is an **early functional implementation, not a production release**. Mood assignment is manual. Automatic valence inference, Room, Hilt, artwork loading, theme selection, and Spotify integration are **not implemented**. The grid mapping model is unit-tested but is not yet wired to automatic analysis.

## Build
Open the repository in Android Studio (JDK 17, Android SDK 35). Sync Gradle and run the `app` configuration. GitHub Actions builds a debug APK on pushes to `main` and uploads it as an artifact if successful. CI has not yet been independently verified.

## Privacy
Music discovery and playback do not require an account or network permission. The app requests local audio access at runtime.

## Design
Editable Figma mockups: https://www.figma.com/design/Uh6pWN1HDrNlOb9TpBQvtV
