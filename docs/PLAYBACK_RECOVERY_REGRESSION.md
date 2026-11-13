# Playback recovery regression

Test corrupted MP3/FLAC, revoked URI permission, removed SD card, unavailable next item, repeated failures, audio focus, incoming call, Bluetooth unplug and widget refresh. Ensure MediaSessionService stays alive and no infinite retries. Build/test in CI and run physical device endurance separately.
