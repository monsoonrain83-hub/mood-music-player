# Audio Studio UI and editor acceptance

Implemented:
- Studio landing screen with four large Material 3 cards and clear descriptions
- Equalizer, batch tag editor, converter and cutter navigation
- Additional track number and comment metadata fields in batch editor and tagged-copy export
- Live input validation for cutter start/end, multiple removal intervals, silence threshold and speed
- Disabled export when cutter inputs are invalid
- New unit tests for input parsing

Manual QA remaining:
- Check small-screen scrolling, font scaling at 200%, TalkBack focus and contrast
- Confirm batch metadata writes on MP3/FLAC/OGG/M4A/WAV test files
- Test malformed time ranges, storage cancellation, and file-provider write errors
- Verify generated audio playback and waveform against reference editors
- Test tablet landscape layout and physical device battery/thermal behavior

Not yet implemented: interactive waveform trimming, ID3 artwork editing, replay gain, batch transcoding queue, native audio effects beyond current equalizer, per-format encoding controls, real-time spectrum analyzer.
