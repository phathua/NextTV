#!/usr/bin/env bash
# NextTV - Ensure Android ADB (Platform Tools) is Available on Linux & macOS
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOCAL_ADB_DIR="${TOOLS_DIR}/platform-tools"
LOCAL_ADB="${LOCAL_ADB_DIR}/adb"

is_adb_working() {
    if command -v adb >/dev/null 2>&1; then
        return 0
    elif [[ -x "${LOCAL_ADB}" ]]; then
        export PATH="${LOCAL_ADB_DIR}:${PATH}"
        return 0
    fi

    # Check common Android SDK locations on macOS and Linux
    local SDK_CANDIDATES=(
        "${ANDROID_HOME:-}"
        "${ANDROID_SDK_ROOT:-}"
        "${HOME}/Library/Android/sdk"
        "${HOME}/Android/Sdk"
        "/usr/lib/android-sdk"
    )

    for cand in "${SDK_CANDIDATES[@]}"; do
        if [[ -n "${cand}" && -x "${cand}/platform-tools/adb" ]]; then
            export PATH="${cand}/platform-tools:${PATH}"
            return 0
        fi
    done

    return 1
}

if is_adb_working; then
    return 0 2>/dev/null || exit 0
fi

echo "========================================================"
echo " [*] ADB chua co tren may. Dang tu dong tai platform-tools tu Google..."
echo "========================================================"

OS_TYPE="$(uname -s)"
ZIP_URL=""
if [[ "${OS_TYPE}" == "Darwin" ]]; then
    ZIP_URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
elif [[ "${OS_TYPE}" == "Linux" ]]; then
    ZIP_URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip"
else
    echo "[-] He dieu hanh khong duoc ho tro tu dong: ${OS_TYPE}"
    return 1 2>/dev/null || exit 1
fi

ZIP_FILE="${TOOLS_DIR}/platform-tools.zip"

echo ">>> Downloading: ${ZIP_URL}"
if command -v curl >/dev/null 2>&1; then
    curl -fSL "${ZIP_URL}" -o "${ZIP_FILE}"
elif command -v wget >/dev/null 2>&1; then
    wget -q "${ZIP_URL}" -O "${ZIP_FILE}"
else
    echo "[-] Can curl hoac wget de tai platform-tools!"
    return 1 2>/dev/null || exit 1
fi

echo ">>> Extracting platform-tools to: ${TOOLS_DIR}"
rm -rf "${LOCAL_ADB_DIR}"
if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "${ZIP_FILE}" -d "${TOOLS_DIR}"
else
    tar -xzf "${ZIP_FILE}" -C "${TOOLS_DIR}" 2>/dev/null || true
fi
rm -f "${ZIP_FILE}"

if [[ -x "${LOCAL_ADB}" ]]; then
    export PATH="${LOCAL_ADB_DIR}:${PATH}"
    echo "[+] Cai dat ADB thanh cong tai: ${LOCAL_ADB_DIR}"
    return 0 2>/dev/null || exit 0
else
    echo "[-] Khong the thiet lap adb executable!"
    return 1 2>/dev/null || exit 1
fi
