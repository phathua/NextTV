#!/usr/bin/env bash
# NextTV - High-speed LAN Scanner for Android TV / ADB (Linux & macOS)
# Supports auto-detecting subnet, rapid scanning, multi-device listing & interactive selection.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${TOOLS_DIR}/.." && pwd)"
CACHE_FILE="${REPO_ROOT}/.last_tv_ip"

# ANSI Colors
C_RESET="\033[0m"
C_BOLD="\033[1m"
C_CYAN="\033[1;36m"
C_GREEN="\033[1;32m"
C_YELLOW="\033[1;33m"
C_RED="\033[1;31m"
C_WHITE="\033[1;37m"
C_GRAY="\033[90m"

OUTPUT_ALL=false
if [[ "${1:-}" == "--all" ]]; then
    OUTPUT_ALL=true
fi

# Ensure ADB is available in PATH
if command -v adb >/dev/null 2>&1; then
    :
elif [[ -x "${HOME}/Library/Android/sdk/platform-tools/adb" ]]; then
    export PATH="${HOME}/Library/Android/sdk/platform-tools:${PATH}"
fi

test_port() {
    local ip="$1"
    local port="${2:-5555}"
    if command -v nc >/dev/null 2>&1; then
        nc -z -w 1 "$ip" "$port" 2>/dev/null
        return $?
    else
        (exec 3<>"/dev/tcp/${ip}/${port}") 2>/dev/null && exec 3<&- && return 0
        return 1
    fi
}

# 1. Detect IPv4 subnet cleanly on macOS & Linux
detect_subnet() {
    local subnet=""
    # Method 1: Linux ip route
    if command -v ip >/dev/null 2>&1; then
        subnet=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7; exit}' | cut -d'.' -f1-3 || true)
    fi

    # Method 2: macOS / BSD route & ifconfig
    if [[ -z "$subnet" ]] && command -v route >/dev/null 2>&1; then
        local iface
        iface=$(route -n get 8.8.8.8 2>/dev/null | awk '/interface:/{print $2; exit}')
        if [[ -n "$iface" ]]; then
            subnet=$(ifconfig "$iface" 2>/dev/null | awk '/inet /{print $2; exit}' | cut -d'.' -f1-3 || true)
        fi
    fi

    # Method 3: Python fallback if available
    if [[ -z "$subnet" ]] && command -v python3 >/dev/null 2>&1; then
        subnet=$(python3 -c "import socket; s=socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.connect(('8.8.8.8', 80)); print('.'.join(s.getsockname()[0].split('.')[:3])); s.close()" 2>/dev/null || true)
    fi

    echo "$subnet"
}

# 2. Scan subnet for open port 5555
scan_devices_on_network() {
    local subnet="$1"
    if [[ -z "$subnet" ]]; then
        return
    fi

    if command -v python3 >/dev/null 2>&1; then
        python3 -c "
import socket, concurrent.futures, sys
subnet = '${subnet}'
def check(ip):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(0.35)
        res = s.connect_ex((ip, 5555))
        s.close()
        if res == 0:
            return f'{ip}:5555'
    except Exception:
        pass
    return None

ips = [f'{subnet}.{i}' for i in range(1, 255)]
with concurrent.futures.ThreadPoolExecutor(max_workers=60) as ex:
    for t in ex.map(check, ips):
        if t:
            print(t)
" 2>/dev/null || true
        return
    fi

    local tmp_file
    tmp_file=$(mktemp)
    for i in $(seq 1 254); do
        (
            local target="${subnet}.${i}"
            if test_port "${target}" 5555; then
                echo "${target}:5555" >> "${tmp_file}"
            fi
        ) &
    done
    wait
    if [[ -f "${tmp_file}" ]]; then
        cat "${tmp_file}"
        rm -f "${tmp_file}"
    fi
}

