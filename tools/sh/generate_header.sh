#!/usr/bin/env bash
# NextTV - Native Header Generator for NDK Build (Linux & macOS)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

if command -v python3 >/dev/null 2>&1; then
    python3 "${REPO_ROOT}/tools/python/generate_native_secrets.py"
elif command -v python >/dev/null 2>&1; then
    python "${REPO_ROOT}/tools/python/generate_native_secrets.py"
else
    echo "[!] Python 3 is required on Linux/macOS to generate native secrets header."
    exit 1
fi
