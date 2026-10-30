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
    return 1
}

if is_adb_working; then
    exit 0
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
    exit 1
fi

ZIP_FILE="${TOOLS_DIR}/platform-tools.zip"

echo ">>> Downloading: ${ZIP_URL}"
if command -v curl >/dev/null 2>&1; then
    curl -fSL "${ZIP_URL}" -o "${ZIP_FILE}"
elif command -v wget >/dev/null 2>&1; then
    wget -q "${ZIP_URL}" -O "${ZIP_FILE}"
else
    echo "[-] Can curl hoac wget de tai platform-tools!"
    exit 1
fi

echo ">>> Extracting platform-tools to: ${TOOLS_DIR}"
if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "${ZIP_FILE}" -d "${TOOLS_DIR}"
    rm -f "${ZIP_FILE}"
else
    echo "[-] Can cong cu unzip de giai nen platform-tools!"
    rm -f "${ZIP_FILE}"
    exit 1
fi

if [[ -f "${LOCAL_ADB}" ]]; then
    chmod +x "${LOCAL_ADB}"
    export PATH="${LOCAL_ADB_DIR}:${PATH}"
    echo "[+] Cai dat ADB thanh cong tai: ${LOCAL_ADB_DIR}"
else
    echo "[-] Khong tim thay binary adb sau khi giai nen!"
    exit 1
fi
