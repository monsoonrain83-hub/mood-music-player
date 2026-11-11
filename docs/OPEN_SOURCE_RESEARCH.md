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
