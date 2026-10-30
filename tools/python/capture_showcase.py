#!/usr/bin/env python3
"""
Automated Showcase Screenshot Capture for NextTV.
Captures clean, production-ready screenshots of NextTV drawers and screens without onboarding overlay.
Converts and saves screenshots as optimized WebP to docs/screenshots/.
Also updates app/src/main/assets/onboarding/home_preview.webp.
"""

import os
import subprocess
import time
from PIL import Image

import sys

raw_ip = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TV_IP", "127.0.0.1")
DEVICE_IP = raw_ip if ":" in raw_ip else f"{raw_ip}:5555"
PKG = "com.next.tv.launcher"
MAIN_ACT = f"{PKG}/.MainActivity"
OUT_DIR = "docs/screenshots"
ASSET_PREVIEW = "app/src/main/assets/onboarding/home_preview.webp"

def run_adb(cmd, wait=0.5):
    full_cmd = f"adb -s {DEVICE_IP} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    if wait > 0:
        time.sleep(wait)
    return res

def keyevent(code, wait=0.3):
    run_adb(f"shell input keyevent {code}", wait=wait)

def capture_screen(dest_webp, quality=82):
    tmp_remote = "/sdcard/screen_tmp.png"
    tmp_local = ".agents/scratch/screen_tmp.png"
    run_adb(f"shell screencap -p {tmp_remote}", wait=0.2)
    run_adb(f"pull {tmp_remote} {tmp_local}", wait=0.2)
    
    if os.path.exists(tmp_local):
        os.makedirs(os.path.dirname(dest_webp), exist_ok=True)
        with Image.open(tmp_local) as img:
            img.save(dest_webp, "WEBP", quality=quality, method=6)
        size_kb = os.path.getsize(dest_webp) / 1024
        print(f"Saved {dest_webp} ({size_kb:.1f} KB)")
        return True
    else:
        print(f"Error capturing {dest_webp}")
        return False

def main():
    print(f"[*] Connecting to {DEVICE_IP}...")
    run_adb(f"connect {DEVICE_IP}")
    time.sleep(1)

    print("[*] Launching NextTV...")
    run_adb(f"shell am start -n {MAIN_ACT}")
    time.sleep(2)

    os.makedirs(OUT_DIR, exist_ok=True)

    print("[1/7] Capturing Home Screen...")
    capture_screen(os.path.join(OUT_DIR, "01_home_screen.webp"), quality=85)
    capture_screen(ASSET_PREVIEW, quality=75)

    print("[2/7] Opening Memory Cleaner...")
    keyevent(19)
    keyevent(21)
    keyevent(23)
    time.sleep(1)
    capture_screen(os.path.join(OUT_DIR, "02_memory_cleaner_donut.webp"))

    keyevent(20)
    time.sleep(0.5)
    capture_screen(os.path.join(OUT_DIR, "02b_protected_apps_grid.webp"))
    keyevent(4)

    print("[3/7] Opening Advanced Settings...")
    keyevent(19)
    keyevent(22)
    keyevent(23)
    time.sleep(1)
    capture_screen(os.path.join(OUT_DIR, "03_advanced_settings.webp"))
    keyevent(4)

    print("[4/7] Opening Kid Mode Drawer...")
    keyevent(19)
    keyevent(21)
    keyevent(21)
    keyevent(23)
    time.sleep(1)
    capture_screen(os.path.join(OUT_DIR, "04_kid_mode.webp"))
    keyevent(4)

    print("[5/7] Opening Remote File Transfer...")
    keyevent(19)
    keyevent(22)
    keyevent(22)
    keyevent(23)
    time.sleep(1)
    capture_screen(os.path.join(OUT_DIR, "05_remote_file_transfer.webp"))
    keyevent(4)

    print("[6/7] Opening Global Search Spotlight...")
    keyevent(84)
    time.sleep(1)
    capture_screen(os.path.join(OUT_DIR, "06_global_search_spotlight.webp"))
    keyevent(4)

    print("[+] All showcase captures completed successfully!")

if __name__ == '__main__':
    main()
