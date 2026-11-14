# Critical SAF source protection

BatchAudioConverter now compares document IDs as well as URI equality before writing. If a broken provider returns the original input as the created output, the cleanup handler refuses to delete it. Cleanup failures are included in batch error reports rather than silently ignored. Cancellation still propagates after a best-effort cleanup. This is a safety guard against provider aliasing; atomic provider transactions remain unsupported.

Re-run CI. Device test with DocumentsProvider implementations that alias URIs, refuse deletion, or rename documents.
