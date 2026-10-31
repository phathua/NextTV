#!/usr/bin/env bash
# NextTV - Build Release APK and Deploy to Android TV (Linux & macOS)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${TOOLS_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Print TrueColor ANSI banner if available
if [[ -f "${TOOLS_DIR}/banner.ans" ]]; then
    cat "${TOOLS_DIR}/banner.ans"
    echo ""
fi

# 1. Dam bao ADB platform-tools co san tren he thong (khong bi out process khi source)
source "${SCRIPT_DIR}/ensure_adb.sh"

# 2. Tu dong kiem tra JDK va Android SDK local.properties
if [[ ! -f "${REPO_ROOT}/local.properties" ]]; then
    SDK_CANDIDATES=(
        "${ANDROID_HOME:-}"
        "${ANDROID_SDK_ROOT:-}"
        "${HOME}/Library/Android/sdk"
        "${HOME}/Android/Sdk"
    )
    for cand in "${SDK_CANDIDATES[@]}"; do
        if [[ -n "${cand}" && -d "${cand}" ]]; then
            echo "sdk.dir=${cand}" > "${REPO_ROOT}/local.properties"
            echo "[+] Created local.properties pointing to: ${cand}"
            break
        fi
    done
fi

if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
    JDK_CANDIDATES=(
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
        "/Applications/Android Studio.app/Contents/jbr"
        "/opt/android-studio/jbr"
        "/usr/lib/jvm/java-17-openjdk"
    )
    for jcand in "${JDK_CANDIDATES[@]}"; do
        if [[ -x "${jcand}/bin/java" ]]; then
            export JAVA_HOME="${jcand}"
            export PATH="${jcand}/bin:${PATH}"
            echo "[+] JAVA_HOME set to: ${jcand}"
            break
        fi
    done
fi

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
