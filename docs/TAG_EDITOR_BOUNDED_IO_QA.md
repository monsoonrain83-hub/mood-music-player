# MP3 Tag Editor bounded IO verification

MP3 Tag Editor now uses AudioFileSafety.copyBounded for SAF source reads, enforces a 2 GiB cap on generated MP3s, and uses AudioFileSafety.sha256 for bounded read-back integrity verification. Coroutine cancellation is checked before replacing the source. If a write is interrupted, the original is restored before cancellation is propagated. A restoration failure is reported explicitly.

Verify compilation and Android tests via CI. Physical-device SAF provider behavior, interruptions during restoration, and multi-gigabyte performance still need testing. This does not claim atomic in-place writes.
