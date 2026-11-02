#!/usr/bin/env bash
# NextTV - 1-Click Build & Deploy Script for Linux & macOS
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

C_RESET="\033[0m"
C_BOLD="\033[1m"
C_GREEN="\033[1;32m"
C_RED="\033[1;31m"
C_YELLOW="\033[1;33m"

cleanup() {
    local exit_code=$?
    echo ""
    if [[ $exit_code -ne 0 ]]; then
        printf "%b==================================================%b\n" "$C_RED" "$C_RESET"
        printf "%b [X] Quá trình triển khai gặp lỗi (Mã lỗi: %d).%b\n" "$C_BOLD$C_RED" "$exit_code" "$C_RESET"
        printf "%b==================================================%b\n" "$C_RED" "$C_RESET"
    fi
    echo ""
    printf "%b👉 Bấm phím Enter hoặc phím bất kỳ để đóng cửa sổ...%b " "$C_YELLOW" "$C_RESET"
    read -r _ || true
    exit $exit_code
}
trap cleanup EXIT

bash "${SCRIPT_DIR}/tools/sh/build_and_deploy.sh" "$@"
