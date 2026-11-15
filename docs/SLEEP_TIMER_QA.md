# Sleep timer user-facing feature

Music player now exposes a Bedtime/Timer control alongside Shuffle and Repeat. Presets: 5, 10, 15, 30, 45, 60, 90, 120 minutes. Countdown uses monotonic elapsedRealtime and pauses the MediaController on expiry. Users can cancel an active timer from the same menu.

Current limitation: timer state lives in the Compose UI; if the app process dies or the UI is destroyed, the timer does not survive. A future iteration should move deadlines to the playback service and persist them. Verify on device and CI.
