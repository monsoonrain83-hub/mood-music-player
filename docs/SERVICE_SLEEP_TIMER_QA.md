# Playback-service sleep timer

The playback service now owns the countdown deadline and pauses ExoPlayer when it expires. The UI persists the monotonic deadline to shared preferences and displays remaining time. The service listens for changes and reschedules without depending on an open Compose screen. Expired deadlines are cleared when the service starts.

Limits: elapsedRealtime deadlines do not survive device reboot correctly; this timer is not a wakeup alarm and will not run if the service/process is stopped. Validate behavior when leaving the Activity, changing timer presets and pausing playback. CI compile and emulator checks required.
