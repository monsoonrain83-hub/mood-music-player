# Audio Engine streaming safety overhaul

This change introduces a shared `AudioFileSafety` streaming primitive, with hard 2 GiB input/output ceilings, bounded read/write and SHA-256 read-back verification. It is integrated into `AudioEngine.process()` for SAF input copy, generated output limits, SAF destination write, and read-back digest. The copy/digest APIs accept cancellation callbacks. Seven JVM unit tests cover byte fidelity, zero-length input, exact boundary, oversize input and digest, SHA-256, and cancellation.

The 2 GiB ceiling is an explicit resource protection policy for temporary copies; larger sources will now fail rather than exhaust disk. The implementation does not yet provide a persistent foreground WorkManager job or cancellable FFmpeg native execution. Build and device validation remain required before release.
