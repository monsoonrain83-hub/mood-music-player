# Privacy, queue, analysis cache and lyrics regression

## Network and privacy
- Disable online lyrics while a request is in progress; confirm request is canceled and no result is displayed after opt-out
- Opt-in is persisted and initially disabled
- Redirect responses from lyrics service are not followed
- Reject remote lyric payloads over 512 KiB and cached lyrics over 200,000 characters
- Verify song metadata is sent only for opted-in lyrics requests

## Persistence and malformed input
- Corrupted cached energy (NaN/Infinity), confidence, BPM and valence are rejected or normalized
- Restored queues reject negative song IDs, oversize arrays and malformed JSON
- Queue indexes, repeat modes and playback positions are clamped
- Clearing an empty queue removes old persisted state
- LRC timestamps with invalid seconds are ignored; timestamp rows capped at 10,000

## Device acceptance
- Physical Android 7 through current: permission revocation, offline use, background playback, Bluetooth disconnect, multi-hour battery and crash/ANR logging
- Tag writeback on copies of MP3, FLAC, OGG, M4A, WAV with forced storage failure
- Security checks and CI do not substitute for real device measurements
