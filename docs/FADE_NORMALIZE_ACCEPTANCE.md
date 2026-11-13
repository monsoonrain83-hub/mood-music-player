# Cutter fade and normalization acceptance

Implemented fade-in (0–30 seconds), fade-out (0–30 seconds with a bounded trim endpoint), and optional single-pass EBU R128 loudness normalization (-16 LUFS integrated target, -1.5 dBTP ceiling, LRA 11). Fade-out is rejected when removal ranges or silence removal would invalidate its time coordinate. Input validation and JUnit coverage added.

Manual audio QA: test exact fade timing on synthetic reference tones; check short clips, mono/stereo, AAC/MP3/WAV/FLAC/OGG, clipping and perceived loudness. Single-pass loudnorm is not a guarantee of exact integrated LUFS, and pitch-preserving speed adjustment can affect duration. Waveform editing and true-peak metering UI are not yet implemented.
