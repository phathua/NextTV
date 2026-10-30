#!/usr/bin/env bash
# NextTV - Fast LAN Scanner for Android TV on Port 5555 (Linux & macOS)
set -euo pipefail

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
for i in $(seq 1 254); do
    (nc -z -w 1 "${SUBNET}.${i}" 5555 2>/dev/null && echo "[+] Found Android TV at: ${SUBNET}.${i}") &
done
wait
