#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "${HOME}/.sdkman/bin/sdkman-init.sh" ]]; then
  # Use the repo-pinned Java when sdkman is available.
  # shellcheck disable=SC1091
  set +u
  source "${HOME}/.sdkman/bin/sdkman-init.sh"
  if command -v sdk >/dev/null 2>&1; then
    sdk env install >/dev/null
    sdk env >/dev/null
  fi
  set -u
fi

adb_bin="${ADB_BIN:-}"
if [[ -z "${adb_bin}" ]]; then
  if command -v adb >/dev/null 2>&1; then
    adb_bin="$(command -v adb)"
  elif [[ -x "${HOME}/Library/Android/sdk/platform-tools/adb" ]]; then
    adb_bin="${HOME}/Library/Android/sdk/platform-tools/adb"
  else
    echo "adb not found. Set ADB_BIN or install Android platform-tools." >&2
    exit 1
  fi
fi

device_serial="${ANDROID_SERIAL:-}"
if [[ -z "${device_serial}" ]]; then
  device_serial="$("${adb_bin}" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi

if [[ -z "${device_serial}" ]]; then
  echo "No connected Android device or emulator found." >&2
  exit 1
fi

echo "Running instrumentation smoke test on ${device_serial}"
cd "${repo_dir}"
./gradlew --no-configuration-cache \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.sagberg.tournarrat.SmokeTest
