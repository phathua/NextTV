#!/usr/bin/env bash
# NextTV - Fast LAN Scanner for Android TV on Port 5555 (Linux & macOS)
# Zero external dependencies. Uses Bash built-in /dev/tcp with fast ARP/cache fallback.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${TOOLS_DIR}/.." && pwd)"
CACHE_FILE="${REPO_ROOT}/.last_tv_ip"

test_port() {
    local ip="$1"
    local port="${2:-5555}"
    if command -v nc >/dev/null 2>&1; then
        nc -z -w 1 "$ip" "$port" 2>/dev/null
        return $?
    else
        # Bash built-in /dev/tcp socket check with subshell timeout
        (exec 3<>"/dev/tcp/${ip}/${port}") 2>/dev/null && exec 3<&- && return 0
        return 1
    fi
}

# 1. Fast path: check cached IP
if [[ -f "${CACHE_FILE}" ]]; then
    CACHED_IP="$(head -n 1 "${CACHE_FILE}" | tr -d '[:space:]')"
    if [[ "${CACHED_IP}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        if test_port "${CACHED_IP}" 5555; then
            echo "[+] Reused cached TV Box IP: ${CACHED_IP}"
            echo "__FOUND_IP__:${CACHED_IP}"
            exit 0
        fi
    fi
fi

# 2. Fast path: check already connected ADB devices
if command -v adb >/dev/null 2>&1; then
    CONNECTED_IP=$(adb devices 2>/dev/null | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:5555[[:space:]]+device' | head -n 1 | awk -F':' '{print $1}' || true)
    if [[ -n "${CONNECTED_IP}" ]]; then
        echo "[+] Found already connected Android TV at: ${CONNECTED_IP}"
        echo "${CONNECTED_IP}" > "${CACHE_FILE}"
        echo "__FOUND_IP__:${CONNECTED_IP}"
        exit 0
    fi
fi

# 3. Detect default subnet
SUBNET=""
if command -v ip >/dev/null 2>&1; then
    SUBNET=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7; exit}' | cut -d'.' -f1-3 || true)
fi
if [[ -z "$SUBNET" ]] && command -v route >/dev/null 2>&1; then
    # BSD / macOS compatibility
    SUBNET=$(route -n get 8.8.8.8 2>/dev/null | awk '/interface:/{getline; print $2}' | cut -d'.' -f1-3 || true)
fi

if [[ -z "$SUBNET" ]]; then
    echo "[!] Could not determine local IPv4 subnet."
    exit 0
fi

echo "[*] Scanning subnet ${SUBNET}.0/24 on port 5555..."
TMP_FOUND=$(mktemp)
trap 'rm -f "${TMP_FOUND}"' EXIT

for i in $(seq 1 254); do
    (
        TARGET="${SUBNET}.${i}"
        if test_port "${TARGET}" 5555; then
            echo "${TARGET}" >> "${TMP_FOUND}"
        fi
    ) &
done
wait

FIRST_IP=$(head -n 1 "${TMP_FOUND}" 2>/dev/null || true)
if [[ -n "${FIRST_IP}" ]]; then
    echo "[+] Found Android TV at: ${FIRST_IP}"
    echo "${FIRST_IP}" > "${CACHE_FILE}"
    echo "__FOUND_IP__:${FIRST_IP}"
else
    echo "[!] No active Android TV found on port 5555."
fi
