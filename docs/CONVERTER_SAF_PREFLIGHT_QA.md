# Converter safety stabilization

Batch export is now blocked in the UI until every selected source passes SAF read-access preflight. The batch writer checks that a newly created output URI is not the input URI and verifies that the provider honored the reserved filename before writing converted bytes. On failure the batch writer attempts to delete the newly created output. Existing output names are enumerated and reserved before conversion.

Verify with Downloads, removable storage and cloud document providers. Providers that rename documents automatically will now fail safely rather than silently producing an ambiguous output. This is a safety-first tradeoff. Remaining: persistent background processing and atomic reservation against concurrent external writers.
