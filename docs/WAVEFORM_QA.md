# WAV waveform implementation and QA

Implemented: PCM 16-bit RIFF/WAVE peak-envelope extractor, max 512 buckets, cooperative cancellation during reads, unsupported encoding fallback, visual start/end markers, tap to adjust nearest marker, trim sliders and source clip preview. Handles mono to 8-channel interleaved 16-bit PCM, rejects malformed headers and oversized format chunks. Extractor does not load the entire audio into RAM.

Important: not all WAV files are PCM 16-bit, and compressed MP3/AAC/FLAC/OGG waveform decoding remains to be added. For unsupported formats, existing duration sliders and preview remain available. Test with RIFF metadata chunks, empty/short/truncated WAV, malformed chunk lengths, long files, multiple sample rates, orientation and screen readers. Source preview does not apply output effects.
