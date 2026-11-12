#!/usr/bin/env python3
"""Summarize real Android device soak metrics. Never fabricate a measurement."""
import json
import pathlib
import re
import sys


def battery_fields(text: str) -> dict:
    result = {}
    for key in ("level", "temperature", "voltage", "status", "plugged"):
        match = re.search(r"^\s*" + key + r":\s*(-?\d+)\s*$", text, re.MULTILINE)
        if match:
            result[key] = int(match.group(1))
    if "temperature" in result:
        result["temperature_celsius"] = result["temperature"] / 10
    return result


def summarize(path: pathlib.Path) -> dict:
    before = battery_fields((path / "battery_before.txt").read_text())
    after = battery_fields((path / "battery_after.txt").read_text())
    output = {"before": before, "after": after, "notes": []}
    if "level" in before and "level" in after:
        output["battery_change_percentage_points"] = after["level"] - before["level"]
    else:
        output["notes"].append("No compatible battery level reading")
    if before.get("plugged", 0) or after.get("plugged", 0):
        output["notes"].append("Device charging/plugged: cannot interpret as discharge")
    pids = (path / "process_observations.txt").read_text()
    output["process_missing_observed"] = "PROCESS_MISSING" in pids
    output["samples"] = len(list(path.glob("battery_minute_*.txt")))
    if output["samples"] == 0:
        output["notes"].append("No periodic samples recorded")
    output["notes"].append("Battery-level delta is not app energy usage; compare batterystats, temperature, and matched control runs.")
    return output


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 tools/summarize_device_qa.py REPORT_DIR")
    directory = pathlib.Path(sys.argv[1])
    summary = summarize(directory)
    target = directory / "summary.json"
    target.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n")
    print(target)
