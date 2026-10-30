#!/usr/bin/env bash
# NextTV - 1-Click Build & Deploy Script for Linux & macOS
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "${SCRIPT_DIR}/tools/sh/build_and_deploy.sh" "$@"
