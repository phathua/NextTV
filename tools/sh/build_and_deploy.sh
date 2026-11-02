#!/usr/bin/env bash
# NextTV - Build Release APK and Deploy to Android TV (Linux & macOS)
# Clean single-line progress, animated spinners, vibrant ANSI colors & zero log flooding.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${TOOLS_DIR}/.." && pwd)"
CACHE_FILE="${REPO_ROOT}/.last_tv_ip"
LOG_DIR="${TOOLS_DIR}/logs"
mkdir -p "${LOG_DIR}"

TIMESTAMP=$(date '+%Y-%m-%d_%H-%M-%S')
LOG_FILE="${LOG_DIR}/deploy_${TIMESTAMP}.log"
cd "${REPO_ROOT}"

# ANSI Colors (Compatible with macOS Terminal.app and Linux)
C_RESET="\033[0m"
C_BOLD="\033[1m"
C_CYAN="\033[1;36m"
C_GREEN="\033[1;32m"
C_YELLOW="\033[1;33m"
C_RED="\033[1;31m"
C_WHITE="\033[1;37m"
C_GRAY="\033[90m"

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "${LOG_FILE}"
}

# Run command with single-line animated spinner, logging stdout+stderr to $LOG_FILE
run_step() {
    local step_title="$1"
    shift

    log_msg "----------------------------------------------------"
    log_msg "Starting Step: ${step_title}"
    log_msg "Command: $*"

    "$@" >> "${LOG_FILE}" 2>&1 &
    local pid=$!
    local spin=("⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏")
    local i=0

    # Hide cursor
    printf "\033[?25l"

    while kill -0 "$pid" 2>/dev/null; do
        printf "\r\033[K%b%s%b %b%s...%b" "$C_CYAN" "${spin[i]}" "$C_RESET" "$C_WHITE" "${step_title}" "$C_RESET"
        i=$(( (i + 1) % 10 ))
        sleep 0.08
    done

    wait "$pid"
    local ec=$?

    # Restore cursor
    printf "\033[?25h"

    if [[ $ec -eq 0 ]]; then
        printf "\r\033[K%b[✓] %s hoàn tất.%b\n" "$C_GREEN" "${step_title}" "$C_RESET"
        log_msg "Completed: ${step_title}"
        return 0
    else
        printf "\r\033[K%b[X] %s thất bại! (Mã lỗi: %d)%b\n" "$C_RED" "${step_title}" "$ec" "$C_RESET"
        log_msg "Failed: ${step_title} (exit code ${ec})"
        echo ""
        printf "%b--- 15 dòng log gần nhất: ---%b\n" "$C_YELLOW" "$C_RESET"
        tail -n 15 "${LOG_FILE}"
        printf "%b-----------------------------%b\n" "$C_YELLOW" "$C_RESET"
        printf "%b📄 Chi tiết lỗi được lưu tại: %b%s%b\n" "$C_YELLOW" "$C_BOLD" "${LOG_FILE}" "$C_RESET"
        return $ec
    fi
}

# Print ANSI banner if available
if [[ -f "${TOOLS_DIR}/banner.ans" ]]; then
    cat "${TOOLS_DIR}/banner.ans"
    echo ""
fi

# 1. Ensure ADB platform-tools & Environment
ensure_environment() {
    source "${SCRIPT_DIR}/ensure_adb.sh"

    if [[ ! -f "${REPO_ROOT}/local.properties" ]]; then
        local sdk_candidates=(
            "${ANDROID_HOME:-}"
            "${ANDROID_SDK_ROOT:-}"
            "${HOME}/Library/Android/sdk"
            "${HOME}/Android/Sdk"
        )
        for cand in "${sdk_candidates[@]}"; do
            if [[ -n "${cand}" && -d "${cand}" ]]; then
                echo "sdk.dir=${cand}" > "${REPO_ROOT}/local.properties"
                break
            fi
        done
    fi

    if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
        local jdk_candidates=(
            "${HOME}/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home"
            "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
            "/Applications/Android Studio.app/Contents/jbr"
            "/opt/android-studio/jbr"
            "/usr/lib/jvm/java-17-openjdk"
        )
        for jcand in "${jdk_candidates[@]}"; do
            if [[ -x "${jcand}/bin/java" ]]; then
                export JAVA_HOME="${jcand}"
                export PATH="${jcand}/bin:${PATH}"
                break
            fi
        done
    fi
}

run_step "[1/5] Kiểm tra công cụ ADB và môi trường biên dịch" ensure_environment

