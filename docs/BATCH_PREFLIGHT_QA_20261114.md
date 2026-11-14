# Batch preflight stability

- A cancelled SAF read now propagates coroutine cancellation rather than incorrectly marking the file unreadable.
- Known zero-length documents fail before batch conversion.
- Duplicate source URIs are rejected by preflight.
- Choosing more than 200 distinct files now fails the entire selection rather than silently processing only the first 200.
- The existing UI conversion button remains gated on a successful preflight.

Android CI is required before release. External provider behavior and device-level cancellation must still be verified.
