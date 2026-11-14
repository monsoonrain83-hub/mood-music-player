# Tag Editor validation verification

The Tag Editor now validates the complete batch metadata payload before attempting any file writes. Empty no-op payloads, oversized metadata, unsupported control characters, malformed year and malformed track numbers fail early. Explicit empty strings remain valid for clearing a tag. The validation is tested with seven JUnit regression cases. Existing MP3 rollback and SHA-256 verification remain in place; other supported formats use the AudioEngine metadata writer.

Run Android CI and test with writable and read-only SAF providers. The existing Tag Editor has no album-art editing; do not describe this tranche as complete album-art support.
