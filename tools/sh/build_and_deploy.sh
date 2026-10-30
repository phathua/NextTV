#!/usr/bin/env bash
# NextTV - Build Release APK and Deploy to Android TV (Linux & macOS)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

echo ">>> [1/4] Generating Native Secrets Header..."
bash "${SCRIPT_DIR}/generate_header.sh"

echo ">>> [2/4] Building Release APK with Gradle..."
./gradlew assembleRelease --console=plain

APK=$(find app/build/outputs/apk/release -name "*.apk" 2>/dev/null | head -n 1)
echo ">>> Found APK: ${APK}"

TV_HOST="${1:-${TV_IP:-}}"
if [[ -z "$TV_HOST" ]]; then
    echo ">>> Scanning for TV on LAN..."
    TV_HOST=$(bash "${SCRIPT_DIR}/find_tv_ip.sh" 2>/dev/null | grep "Found Android TV at:" | head -n 1 | awk '{print $NF}' || true)
fi

if [[ -n "$TV_HOST" ]]; then
    echo ">>> Connecting to TV at ${TV_HOST}:5555..."
    adb connect "${TV_HOST}:5555"
fi

echo ">>> [3/4] Installing NextTV APK..."
adb install -r "${APK}"

echo ">>> [4/4] Launching NextTV..."
adb shell am start -n com.next.tv.launcher/.MainActivity

if [[ "${2:-}" == "--default" ]]; then
    echo ">>> Setting NextTV as default Home..."
    adb shell cmd package set-home-activity com.next.tv.launcher/.MainActivity
fi

echo ">>> Done! NextTV is active."
