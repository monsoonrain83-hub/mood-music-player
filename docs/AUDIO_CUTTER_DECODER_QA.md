# Audio Cutter verification: decoded waveforms and async preview

New MediaExtractor/MediaCodec decoder processes audio into 160 bounded amplitude buckets, with cooperative cancellation and codec cleanup. Supports formats with Android platform audio decoders, including many MP3/AAC/FLAC/Vorbis sources. Unsupported formats gracefully retain duration sliders. The original PCM16 RIFF/WAV extractor remains the first path. Preview now prepares asynchronously and seeks before starting playback; completion callback no longer releases player mid-loop.

New source-aware cutter validation rejects out-of-bounds, overlapping and fully destructive cut ranges, with seven JUnit tests. These changes require Android CI and device testing. MediaCodec output assumes PCM16 or float output and rejects unsupported PCM encodings. Verify output timing and waveform alignment for variable sample-rate media, nonzero decoder timestamps and channel layouts.
