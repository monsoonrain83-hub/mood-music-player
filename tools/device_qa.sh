#!/usr/bin/env bash
# Real device only. Running CI on an emulator is not proof of battery or thermal reliability.
set -euo pipefail
command -v adb >/dev/null || { echo "adb is required"; exit 2; }
if [[ "$(adb devices | awk 'NR>1 && $2=="device"{n++} END{print n+0}')" != "1" ]]; then
 echo "Connect exactly one unlocked Android device with USB debugging."; exit 2
fi
PACKAGE=com.musicsquare.player
MINUTES="${DURATION_MINUTES:-60}"
[[ "$MINUTES" =~ ^[0-9]+$ && "$MINUTES" -ge 1 && "$MINUTES" -le 480 ]] || { echo "DURATION_MINUTES must be 1..480"; exit 2; }
OUT="${QA_REPORT_DIR:-device_qa_$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$OUT"
adb shell getprop ro.product.model >"$OUT/model.txt"
adb shell getprop ro.build.version.release >"$OUT/android_version.txt"
adb shell dumpsys battery >"$OUT/battery_before.txt"
adb shell dumpsys batterystats --reset >"$OUT/reset.txt" 2>&1 || true
adb logcat -c
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >"$OUT/launch.txt"
echo "Begin local playlist playback on device. Soak duration: $MINUTES min."
for (( i=1; i<=MINUTES; i++ )); do
 sleep 60
 adb shell dumpsys meminfo "$PACKAGE" > "$OUT/memory_minute_$i.txt" 2>&1
 adb shell dumpsys battery > "$OUT/battery_minute_$i.txt" 2>&1
 adb shell pidof "$PACKAGE" >>"$OUT/process_observations.txt" 2>/dev/null || echo "PROCESS_MISSING minute=$i" >>"$OUT/process_observations.txt"
done
adb logcat -d -v threadtime >"$OUT/logcat.txt"
adb shell dumpsys batterystats "$PACKAGE" >"$OUT/batterystats.txt" 2>&1 || true
adb shell dumpsys battery >"$OUT/battery_after.txt"
adb shell dumpsys activity processes >"$OUT/processes.txt"
if grep -q 'PROCESS_MISSING' "$OUT/process_observations.txt"; then
 echo "FAIL: process disappeared. Reports: $OUT"; exit 1
fi
if grep -E 'FATAL EXCEPTION|ANR in com\.musicsquare\.player' "$OUT/logcat.txt" | grep -q "$PACKAGE"; then
 echo "FAIL: app crash/ANR in logcat. Reports: $OUT"; exit 1
fi
echo "Soak completed without detected disappearance. Compare battery, thermal and memory reports: $OUT"