# 2. Discover and Select Target Device
TV_HOST="${1:-${TV_IP:-}}"

if [[ -z "$TV_HOST" ]]; then
    printf "%b>>> [2/5] Đang tìm kiếm Android TV trên mạng LAN...%b\n" "$C_CYAN" "$C_RESET"
    bash "${SCRIPT_DIR}/find_tv_ip.sh"
    if [[ -f "${CACHE_FILE}" ]]; then
        TV_HOST=$(head -n 1 "${CACHE_FILE}" | tr -d '[:space:]')
    fi
fi

if [[ -z "$TV_HOST" ]]; then
    printf "%b[-] Chưa xác định được IP thiết bị Android TV. Hủy bỏ.%b\n" "$C_RED" "$C_RESET"
    exit 1
fi

TARGET="${TV_HOST}"
if [[ "$TARGET" != *:* ]]; then
    TARGET="${TARGET}:5555"
fi

# 3. Connect ADB and Handle Authorization Loop
printf "%b>>> Đang kết nối ADB tới %b%s%b...%b\n" "$C_CYAN" "$C_BOLD" "${TARGET}" "$C_RESET" "$C_RESET"
adb connect "${TARGET}" >> "${LOG_FILE}" 2>&1 || true
sleep 1

MAX_WAIT=90
WAITED=0
SHOWN_UNAUTH_BANNER=false

while true; do
    DEV_STATE=$(adb devices 2>/dev/null | awk -v t="$TARGET" '$1==t {print $2; exit}')
    if [[ -z "$DEV_STATE" ]]; then
        BARE_IP="${TARGET%%:*}"
        DEV_STATE=$(adb devices 2>/dev/null | awk -v t="$BARE_IP" '$1==t {print $2; exit}')
    fi

    if [[ "$DEV_STATE" == "device" ]]; then
        if [[ "$SHOWN_UNAUTH_BANNER" == true ]]; then
            echo ""
        fi
        printf "%b[✓] Thiết bị %b%s%b đã kết nối & xác thực thành công!%b\n" "$C_GREEN" "$C_BOLD" "${TARGET}" "$C_RESET" "$C_RESET"
        break
    elif [[ "$DEV_STATE" == "unauthorized" ]]; then
        if [[ "$SHOWN_UNAUTH_BANNER" == false ]]; then
            echo ""
            printf "%b==========================================================================%b\n" "$C_YELLOW" "$C_RESET"
            printf "%b [!] MÀN HÌNH TV ĐANG CHỜ XÁC THỰC KẾT NỐI (ALLOW USB DEBUGGING)%b\n" "$C_BOLD$C_YELLOW" "$C_RESET"
            printf "%b==========================================================================%b\n" "$C_YELLOW" "$C_RESET"
            printf "%b 👉 Hãy cầm remote TV, tích chọn 'Luôn cho phép từ máy tính này'%b\n" "$C_WHITE" "$C_RESET"
            printf "%b    rồi bấm [Cho phép / OK / Allow].%b\n" "$C_WHITE" "$C_RESET"
            printf "%b ⏳ Đang chờ xác nhận... (Tự kiểm tra sau mỗi 2s, hoặc bấm [Enter]/[Space]%b\n" "$C_GRAY" "$C_RESET"
            printf "%b    để kiểm tra ngay lập tức, Ctrl+C để hủy)%b\n" "$C_GRAY" "$C_RESET"
            printf "%b==========================================================================%b\n" "$C_YELLOW" "$C_RESET"
            SHOWN_UNAUTH_BANNER=true
        fi

        # Non-blocking read with 2-second timeout: Enter or Space triggers immediate re-check
        if read -t 2 -n 1 -r _key; then
            printf " %b[Đang kiểm tra lại...]%b" "$C_CYAN" "$C_RESET"
        else
            printf "%b.%b" "$C_GRAY" "$C_RESET"
        fi
        WAITED=$((WAITED + 2))

        if (( WAITED >= MAX_WAIT )); then
            echo ""
            printf "%b[!] Đã chờ quá %d giây mà TV chưa xác nhận.%b\n" "$C_YELLOW" "$MAX_WAIT" "$C_RESET"
            read -r -p "👉 Bạn có muốn tiếp tục chờ không? (Y/n): " KEEP_WAITING
            KEEP_WAITING="${KEEP_WAITING:-y}"
            if [[ "$KEEP_WAITING" =~ ^[Yy] ]]; then
                WAITED=0
            else
                printf "%b[-] Hủy bỏ tiến trình do TV chưa cấp quyền kết nối ADB.%b\n" "$C_RED" "$C_RESET"
                exit 1
            fi
        fi
    elif [[ "$DEV_STATE" == "offline" ]]; then
        echo ""
        printf "%b[*] Thiết bị đang offline. Đang khởi động lại kết nối...%b\n" "$C_YELLOW" "$C_RESET"
        adb disconnect "${TARGET}" >> "${LOG_FILE}" 2>&1 || true
        sleep 1
        adb connect "${TARGET}" >> "${LOG_FILE}" 2>&1 || true
        sleep 2
    else
        adb connect "${TARGET}" >> "${LOG_FILE}" 2>&1 || true
        sleep 1
        WAITED=$((WAITED + 1))
        if (( WAITED >= 15 && SHOWN_UNAUTH_BANNER == false )); then
            echo ""
            printf "%b[-] Không thể kết nối tới %s. Vui lòng kiểm tra lại mạng Wi-Fi và TV.%b\n" "$C_RED" "${TARGET}" "$C_RESET"
            exit 1
        fi
    fi
