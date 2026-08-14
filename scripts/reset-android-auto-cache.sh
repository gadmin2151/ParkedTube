#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found. Install Android platform-tools first." >&2
    exit 1
fi

adb shell am force-stop com.google.android.projection.gearhead || true
adb shell am force-stop com.google.android.gms || true

echo "Stopped Android Auto and Google Play services."
echo "Now reopen Android Auto settings and check Customize launcher again."
