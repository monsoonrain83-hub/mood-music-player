# Open-source reference review — 2026-11-11

| Project | Relevant strengths | License | Decision |
|---|---|---|---|
| [Resonate](https://github.com/kimhabkhun/resonate) | Local MediaStore library, Media3 session, playlists, artwork-driven UI, backup, offline-first | MIT (as documented by project) | **Preferred architectural and UX reference** |
| [Rhythm](https://github.com/cromaguy/Rhythm) | Material 3 Expressive, high-resolution playback, equalizer, lyrics, queue | GPL-3.0 | **Design inspiration only**; do not copy GPL code into a differently licensed app |
| [Music — skaftab-in](https://github.com/skaftab-in/android-music-player) | Compose, Media3, Room, playlists, playback controls | Verify before reuse | Reference for local library and playback design |
| [Moody](https://github.com/circuitflow/moody) | Continuous valence × arousal plane, embeddings and mood-path playlists | Verify before reuse | Research inspiration; project describes early scaffold |
| [tsirysndr/music-player](https://github.com/tsirysndr/music-player) | Tempo, energy, valence/arousal metadata and auto-DJ | Verify before reuse | Algorithm reference; server/Rust code not Android-portable |

## Current implementation
All code in this repository is independently written, not copied from these projects.
The 5×5 Music Square is a discretized valence × energy map.
Offline energy is a normalized RMS proxy; BPM uses envelope autocorrelation.
**No automatic valence classifier is included**: the app never fabricates a happy/sad label.
Manual mood labels are stored on-device.

## Engineering notes
- BPM from short excerpts is approximate and may show half/double-time errors.
- Loudness-based energy is not a perceptual energy classifier.
- Android MediaCodec output may vary by codec; unsupported formats return no result.
- For a real mood model, use a licensed, validated mobile model trained on a mood-labelled dataset such as DEAM, with reproducible evaluation.


## November 2026 comparative feature audit

Compared open-source Android players (features studied, **no GPL code copied**):

- [Metro](https://github.com/zyrouge/Metro) (GPL-3.0): drag-reorder queue, smart playlists, sleep timer, Android Auto, lyrics, tag editor, compact Now Playing. MusicSquare now has a simple sleep timer and shuffle/repeat, but queue drag-reorder, Android Auto and widgets remain open.
- [HexaPlayer](https://github.com/OxoGhost01/HexaPlayer): file-embedded multi-tag editor, remembered queue, lyric offset, advanced audio effects. MusicSquare now persists per-track LRC offsets, but multi-format in-place batch tagging and queue persistence remain open.
- [OmniTune](https://github.com/soupashh-ship-it/OmniTune) (GPL-3.0): multiple lyric providers, Room persistence, backup/restore and background work. MusicSquare uses LRCLIB only and a lightweight SharedPreferences cache.
- [Flick](https://github.com/moss-apps/Flick): waveform seek, FFT visualizer, widgets, metadata and CUE support. Not yet implemented here.
- [Retro Music Player](https://github.com/RetroMusicPlayer/RetroMusicPlayer) (GPL-3.0): polished Material You player layouts, queue management and smart playlists. Design reference only.

### Release blockers

1. Confirm latest CI compiles and passes unit tests; test actual Android device playback and SAF access.
2. Validate native FFmpegKit binaries, codec coverage, 1h+ files, large-file handling, all five output formats and cancellation.
3. Finish direct embedded metadata write and safe batch processing for MP3, WAV, FLAC, OGG and M4A.
4. Validate landscape, small screens, screen readers, drag/dismiss dancer; replace 2.5D illustration with genuine rigged 3D avatar only with suitable licensed assets.
5. Add robust queue restoration, crossfade, background-safe sleep timer, artwork, Android Auto, widgets, and comprehensive mood valence analysis.
6. Artist catalog contains names only. Do not bundle copyrighted lyrics without redistribution rights; use licensed sources and user-imported LRC/plain text.

### Licensing

Reference repositories use a mix of licenses. Studying interaction patterns is different from copying GPL-3.0 code. Any actual code reuse must satisfy the original license and attribution requirements.
