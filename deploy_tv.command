#!/usr/bin/env bash
# NextTV - 1-Click Build & Deploy Script for Linux & macOS
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cleanup() {
    local exit_code=$?
    echo ""
    if [[ $exit_code -eq 0 ]]; then
        echo "=================================================="
        echo " [✓] Hoàn tất triển khai NextTV thành công!"
        echo "=================================================="
    else
        echo "=================================================="
        echo " [X] Tiến trình kết thúc với mã lỗi: $exit_code."
        echo "=================================================="
    fi
    echo ""
    read -r -p "👉 Bấm phím Enter hoặc phím bất kỳ để đóng cửa sổ..." _ || true
    exit $exit_code
}
trap cleanup EXIT

bash "${SCRIPT_DIR}/tools/sh/build_and_deploy.sh" "$@"
