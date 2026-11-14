# Cancellable FFmpeg native execution

AudioEngine now runs FFmpeg asynchronously through CancellableFfmpeg. Coroutine cancellation cancels the active FFmpegKit session, including cancellation during the race between registering the callback and receiving the session handle. Conversion, cutter, and AudioEngine metadata writing share this path. The execution coroutine does not occupy a dispatcher thread waiting for FFmpeg completion.

Validate compile, cancellation while transcoding large inputs, and output cleanup with SAF. This does not make SAF provider writes atomic and does not yet implement a foreground WorkManager service.
