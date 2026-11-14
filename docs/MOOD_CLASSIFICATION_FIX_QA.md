# Mood classification zero-result fix

The decoder returns energy even when beat/BPM confidence is insufficient. AcousticMoodEstimate now groups such tracks on the middle valence column using energy, explicitly labelled an acoustic proxy rather than a trained happiness/emotion classifier. Previously those tracks were silently left unclassified. UI reports number of newly assigned groups and unreadable files after completion. Existing Player screen already includes an Edit tags action for the current track, which opens Studio Tag Editor and loads embedded metadata; further direct mini-player access is a separate UI improvement.

QA: run Analyze and assign supported moods on a representative library, verify counts increment as readable files finish, verify songs with unreliable BPM appear in middle column, and ensure unreadable files remain unclassified. Verify CI and physical-device behavior.
