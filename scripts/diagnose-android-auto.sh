#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="${1:-dev.codex.aaparkedyoutube}"
OUT_DIR="outputs/diagnostics"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="${OUT_DIR}/android-auto-${STAMP}.txt"

mkdir -p "${OUT_DIR}"

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found. Install Android platform-tools first." >&2
    exit 1
fi

{
    echo "Android Auto diagnostics"
    echo "Date: $(date)"
    echo "Package: ${PACKAGE_NAME}"
    echo

    echo "== adb devices =="
    adb devices -l
    echo

    echo "== device =="
    adb shell getprop ro.product.manufacturer || true
    adb shell getprop ro.product.model || true
    adb shell getprop ro.build.version.release_or_codename || true
    adb shell getprop ro.build.version.sdk || true
    echo

    echo "== installed packages =="
    adb shell pm list packages | grep -E "(${PACKAGE_NAME}|com.google.android.projection.gearhead|com.google.android.gms)" || true
    echo

    echo "== package dump =="
    adb shell dumpsys package "${PACKAGE_NAME}" || true
    echo

    echo "== car app service query =="
    adb shell cmd package query-services --brief -a androidx.car.app.CarAppService 2>/dev/null || true
    adb shell cmd package query-services -a androidx.car.app.CarAppService 2>/dev/null || true
    echo

    echo "== android auto package dump snippets =="
    adb shell dumpsys package com.google.android.projection.gearhead | grep -Ei "versionName|versionCode|enabled|stopped|installer|source|unknown|developer" || true
    echo

    echo "== installer/source for ${PACKAGE_NAME} =="
    adb shell cmd package get-install-location 2>/dev/null || true
    adb shell pm path "${PACKAGE_NAME}" || true
    adb shell dumpsys package "${PACKAGE_NAME}" | grep -Ei "installer|install|source|enabled|stopped|hidden|suspended|category|CarAppService|automotive|gearhead" || true
    echo

    echo "== recent Android Auto / car logs =="
    adb logcat -d -v time | grep -Ei "(${PACKAGE_NAME}|androidx.car.app|CarApp|CarAppService|gearhead|projection|android auto|car app|carservice|HostValidator|denied|reject|invalid|unknown source|not allowed)" | tail -300 || true
} | tee "${OUT_FILE}"

echo
echo "Diagnostics saved to ${OUT_FILE}"
