# Audio Cutter completion: bounds and destructive edit prevention

Cutter parser now validates start/end and removal ranges against detected source duration, rejects overlapping ranges, rejects removing the entire selected clip, and enforces nonnegative finite time inputs. UI passes detected duration to validation. Seven new JUnit regression tests cover valid/invalid intervals and adjacent ranges. Missing source duration preserves previous validation behavior.

This change does not implement waveform decoding for compressed audio, persistent background conversion or physical device testing. Existing Audio Studio CI from prior phase completed successfully. Validate the new branch's CI before distributing an APK.
