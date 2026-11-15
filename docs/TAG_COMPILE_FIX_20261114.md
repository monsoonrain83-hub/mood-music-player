# Tag Editor coroutine context compile fix

Capture the coroutine context inside the suspend function and use the captured context in synchronous bounded IO callbacks. This resolves the Kotlin compiler errors in TagEditor.kt at lines 97 and 135 observed in CI run 43577713706. Re-run the full Android workflow before considering a release.
