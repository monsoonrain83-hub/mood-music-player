# Background batch conversion WorkManager

WorkManager 2.9.1 now runs batch audio conversion in a CoroutineWorker rather than an Activity-owned coroutine. The UI schedules unique work, observes its WorkInfo Flow for progress and terminal states, and cancels by unique work name. Source and tree SAF persistable grants are requested and conversion history is written by the worker. Input is validated and the existing sequential BatchAudioConverter is reused. A new job is not enqueued while existing unique work is active (KEEP policy).

Limitations: no foreground long-running notification yet; OS can stop or reschedule lengthy work. No per-file checkpoint/resume after process death; a restarted worker could repeat completed conversions. Persistable SAF grants depend on provider support. These must be addressed before describing the queue as fully durable for large workloads. Run CI and real device tests.