done

# Query target device model & CPU architecture
DEVICE_MODEL=$(adb -s "${TARGET}" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n' || true)
DEVICE_MFTR=$(adb -s "${TARGET}" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r\n' || true)
DEVICE_ABI=$(adb -s "${TARGET}" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r\n' || true)
printf "%b[✓] [2/5] Đã nhận diện: %b%s %s%b %b(Kiến trúc CPU: %s)%b\n" "$C_GREEN" "$C_BOLD" "${DEVICE_MFTR}" "${DEVICE_MODEL}" "$C_RESET" "$C_CYAN" "${DEVICE_ABI:-unknown}" "$C_RESET"

# 4. Generate Native Secrets Header & Build APK
run_step "[3/5] Tạo mã bảo mật Native Secrets (NDK)" bash "${SCRIPT_DIR}/generate_header.sh"

run_step "[4/5] Biên dịch Sideload Release APK với Gradle" ./gradlew assembleSideloadRelease --console=plain

# Select best APK matching TV architecture
APK_DIR="app/build/outputs/apk/sideload/release"
if [[ ! -d "${APK_DIR}" ]]; then
    APK_DIR="app/build/outputs/apk/release"
fi

APK=""
if [[ "${DEVICE_ABI}" =~ ^arm64 ]] && ls "${APK_DIR}"/*arm64*.apk >/dev/null 2>&1; then
    APK=$(ls "${APK_DIR}"/*arm64*.apk | head -n 1)
elif [[ "${DEVICE_ABI}" =~ ^armeabi ]] && ls "${APK_DIR}"/*armeabi*.apk >/dev/null 2>&1; then
    APK=$(ls "${APK_DIR}"/*armeabi*.apk | head -n 1)
elif ls "${APK_DIR}"/*universal*.apk >/dev/null 2>&1; then
    APK=$(ls "${APK_DIR}"/*universal*.apk | head -n 1)
else
    APK=$(find "${APK_DIR}" -name "*.apk" | head -n 1 || true)
fi

if [[ -z "${APK}" || ! -f "${APK}" ]]; then
    printf "%b[-] Không tìm thấy file APK release trong %s!%b\n" "$C_RED" "${APK_DIR}" "$C_RESET"
    exit 1
fi

# 5. Install APK on TV Box
install_apk_on_tv() {
    if ! adb -s "${TARGET}" install -r "${APK}" >> "${LOG_FILE}" 2>&1; then
        log_msg "First install attempt failed, trying uninstall old version..."
        adb -s "${TARGET}" uninstall com.next.tv.launcher >> "${LOG_FILE}" 2>&1 || true
        adb -s "${TARGET}" install -r "${APK}" >> "${LOG_FILE}" 2>&1
    fi
}

run_step "[5/5] Cài đặt APK $(basename "${APK}") lên TV" install_apk_on_tv

# 6. Launch NextTV
adb -s "${TARGET}" shell am start -n com.next.tv.launcher/.MainActivity >> "${LOG_FILE}" 2>&1 || true

echo ""
printf "%b==================================================%b\n" "$C_GREEN" "$C_RESET"
printf "%b [✓] TRIỂN KHAI NEXTTV THÀNH CÔNG!%b\n" "$C_BOLD$C_GREEN" "$C_RESET"
printf "%b     Ứng dụng đã được khởi chạy trên TV.%b\n" "$C_WHITE" "$C_RESET"
printf "%b==================================================%b\n" "$C_GREEN" "$C_RESET"
