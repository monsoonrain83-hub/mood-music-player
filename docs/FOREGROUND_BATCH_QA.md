# Foreground batch audio conversion

The WorkManager batch worker now calls setForeground before conversion and updates its foreground notification for each file. Notification channel is low-importance, has a progress bar, opens the app, and offers a native WorkManager cancel action. AndroidManifest merges WorkManager's SystemForegroundService with the dataSync type and declares FOREGROUND_SERVICE_DATA_SYNC.

Caveats: Android 13+ notification permission is user-controlled. Android 14+ foreground-service start restrictions apply. Android 15+ dataSync time limits may apply. No per-file checkpointing exists yet; interrupted work may be retried from the beginning. Validate on physical Android devices and CI before release.