# Get human-readable device model and state via ADB
get_device_label() {
    local target="$1"
    local state="unknown"
    if command -v adb >/dev/null 2>&1; then
        state=$(adb devices 2>/dev/null | awk -v t="$target" '$1==t {print $2; exit}')
        if [[ -z "$state" && "$target" =~ :[0-9]+$ ]]; then
            local bare_ip="${target%%:*}"
            state=$(adb devices 2>/dev/null | awk -v t="$bare_ip" '$1==t {print $2; exit}')
        fi
    fi

    local model_name=""
    if [[ "$state" == "device" ]]; then
        local mftr model
        mftr=$(adb -s "$target" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r\n' || true)
        model=$(adb -s "$target" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n' || true)
        if [[ -n "$mftr" && -n "$model" ]]; then
            model_name="${mftr} ${model}"
        elif [[ -n "$model" ]]; then
            model_name="${model}"
        fi
        echo "${model_name:-Android TV} (${target}) [Đã kết nối]"
    elif [[ "$state" == "unauthorized" ]]; then
        echo "Android TV (${target}) [Chờ xác thực quyền / Unauthorized]"
    elif [[ "$state" == "offline" ]]; then
        echo "Android TV (${target}) [Offline]"
    else
        echo "Android TV (${target}) [Đang mở cổng 5555]"
    fi
}

# Collect all candidates: connected devices + network scan
CANDIDATE_LIST=()

# 1. Existing ADB devices
if command -v adb >/dev/null 2>&1; then
    while read -r line; do
        if [[ "$line" =~ ^([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(:[0-9]+)?)[[:space:]]+(device|unauthorized|offline) ]]; then
            dev="${BASH_REMATCH[1]}"
            if [[ "$dev" != *:* ]]; then
                dev="${dev}:5555"
            fi
            CANDIDATE_LIST+=("$dev")
        fi
    done < <(adb devices 2>/dev/null | tail -n +2 || true)
fi

# 2. LAN scan on subnet
SUBNET=$(detect_subnet)
if [[ -n "$SUBNET" ]]; then
    while read -r dev; do
        if [[ -n "$dev" ]]; then
            already=false
            for existing in "${CANDIDATE_LIST[@]:-}"; do
                if [[ "$existing" == "$dev" ]]; then
                    already=true
                    break
                fi
            done
            if [[ "$already" == false ]]; then
                CANDIDATE_LIST+=("$dev")
            fi
        fi
    done < <(scan_devices_on_network "$SUBNET")
fi

if [[ "$OUTPUT_ALL" == true ]]; then
    for cand in "${CANDIDATE_LIST[@]:-}"; do
        echo "__CANDIDATE__:${cand}"
    done
    exit 0
fi

TOTAL_FOUND=${#CANDIDATE_LIST[@]}

if [[ $TOTAL_FOUND -eq 0 ]]; then
    printf "%b[!] Không tìm thấy thiết bị Android TV nào đang mở cổng ADB 5555 trên mạng %s.0/24.%b\n" "$C_YELLOW" "${SUBNET:-local}" "$C_RESET"
    
    if [[ -f "${CACHE_FILE}" ]]; then
        CACHED_IP="$(head -n 1 "${CACHE_FILE}" | tr -d '[:space:]')"
        if [[ -n "${CACHED_IP}" ]]; then
            read -r -p "👉 Thử kết nối lại địa chỉ IP gần nhất [${CACHED_IP}]? (Y/n): " use_cached
            use_cached="${use_cached:-y}"
            if [[ "$use_cached" =~ ^[Yy] ]]; then
                echo "__FOUND_IP__:${CACHED_IP}"
                exit 0
            fi
        fi
    fi

    read -r -p "👉 Vui lòng nhập địa chỉ IP TV thủ công (ví dụ 192.168.1.100): " manual_ip
    manual_ip="${manual_ip:-}"
    if [[ -n "$manual_ip" ]]; then
        echo "${manual_ip}" > "${CACHE_FILE}"
        echo "__FOUND_IP__:${manual_ip}"
        exit 0
    else
        printf "%b[-] Không có IP TV. Hủy bỏ.%b\n" "$C_RED" "$C_RESET"
        exit 1
    fi

elif [[ $TOTAL_FOUND -eq 1 ]]; then
    CHOSEN="${CANDIDATE_LIST[0]}"
    LABEL=$(get_device_label "$CHOSEN")
    printf "%b[+] Tìm thấy 1 thiết bị Android TV: %b%s%b\n" "$C_GREEN" "$C_BOLD" "${LABEL}" "$C_RESET"
    echo "${CHOSEN%%:*}" > "${CACHE_FILE}"
    echo "__FOUND_IP__:${CHOSEN}"
    exit 0

else
    CACHED_IP=""
    if [[ -f "${CACHE_FILE}" ]]; then
        CACHED_IP="$(head -n 1 "${CACHE_FILE}" | tr -d '[:space:]')"
    fi

    echo ""
    printf "%b==========================================================================%b\n" "$C_CYAN" "$C_RESET"
    printf "%b [?] TÌM THẤY %d THIẾT BỊ ANDROID TV / CỔNG ADB ĐANG MỞ:%b\n" "$C_BOLD$C_CYAN" "$TOTAL_FOUND" "$C_RESET"
    printf "%b==========================================================================%b\n" "$C_CYAN" "$C_RESET"
    
    DEFAULT_IDX=1
    for i in "${!CANDIDATE_LIST[@]}"; do
        idx=$((i + 1))
        dev="${CANDIDATE_LIST[$i]}"
        dev_ip="${dev%%:*}"
        label=$(get_device_label "$dev")
        marker=""
        if [[ -n "$CACHED_IP" && "$dev_ip" == "$CACHED_IP" ]]; then
            marker=" (Dùng gần nhất)"
            DEFAULT_IDX=$idx
        fi
        printf "  %b[%d]%b %b%s%b%b%s%b\n" "$C_CYAN" "$idx" "$C_RESET" "$C_WHITE" "${label}" "$C_RESET" "$C_YELLOW" "${marker}" "$C_RESET"
    done
    printf "  %b[m]%b %bNhập địa chỉ IP / Cổng khác thủ công...%b\n" "$C_CYAN" "$C_RESET" "$C_GRAY" "$C_RESET"
    printf "%b==========================================================================%b\n" "$C_CYAN" "$C_RESET"

    while true; do
        read -r -p "👉 Vui lòng chọn thiết bị muốn sử dụng [1-${TOTAL_FOUND}] (Mặc định: ${DEFAULT_IDX}): " user_choice
        user_choice="${user_choice:-$DEFAULT_IDX}"
        if [[ "$user_choice" == "m" || "$user_choice" == "M" ]]; then
            read -r -p "👉 Nhập IP:Cổng thiết bị (ví dụ 192.168.1.100:5555): " manual_choice
            if [[ -n "$manual_choice" ]]; then
                echo "${manual_choice%%:*}" > "${CACHE_FILE}"
                echo "__FOUND_IP__:${manual_choice}"
                exit 0
            fi
        elif [[ "$user_choice" =~ ^[0-9]+$ ]] && (( user_choice >= 1 && user_choice <= TOTAL_FOUND )); then
            CHOSEN="${CANDIDATE_LIST[$((user_choice - 1))]}"
            echo "${CHOSEN%%:*}" > "${CACHE_FILE}"
            printf "%b[+] Đã chọn thiết bị: %b%s%b\n" "$C_GREEN" "$C_BOLD" "$(get_device_label "$CHOSEN")" "$C_RESET"
            echo "__FOUND_IP__:${CHOSEN}"
            exit 0
        else
            printf "%b[!] Lựa chọn không hợp lệ. Vui lòng nhập từ 1 đến %d.%b\n" "$C_RED" "$TOTAL_FOUND" "$C_RESET"
        fi
    done
fi
