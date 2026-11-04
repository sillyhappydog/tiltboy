#!/usr/bin/env bash
# Build the signed TiltBoy APK (liteFdroid release, the Sumire Pad code base) and copy it to apk/ with its SHA-256.
#
# Needs Google's Maven repository (https://dl.google.com/dl/android/maven2/), which the
# Claude cloud session cannot reach. Run this on a machine with normal network access, or let
# GitHub Actions run it (.github/workflows/sumire-pad.yml does exactly this and commits the
# APK back to the branch). tools/setup.sh prepares the SDK when sdkmanager is not available.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

VERSION_TAG="${1:-v6}"
OUT_DIR="$REPO_ROOT/apk"
# v1-v3 were apk/sumire-pad-vN.apk; from v4 (amendment 15) the app is TiltBoy: apk/tiltboy-vN.apk.
APK_BASENAME="${APK_BASENAME:-tiltboy}"
OUT_APK="$OUT_DIR/$APK_BASENAME-$VERSION_TAG.apk"

log() { printf '[build] %s\n' "$*" >&2; }

if [ -z "${ANDROID_HOME:-}" ] && ! grep -q '^sdk.dir=' local.properties 2>/dev/null; then
  log "no Android SDK configured; running tools/setup.sh"
  tools/setup.sh
fi

INIT_ARGS=(-I tools/sumire-pad-init.gradle)
# Only redirect google() when dl.google.com is blocked (see tools/sumire-pad-init.gradle).
if ! curl -fsSI --max-time 15 https://dl.google.com/dl/android/maven2/master-index.xml >/dev/null 2>&1; then
  export SUMIRE_PAD_REDIRECT_GOOGLE=1
fi

log "assembleLiteFdroidRelease"
./gradlew "${INIT_ARGS[@]}" :app:assembleLiteFdroidRelease --no-daemon --stacktrace --max-workers=2 ${GRADLE_EXTRA_ARGS:-}

SRC="app/build/outputs/apk/liteFdroid/release/app-liteFdroid-release.apk"
[ -f "$SRC" ] || SRC="$(ls app/build/outputs/apk/liteFdroid/release/*.apk | head -1)"
mkdir -p "$OUT_DIR"
cp "$SRC" "$OUT_APK"
(cd "$OUT_DIR" && sha256sum "$(basename "$OUT_APK")" > "$(basename "$OUT_APK").sha256")

SIZE=$(stat -c %s "$OUT_APK")
log "APK: $OUT_APK ($((SIZE / 1000000)) MB)"
cat "$OUT_APK.sha256" >&2
if [ "$SIZE" -ge 100000000 ]; then log "ERROR: APK is 100 MB or more; it cannot be committed"; exit 1; fi

# Show the signing certificate so it can be compared with keystore/README.md.
APKSIGNER=$(ls "${ANDROID_HOME:-$HOME/android-sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
if [ -n "$APKSIGNER" ]; then "$APKSIGNER" verify --print-certs "$OUT_APK" | grep -i "SHA-256" >&2 || true; fi
