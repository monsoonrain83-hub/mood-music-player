# SAF alias and cancel acceptance

1. Select the same document through different SAF URI paths sharing authority and document ID; conversion must refuse before writing.
2. Convert different documents in MP3, FLAC, OGG, WAV, M4A; compare original SHA-256 before and after.
3. Start bulk BPM analysis from library; press Cancel; progress should clear and unprocessed tracks should remain untouched.
4. Start mood-grid bulk analysis; press Cancel; existing manual labels must not be overwritten.
5. Repeatedly start and cancel jobs; verify no ANR or decoder leak on device.
6. Confirm native FFmpeg processing cancellation is only checked before output writing; no claim of native preemption.
7. URI alias detection covers matching document IDs within an authority, not all provider alias mechanisms; use separate destinations and backups.
