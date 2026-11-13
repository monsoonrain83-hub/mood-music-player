# Integrated smart-library and stability verification

## Functional
- [ ] Offline energy analysis: silent, low/high RMS, unknown BPM, canceled jobs and corrupt files
- [ ] Energetic: energy >= 0.70, Relaxed: energy <= 0.35 and BPM absent or <= 105, Fast tempo: BPM >= 130
- [ ] Unanalyzed shows only tracks without cached features
- [ ] Playlist names: blank, Unicode, case-insensitive duplicate, rename collisions, deleted playlist additions
- [ ] Music plays without requiring ML analysis or internet

## Reliability
- [ ] Headphones unplug; audio focus; incoming call; background and lock screen
- [ ] EQ unavailable or OEM audio effect throws; playback must continue
- [ ] Broken file and revoked permission; no crash or infinite retries
- [ ] Media widgets can fail without terminating service
- [ ] 4-hour physical device playback; record ANRs, crashes, wakeups and battery delta

## Security
- [ ] Confirm no network request during local analysis
- [ ] Confirm lyrics lookup is opt-in and file share requires user action
- [ ] Verify SAF original-file write and rollback on a copy before using irreplaceable files
- [ ] Static manifest checks are not penetration tests

CI builds and emulator smoke tests are necessary but insufficient to certify physical device reliability.
