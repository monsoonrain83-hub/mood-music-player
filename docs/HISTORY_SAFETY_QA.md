# Conversion history hardening

ConversionHistory now limits JSON parsing to 20 entries, ignores individually corrupted entries instead of losing the whole history, rejects negative counts, and serializes read/add/clear access. Preference writes use synchronous commit so the calling code can immediately observe persistence.

No source URIs or filenames are stored in the conversion history. CI should validate compilation; device testing remains necessary for process-death persistence and malformed SharedPreferences data. This is a hardening change, not a new full-featured job queue.
