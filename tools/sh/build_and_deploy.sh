#!/usr/bin/env bash
# NextTV - Build Release APK and Deploy to Android TV (Linux & macOS)
# Persistent single-line progress, animated spinners, real-time progress bars & zero clutter.
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

# ANSI Colors (macOS Terminal.app & Linux)
C_RESET="\033[0m"
C_BOLD="\033[1m"
C_CYAN="\033[1;36m"
C_GREEN="\033[1;32m"
C_YELLOW="\033[1;33m"
C_RED="\033[1;31m"
C_WHITE="\033[1;37m"
C_GRAY="\033[90m"

# Ensure cursor is restored on exit
cleanup_cursor() {
    printf "\033[?25h"
}
trap cleanup_cursor EXIT

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "${LOG_FILE}"
}

# 1. Single-line Spinner Drawing
draw_spinner() {
    local step="$1"
    local text="$2"
    local spin_char="$3"

    local term_cols
    term_cols=$(tput cols 2>/dev/null || echo 80)
    local max_len=$(( term_cols - 16 ))
    if (( ${#text} > max_len )); then
        text="${text:0:$((max_len - 3))}..."
    fi

    printf "\r\033[K%b[%s]%b %b%s%b %b%s...%b" \
        "$C_CYAN" "$step" "$C_RESET" \
        "$C_YELLOW" "$spin_char" "$C_RESET" \
        "$C_WHITE" "$text" "$C_RESET"
}

# 2. Single-line Status Drawing
draw_status() {
    local step="$1"
    local text="$2"
    local color="${3:-$C_CYAN}"

    local term_cols
    term_cols=$(tput cols 2>/dev/null || echo 80)
    local max_len=$(( term_cols - 12 ))
    if (( ${#text} > max_len )); then
        text="${text:0:$((max_len - 3))}..."
    fi

    printf "\r\033[K%b[%s]%b %b%s%b" "$C_CYAN" "$step" "$C_RESET" "$color" "$text" "$C_RESET"
}

# 3. Single-line Visual Progress Bar Drawing
draw_progress() {
    local step="$1"
    local percent="$2"
    local label="$3"
    local width=18

    if (( percent > 100 )); then percent=100; fi
    if (( percent < 0 )); then percent=0; fi
    local filled=$(( (percent * width) / 100 ))
    local empty=$(( width - filled ))

    local fill_str=""
    local empty_str=""
    for ((j=0; j<filled; j++)); do fill_str="${fill_str}█"; done
    for ((j=0; j<empty; j++)); do empty_str="${empty_str}░"; done

    local term_cols
    term_cols=$(tput cols 2>/dev/null || echo 80)
    local max_label_len=$(( term_cols - 38 ))
    if (( max_label_len < 10 )); then max_label_len=10; fi
    if (( ${#label} > max_label_len )); then
        label="${label:0:$((max_label_len - 3))}..."
    fi

    printf "\r\033[K%b[%s]%b %b[%b%s%b%s%b]%b %b%3d%%%b %b%s%b" \
        "$C_CYAN" "$step" "$C_RESET" \
        "$C_GRAY" "$C_GREEN" "$fill_str" "$C_GRAY" "$empty_str" "$C_GRAY" "$C_RESET" \
        "$C_BOLD$C_WHITE" "$percent" "$C_RESET" \
        "$C_CYAN" "$label" "$C_RESET"
}

# Run command with spinner on that single line
run_step_spinner() {
    local step="$1"
    local title="$2"
    shift 2

    log_msg "Starting: [${step}] ${title}"
    "$@" >> "${LOG_FILE}" 2>&1 &
    local pid=$!
    local spin=("⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏")
    local i=0

    # Hide cursor
    printf "\033[?25l"

    while kill -0 "$pid" 2>/dev/null; do
        draw_spinner "$step" "$title" "${spin[i]}"
        i=$(( (i + 1) % 10 ))
        sleep 0.08
    done

    wait "$pid"
    local ec=$?

    if [[ $ec -eq 0 ]]; then
        draw_status "$step" "[✓] ${title} hoàn tất" "$C_GREEN"
        sleep 0.35
        return 0
    else
        printf "\n\r\033[K%b[X] [%s] %s thất bại! (Mã lỗi: %d)%b\n" "$C_RED" "$step" "$title" "$ec" "$C_RESET"
        log_msg "Failed: [${step}] ${title} (exit code ${ec})"
        echo ""
        printf "%b--- 15 dòng log gần nhất: ---%b\n" "$C_YELLOW" "$C_RESET"
        tail -n 15 "${LOG_FILE}"
        printf "%b-----------------------------%b\n" "$C_YELLOW" "$C_RESET"
        printf "%b📄 Chi tiết lỗi: %s%b\n" "$C_YELLOW" "${LOG_FILE}" "$C_RESET"
        exit $ec
    fi
}

# Print ANSI banner once at the top
if [[ -f "${TOOLS_DIR}/banner.ans" ]]; then
    cat "${TOOLS_DIR}/banner.ans"
    echo ""
fi

# Hide cursor for clean UI
printf "\033[?25l"

# =========================================================================
# STEP 1: Kiểm tra công cụ ADB & môi trường Java
# =========================================================================
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

run_step_spinner "1/5" "Kiểm tra công cụ ADB & môi trường Java" ensure_environment

# =========================================================================
# STEP 2: Dò tìm & Kết nối Android TV
# =========================================================================
TV_HOST="${1:-${TV_IP:-}}"

if [[ -z "$TV_HOST" ]]; then
    draw_status "2/5" "Đang dò tìm Android TV trên mạng LAN..." "$C_CYAN"
    
    # Run scanner with --quiet so it does not print excess lines when 1 device found
    bash "${SCRIPT_DIR}/find_tv_ip.sh" --quiet
    if [[ -f "${CACHE_FILE}" ]]; then
        TV_HOST=$(head -n 1 "${CACHE_FILE}" | tr -d '[:space:]')
    fi
fi

if [[ -z "$TV_HOST" ]]; then
    printf "\n%b[-] Chưa xác định được IP thiết bị Android TV. Hủy bỏ.%b\n" "$C_RED" "$C_RESET"
    exit 1
fi

TARGET="${TV_HOST}"
if [[ "$TARGET" != *:* ]]; then
    TARGET="${TARGET}:5555"
fi

draw_status "2/5" "Đang kết nối ADB tới ${TARGET}..." "$C_CYAN"
adb connect "${TARGET}" >> "${LOG_FILE}" 2>&1 || true
sleep 0.8

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
        adb disconnect "${TARGET}" >> "${LOG_FILE}" 2>&1 || true
        sleep 1
        adb connect "${TARGET}" >> "${LOG_FILE}" 2>&1 || true
        sleep 2
    else
        adb connect "${TARGET}" >> "${LOG_FILE}" 2>&1 || true
        sleep 1
        WAITED=$((WAITED + 1))
        if (( WAITED >= 15 && SHOWN_UNAUTH_BANNER == false )); then
            printf "\n%b[-] Không thể kết nối tới %s. Vui lòng kiểm tra lại mạng Wi-Fi và TV.%b\n" "$C_RED" "${TARGET}" "$C_RESET"
            exit 1
        fi
    fi
done

DEVICE_MODEL=$(adb -s "${TARGET}" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n' || true)
DEVICE_MFTR=$(adb -s "${TARGET}" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r\n' || true)
DEVICE_ABI=$(adb -s "${TARGET}" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r\n' || true)

NAME="${DEVICE_MFTR} ${DEVICE_MODEL}"
NAME="${NAME#" "}"
NAME="${NAME%" "}"
draw_status "2/5" "[✓] Đã kết nối: ${NAME:-Android TV} (${TARGET})" "$C_GREEN"
sleep 0.5

# =========================================================================
# STEP 3: Tạo mã bảo mật Native Secrets (NDK)
# =========================================================================
run_step_spinner "3/5" "Tạo mã bảo mật Native Secrets (NDK)" bash "${SCRIPT_DIR}/generate_header.sh"

# =========================================================================
# STEP 4: Biên dịch Sideload Release APK với Gradle (Visual Progress Bar)
# =========================================================================
log_msg "Starting Gradle assembleSideloadRelease"
draw_progress "4/5" 5 "Khởi động tiến trình Gradle..."

./gradlew assembleSideloadRelease --console=plain >> "${LOG_FILE}" 2>&1 &
GRADLE_PID=$!

TOTAL_TASKS=55

while kill -0 "$GRADLE_PID" 2>/dev/null; do
    RAW_COUNT=$(grep -c "^> Task :app:" "${LOG_FILE}" 2>/dev/null || true)
    TASK_COUNT=$(echo "${RAW_COUNT}" | tr -cd '0-9')
    if [[ -z "${TASK_COUNT}" ]]; then TASK_COUNT=0; fi

    CURRENT_TASK=$(grep "^> Task :app:" "${LOG_FILE}" 2>/dev/null | tail -n 1 | sed 's/> Task :app://' | awk '{print $1}' || true)

    PCT=$(( (TASK_COUNT * 100) / TOTAL_TASKS ))
    if (( PCT > 98 )); then PCT=98; fi
    if (( PCT < 5 )); then PCT=5; fi

    TASK_LABEL="Biên dịch Release APK"
    if [[ -n "$CURRENT_TASK" ]]; then
        TASK_LABEL="Biên dịch: ${CURRENT_TASK}"
    fi

    draw_progress "4/5" "$PCT" "${TASK_LABEL}"
    sleep 0.12
done

wait "$GRADLE_PID"
GRADLE_EC=$?

if [[ $GRADLE_EC -eq 0 ]]; then
    draw_progress "4/5" 100 "Biên dịch Release APK hoàn tất!"
    sleep 0.4
else
    printf "\n%b[X] [4/5] Biên dịch Gradle thất bại! (Mã lỗi: %d)%b\n" "$C_RED" "$GRADLE_EC" "$C_RESET"
    tail -n 15 "${LOG_FILE}"
    printf "%b📄 Chi tiết lỗi: %s%b\n" "$C_YELLOW" "${LOG_FILE}" "$C_RESET"
    exit $GRADLE_EC
fi

# Locate best APK
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
    printf "\n%b[-] Không tìm thấy file APK release trong %s!%b\n" "$C_RED" "${APK_DIR}" "$C_RESET"
    exit 1
fi

# =========================================================================
# STEP 5: Cài đặt APK lên TV (Visual Progress Bar)
# =========================================================================
log_msg "Starting APK Install: ${APK}"
draw_progress "5/5" 10 "Đang chuẩn bị cài đặt APK..."

# Run install in background to animate progress bar
(
    if ! adb -s "${TARGET}" install -r "${APK}" >> "${LOG_FILE}" 2>&1; then
        adb -s "${TARGET}" uninstall com.next.tv.launcher >> "${LOG_FILE}" 2>&1 || true
        adb -s "${TARGET}" install -r "${APK}" >> "${LOG_FILE}" 2>&1
    fi
) &
INSTALL_PID=$!

INSTALL_PCT=15
while kill -0 "$INSTALL_PID" 2>/dev/null; do
    if (( INSTALL_PCT < 92 )); then
        INSTALL_PCT=$(( INSTALL_PCT + 3 ))
    fi

    LABEL="Đang truyền tải & cài đặt APK..."
    if (( INSTALL_PCT >= 50 )); then
        LABEL="TV đang giải nén & cài đặt hệ thống..."
    fi

    draw_progress "5/5" "$INSTALL_PCT" "$LABEL"
    sleep 0.12
done

wait "$INSTALL_PID"
INSTALL_EC=$?

if [[ $INSTALL_EC -eq 0 ]]; then
    draw_progress "5/5" 100 "Cài đặt APK lên TV hoàn tất!"
    sleep 0.4
else
    printf "\n%b[X] [5/5] Cài đặt APK thất bại!%b\n" "$C_RED" "$C_RESET"
    tail -n 15 "${LOG_FILE}"
    printf "%b📄 Chi tiết lỗi: %s%b\n" "$C_YELLOW" "${LOG_FILE}" "$C_RESET"
    exit $INSTALL_EC
fi

# =========================================================================
# FINAL: Khởi chạy NextTV & Hiển thị kết quả
# =========================================================================
draw_status "✓" "Đang khởi chạy NextTV trên màn hình TV..." "$C_GREEN"
adb -s "${TARGET}" shell am start -n com.next.tv.launcher/.MainActivity >> "${LOG_FILE}" 2>&1 || true
sleep 0.5

# Restore cursor and finalize
printf "\033[?25h"
echo ""
echo ""
printf "%b==================================================%b\n" "$C_GREEN" "$C_RESET"
printf "%b [✓] TRIỂN KHAI NEXTTV THÀNH CÔNG!%b\n" "$C_BOLD$C_GREEN" "$C_RESET"
printf "%b     Ứng dụng đã được khởi chạy trên TV.%b\n" "$C_WHITE" "$C_RESET"
printf "%b==================================================%b\n" "$C_GREEN" "$C_RESET"
