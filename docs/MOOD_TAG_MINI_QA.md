# Mood classification and mini-player Tag Editor QA

- Persistent mini-player has an Edit icon that opens Studio Tag Editor for the currently playing track, sharing the same loading handler as Now Playing.
- Mood scan completion reports newly grouped, previously grouped, readable and decode failures.
- Energy-only provisional grouping remains separate from validated emotion detection.

Test scenarios: play song, tap mini-player Edit, verify title/artist/album loaded; return to Player and edit from there; run scan on representative files with and without BPM; check counts and cancellation. CI must compile and run tests. Editing MediaStore URI may require user write consent or selecting original SAF file; do not assume writable access.
