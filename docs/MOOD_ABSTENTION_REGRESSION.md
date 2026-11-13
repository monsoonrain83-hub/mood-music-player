# Mood inference integrity

Automatic mood assignment must abstain when supervised PersonalMoodModel has insufficient support and AudioAnalysis.valence is null. Never default valence to 0.5, since doing so incorrectly assigns all unclassified songs to a neutral-valence column. Manual labels remain authoritative. Offline energy and tempo remain usable in smart library filters. Batch analysis must release its progress indicator in finally and honor coroutine cancellation.
