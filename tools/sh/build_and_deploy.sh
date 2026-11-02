#!/usr/bin/env bash
# NextTV - Build Release APK and Deploy to Android TV (Linux & macOS)
# Features auto-discovery, interactive device selection, authorization loop & ABI-matching install.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${TOOLS_DIR}/.." && pwd)"
CACHE_FILE="${REPO_ROOT}/.last_tv_ip"
cd "${REPO_ROOT}"

# Print TrueColor ANSI banner if available
if [[ -f "${TOOLS_DIR}/banner.ans" ]]; then
    cat "${TOOLS_DIR}/banner.ans"
    echo ""
fi

# 1. Ensure ADB platform-tools is available
source "${SCRIPT_DIR}/ensure_adb.sh"

# 2. Check local.properties & JDK environment
if [[ ! -f "${REPO_ROOT}/local.properties" ]]; then
    SDK_CANDIDATES=(
        "${ANDROID_HOME:-}"
        "${ANDROID_SDK_ROOT:-}"
        "${HOME}/Library/Android/sdk"
        "${HOME}/Android/Sdk"
    )
    for cand in "${SDK_CANDIDATES[@]}"; do
        if [[ -n "${cand}" && -d "${cand}" ]]; then
            echo "sdk.dir=${cand}" > "${REPO_ROOT}/local.properties"
            echo "[+] Created local.properties pointing to: ${cand}"
            break
        fi
    done
fi

if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
    JDK_CANDIDATES=(
        "${HOME}/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home"
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
        "/Applications/Android Studio.app/Contents/jbr"
        "/opt/android-studio/jbr"
        "/usr/lib/jvm/java-17-openjdk"
    )
    for jcand in "${JDK_CANDIDATES[@]}"; do
        if [[ -x "${jcand}/bin/java" ]]; then
            export JAVA_HOME="${jcand}"
            export PATH="${jcand}/bin:${PATH}"
            echo "[+] JAVA_HOME set to: ${jcand}"
            break
        fi
    done
fi

# 3. Discover and Select Target Device
TV_HOST="${1:-${TV_IP:-}}"

if [[ -z "$TV_HOST" ]]; then
    echo ">>> Đang tìm kiếm Android TV trên mạng LAN..."
    bash "${SCRIPT_DIR}/find_tv_ip.sh"
    if [[ -f "${CACHE_FILE}" ]]; then
        TV_HOST=$(head -n 1 "${CACHE_FILE}" | tr -d '[:space:]')
    fi
fi

if [[ -z "$TV_HOST" ]]; then
    echo "[-] Chưa xác định được IP hoặc thiết bị Android TV. Hủy bỏ."
    exit 1
fi

TARGET="${TV_HOST}"
if [[ "$TARGET" != *:* ]]; then
    TARGET="${TARGET}:5555"
fi

# 4. Connect ADB and Handle Authorization / Offline Loop
echo ">>> Đang kết nối ADB tới ${TARGET}..."
adb connect "${TARGET}" >/dev/null 2>&1 || true
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
        echo "[+] Thiết bị ${TARGET} đã sẵn sàng và được cấp quyền (Authorized)!"
        break
    elif [[ "$DEV_STATE" == "unauthorized" ]]; then
        if [[ "$SHOWN_UNAUTH_BANNER" == false ]]; then
            echo ""
            echo "=========================================================================="
            echo " [!] MÀN HÌNH TV ĐANG CHỜ XÁC THỰC KẾT NỐI (ALLOW USB DEBUGGING)"
            echo "=========================================================================="
            echo " 👉 Hãy cầm remote TV, tích chọn 'Luôn cho phép từ máy tính này'"
            echo "    rồi bấm [Cho phép / OK / Allow]."
            echo " ⏳ Đang chờ xác nhận... (Tự kiểm tra sau mỗi 2s, hoặc bấm [Enter]/[Space]"
            echo "    để kiểm tra ngay lập tức, Ctrl+C để hủy)"
            echo "=========================================================================="
            SHOWN_UNAUTH_BANNER=true
        fi

        # Non-blocking read with 2-second timeout: Enter or Space triggers immediate re-check
        if read -t 2 -n 1 -r _key; then
            printf " [Đang kiểm tra lại...]"
        else
            printf "."
        fi
        WAITED=$((WAITED + 2))

        if (( WAITED >= MAX_WAIT )); then
            echo ""
            echo "[!] Đã chờ quá ${MAX_WAIT} giây mà TV chưa xác nhận."
            read -r -p "👉 Bạn có muốn tiếp tục chờ không? (Y/n): " KEEP_WAITING
            KEEP_WAITING="${KEEP_WAITING:-y}"
            if [[ "$KEEP_WAITING" =~ ^[Yy] ]]; then
                WAITED=0
            else
                echo "[-] Hủy bỏ tiến trình do TV chưa cấp quyền kết nối ADB."
                exit 1
            fi
        fi
    elif [[ "$DEV_STATE" == "offline" ]]; then
        echo ""
        echo "[*] Thiết bị đang offline. Đang khởi động lại kết nối..."
        adb disconnect "${TARGET}" >/dev/null 2>&1 || true
        sleep 1
        adb connect "${TARGET}" >/dev/null 2>&1 || true
        sleep 2
    else
        adb connect "${TARGET}" >/dev/null 2>&1 || true
        sleep 1
        WAITED=$((WAITED + 1))
        if (( WAITED >= 15 && SHOWN_UNAUTH_BANNER == false )); then
            echo ""
            echo "[-] Không thể kết nối tới ${TARGET}. Vui lòng kiểm tra lại mạng Wi-Fi và TV."
            exit 1
        fi
    fi
done

# Query device information & CPU ABI
DEVICE_MODEL=$(adb -s "${TARGET}" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n' || true)
DEVICE_MFTR=$(adb -s "${TARGET}" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r\n' || true)
DEVICE_ABI=$(adb -s "${TARGET}" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r\n' || true)
echo ">>> Thiết bị mục tiêu: ${DEVICE_MFTR} ${DEVICE_MODEL} (Kiến trúc CPU: ${DEVICE_ABI:-unknown})"

# 5. Generate Native Secrets Header & Build APK
echo ">>> [1/4] Generating Native Secrets Header..."
bash "${SCRIPT_DIR}/generate_header.sh"

echo ">>> [2/4] Building Sideload Release APK with Gradle..."
./gradlew assembleSideloadRelease --console=plain

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
    echo "[-] Không tìm thấy file APK release trong ${APK_DIR}!"
    exit 1
fi
echo ">>> Đã chọn APK tối ưu: $(basename "${APK}")"

# 6. Install APK on TV Box
echo ">>> [3/4] Đang cài đặt NextTV lên TV..."
if ! adb -s "${TARGET}" install -r "${APK}"; then
    echo "[!] Cài đặt gặp xung đột chữ ký hoặc phiên bản cũ. Đang tự động gỡ bỏ và cài lại..."
    adb -s "${TARGET}" uninstall com.next.tv.launcher >/dev/null 2>&1 || true
    adb -s "${TARGET}" install -r "${APK}"
fi

# 7. Launch NextTV
echo ">>> [4/4] Khởi chạy NextTV trên TV..."
adb -s "${TARGET}" shell am start -n com.next.tv.launcher/.MainActivity
echo "[+] Hoàn tất! NextTV đã sẵn sàng trên TV."
