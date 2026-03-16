#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${repo_dir}/.env"

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
  device_serial="$("${adb_bin}" devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')"
fi

if [[ -z "${device_serial}" ]]; then
  if "${adb_bin}" devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit found ? 0 : 1 }'; then
    echo "No emulator found. Start an emulator or set ANDROID_SERIAL explicitly to run smoke tests on a specific device." >&2
  else
    echo "No connected Android device or emulator found." >&2
  fi
  exit 1
fi

export ANDROID_SERIAL="${device_serial}"

echo "Running instrumentation smoke test on ${device_serial}"
cd "${repo_dir}"
./gradlew --no-configuration-cache \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.sagberg.tournarrat.SmokeTest

openai_api_key=""
if [[ -f "${env_file}" ]]; then
  openai_api_key="$(
    python3 - "${env_file}" <<'PY'
import sys
from pathlib import Path

env_path = Path(sys.argv[1])
for line in env_path.read_text().splitlines():
    stripped = line.strip()
    if not stripped or stripped.startswith("#") or "=" not in stripped:
        continue
    key, value = stripped.split("=", 1)
    if key.strip() == "OPENAI_API_KEY":
        print(value.strip().strip("'\""))
        break
PY
  )"
fi

if [[ -n "${openai_api_key}" ]]; then
  echo "Running live OpenAI smoke test on ${device_serial}"
  ./gradlew --no-configuration-cache \
    :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=net.sagberg.tournarrat.LiveOpenAiSmokeTest \
    "-Pandroid.testInstrumentationRunnerArguments.openAiApiKey=${openai_api_key}"
else
  echo "Skipping live OpenAI smoke test because OPENAI_API_KEY is not set in .env"
fi
