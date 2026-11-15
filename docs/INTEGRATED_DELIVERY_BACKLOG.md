# MusicSquare Player — integrated delivery backlog

Status: planning and automation scaffolding; not a claim that features or APK are complete.

## P0 — Restore green CI
- [ ] Repair MainActivity.kt broken composable structure around current-song Tag Editor and Scaffold (reported errors near lines 627, 678, 1193, 1204).
- [ ] Pass security audit, Python regression tests, Kotlin JVM tests, debug APK assembly, emulator startup.
- [ ] Review existing open PRs and prevent duplicate changes or unreviewed merges.

## P1 — Verify core playback
- [ ] Background playback, MediaSession, notification, queue, repeat/shuffle, headset and audio focus.
- [ ] Sleep timer service behavior, cancellation, notification, process death.
- [ ] MediaStore scan, permission handling, duplicates and large libraries.

## P1 — Mood and analysis
- [ ] Reproduce scan of 2,173 tracks with zero categories; distinguish unanalyzed, failed, insufficient evidence and categorized.
- [ ] Verify BPM/RMS extraction and incremental reruns, failure diagnostics and user feedback.
- [ ] Clearly label acoustic heuristics versus user-trained personalized mood inference; no unsupported emotion-accuracy claims.

## P1 — File operations
- [ ] Verify current-song Tag Editor from Now Playing and mini-player, SAF permissions and rollback/recovery.
- [ ] Verify conversion preflight, cancellation, batch WorkManager execution, progress and source-file protection.

## P2 — Complete product checks
- [ ] EQ compatibility, lyrics import/export/sync, offline/privacy opt-in, widgets, dancer and appearance.
- [ ] Persian/RTL and accessibility, performance/battery, device-specific crash testing.
- [ ] Release signing, versioning and publication readiness only after user approval.

## Ideas to evaluate (not commitments)
- [ ] Explainable classification confidence and per-track manual override.
- [ ] Crash-free telemetry with explicit opt-in and privacy controls.
- [ ] Reproducible benchmark library and performance regression thresholds.

## Automation policy
1. CI is the source of truth. Never mark a task complete merely because code was committed.
2. On failure, preserve logs, create/update a tracking issue and request an authorized coding agent to propose a fix.
3. A workflow alone cannot autonomously write correct code; connect an authorized agent and grant narrowly scoped permissions.
4. Limit retries, avoid committing generated changes to main without review, and stop on ambiguous product decisions, permissions, secrets or release signing.
5. Only publish a debug APK when all required CI jobs pass; production release needs separate verification.
