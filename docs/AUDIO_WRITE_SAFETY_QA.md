# Audio editing safety acceptance tests

## Critical data integrity
1. Attempt convert(sourceUri, sourceUri): must return failure before reading or truncating the original.
2. Tag edits on the same URI must preserve backup and verify read-back; force provider failure and inspect restoration.
3. NaN, Infinity, negative times and invalid speed must be rejected before any output is opened.
4. Cancel batch tag editing after first file: no subsequent file should be opened.
5. Cancel audio processing before write: destination must not be opened after cancellation.
6. Verify output on separate URI for MP3, WAV, FLAC, OGG, M4A using actual Android document providers.
7. Check source and destination with separate URI strings referring to the same underlying document: URI equality alone cannot detect provider aliases. Require user-facing warning and test on real providers.

## Limits
The source-code protections do not establish atomic writes on SAF providers. Do not edit irreplaceable files without independent backups. FFmpegKit synchronous processing may continue until its native session finishes even if the calling coroutine is cancelled; cancellation is checked before file replacement.

## CI
Run static checks, unit tests, debug APK build and emulator startup tests.
