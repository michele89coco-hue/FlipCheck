#!/usr/bin/env bash
set -euo pipefail

apk=${1:?APK path required}
report=${2:-apk-analyzer-report.txt}
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$sdk" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME is required" >&2
  exit 2
fi
build_tools=${BUILD_TOOLS_VERSION:-36.0.0}
aapt2="$sdk/build-tools/$build_tools/aapt2"
dexdump="$sdk/build-tools/$build_tools/dexdump"
apksigner="$sdk/build-tools/$build_tools/apksigner"
apkanalyzer="$sdk/cmdline-tools/latest/bin/apkanalyzer"

tmp_dir=$(mktemp -d)
trap 'find "$tmp_dir" -type f -delete 2>/dev/null || true; rmdir "$tmp_dir" 2>/dev/null || true' EXIT
unzip -q "$apk" 'classes*.dex' -d "$tmp_dir"

{
  echo "APK=$(realpath "$apk")"
  echo "SHA256=$(sha256sum "$apk" | awk '{print $1}')"
  echo "OFFICIAL_TOOLS=aapt2,dexdump,apksigner"
  echo
  echo "[manifest]"
  if [[ -x "$apkanalyzer" ]]; then
    "$apkanalyzer" manifest print "$apk"
  else
    "$aapt2" dump badging "$apk"
  fi
  echo
  echo "[files]"
  if [[ -x "$apkanalyzer" ]]; then
    "$apkanalyzer" files list "$apk"
  else
    unzip -Z1 "$apk"
  fi
  if [[ -x "$apkanalyzer" ]]; then
    echo
    echo "[dex packages]"
    "$apkanalyzer" dex packages "$apk"
  fi
  echo
  echo "[required dex classes/packages]"
  for dex in "$tmp_dir"/classes*.dex; do
    "$dexdump" -f "$dex"
    "$dexdump" "$dex"
  done
} > "$report.full"

required=(
  'com/flipcheck/nativebeta/MainActivity'
  'com/flipcheck/nativebeta/AnalysisForegroundService'
  'com/flipcheck/nativebeta/PhotographicFactNormalizer'
  'com/flipcheck/nativebeta/LocalEvidenceBootstrap'
  'com/flipcheck/nativebeta/NumberConflictResolver'
  'com/flipcheck/nativebeta/PostEnrichmentConsistencyChecker'
  'com/google/mlkit'
  'com/google/android/gms/tasks'
  'org/jsoup'
)
{
  echo "APK analyzer verification"
  echo "SHA256=$(sha256sum "$apk" | awk '{print $1}')"
  echo "DEX_FILES=$(unzip -Z1 "$apk" | awk '/^classes([0-9]+)?\.dex$/' | paste -sd, -)"
  for needle in "${required[@]}"; do
    if rg -q -F "$needle" "$report.full"; then
      echo "PASS required=$needle"
    else
      echo "FAIL missing=$needle"
      exit 1
    fi
  done
  if find "$tmp_dir" -name '*.dex' -print0 | xargs -0 strings | rg -q 'setBackground\(Ljava/lang/Object;\)'; then
    echo "FAIL invalid Android method signature setBackground(Object)"
    exit 1
  fi
  echo "PASS invalid_stub_signatures_absent"
  echo "PASS manifest_files_dex_checked_with_official_android_tools"
} > "$report"

"$apksigner" verify --verbose --print-certs "$apk" > "${report%/*}/apksigner-report.txt" 2>&1
