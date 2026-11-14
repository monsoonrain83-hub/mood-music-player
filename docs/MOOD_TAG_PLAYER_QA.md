# Mood analysis and now-playing tags QA

The Mood screen now groups reliable-tempo songs using a clearly labeled provisional acoustic estimate if no trained personal model is available. This is not a scientifically validated happiness predictor. The now-playing screen has an Edit current song tags button which opens the existing tag editor with the current MediaStore URI and reads its embedded metadata. Editing may require SAF write access on Android 11+; choose the original document if permission fails.

CI must compile and run tests; verify real device behavior before release.
