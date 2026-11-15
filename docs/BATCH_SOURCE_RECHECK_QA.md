# Batch conversion per-source recheck

The batch converter now checks each source immediately before creating the destination. It checks known file sizes, verifies the document can still be opened and contains at least one byte, and honors coroutine cancellation. Unknown-size providers remain supported; AudioFileSafety enforces the actual streaming size limit. The selection policy rejects duplicate and oversized batches. Eight JVM unit tests cover selection and size boundaries.

This closes a gap between UI-time preflight and actual export, but does not make provider permissions immutable or guarantee that an external document cannot change after the check. Android CI and real-provider testing are required.
