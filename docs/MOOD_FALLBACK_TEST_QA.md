# Acoustic mood fallback verification

Tests now verify that tracks with valid energy but missing or unreliable BPM receive neutral-column acoustic grouping, and invalid energy is rejected. This grouping is a provisional acoustic proxy, not emotion recognition. Confirm all unit tests, APK assembly and emulator smoke tests before releasing.
