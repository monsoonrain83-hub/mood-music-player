# Streaming coroutine compilation fix

The CI run for cancellable FFmpeg reported two Kotlin compilation errors in TagEditor: suspension functions can only be called within a coroutine body. The job context is now captured from the suspending method before passing callbacks to synchronous copy/digest functions. AudioEngine also captures the context for consistency.

This PR triggers the Android build and emulator smoke workflow to verify the fixes. Do not ship an APK until CI passes.
