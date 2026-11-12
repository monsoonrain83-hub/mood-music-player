#!/usr/bin/env python3
"""Fail-closed static security/permission checks. NOT a penetration test."""
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
permissions = {p.attrib.get(android + "name") for p in manifest.findall("uses-permission")}
required = {"android.permission.READ_MEDIA_AUDIO", "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"}
assert required <= permissions, f"Missing playback permissions: {required - permissions}"
for blocked in ("android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.QUERY_ALL_PACKAGES", "android.permission.ACCESS_FINE_LOCATION"):
    assert blocked not in permissions, f"Disallowed sensitive permission: {blocked}"
app = manifest.find("application")
assert app is not None
assert app.attrib.get(android + "usesCleartextTraffic") == "false"
assert app.attrib.get(android + "allowBackup") == "false"
for child in app:
    if child.tag in {"activity", "service", "receiver", "provider"}:
        assert child.attrib.get(android + "exported") in {"true", "false"}, child.attrib
        if child.tag == "provider":
            assert child.attrib.get(android + "exported") == "false", "Unexpected exported provider"
print("Manifest permission, backup, export and cleartext static checks passed")
