#!/usr/bin/env bash
# NextTV - Build Release APK and Deploy to Android TV (Linux & macOS)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

# 1. Dam bao ADB platform-tools co san tren he thong
source "${SCRIPT_DIR}/ensure_adb.sh"

echo ">>> [1/4] Generating Native Secrets Header..."
bash "${SCRIPT_DIR}/generate_header.sh"

echo ">>> [2/4] Building Release APK with Gradle..."
./gradlew assembleRelease --console=plain

APK=$(find app/build/outputs/apk/release -name "*.apk" 2>/dev/null | head -n 1 || true)
if [[ -z "${APK}" ]]; then
    echo "[-] Could not find any release APK in app/build/outputs/apk/release!"
    exit 1
fi
echo ">>> Found APK: ${APK}"

TV_HOST="${1:-${TV_IP:-}}"
if [[ -z "$TV_HOST" ]]; then
    echo ">>> Scanning for TV on LAN..."
    SCAN_OUT=$(bash "${SCRIPT_DIR}/find_tv_ip.sh" 2>/dev/null || true)
    TV_HOST=$(echo "${SCAN_OUT}" | grep "Found Android TV at:" | head -n 1 | awk '{print $NF}' || true)
fi

if [[ -n "$TV_HOST" ]]; then
    echo ">>> Connecting to TV at ${TV_HOST}:5555..."
    adb connect "${TV_HOST}:5555" || true
    sleep 1
    TARGET_OPT=("-s" "${TV_HOST}:5555")
else
    echo ">>> No specific TV IP detected, using default ADB device..."
    TARGET_OPT=()
fi

echo ">>> [3/4] Installing NextTV APK..."
if ! adb "${TARGET_OPT[@]}" install -r "${APK}"; then
    echo "[!] Targeted install failed, trying default ADB fallback..."
    adb install -r "${APK}"
fi

echo ">>> [4/4] Launching NextTV..."
adb "${TARGET_OPT[@]}" shell am start -n com.next.tv.launcher/.MainActivity

if [[ "${2:-}" == "--default" ]]; then
    echo ">>> Setting NextTV as default Home..."
    adb "${TARGET_OPT[@]}" shell cmd package set-home-activity com.next.tv.launcher/.MainActivity || true
fi

echo ">>> Done! NextTV is active."
