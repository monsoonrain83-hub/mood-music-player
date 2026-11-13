# Streaming integrity checks and memory

This change removes redundant full-file temporary readback copies in audio conversion, FFmpeg metadata writes, and MP3 tag editing. Readback is hashed in 64 KiB chunks and the byte count must match the output length. Verify 100 MB, 1 GB, storage-full, corrupt-provider and permission-revocation scenarios. Source and generated output temporary files still consume cache space; native FFmpeg processing is not yet cooperatively cancellable. CI and emulator tests do not measure real-world battery or storage pressure.
