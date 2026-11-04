#!/usr/bin/env bash
# Sumire Pad build-environment setup.
#
# Problem: the build network allows Docker Hub, Maven Central, maven.google.com and
# GitHub, but not dl.google.com, where sdkmanager downloads the Android SDK from.
# There is also no Docker daemon in the cloud session (only the client).
#
# Solution: pull the Android SDK out of the Docker Hub image `cimg/android`
# (CircleCI's Android image) with the plain registry HTTP API, no daemon needed.
# Only the layers that contain the SDK are downloaded (about 0.9 GB), then the
# directory /home/circleci/android-sdk is extracted into $ANDROID_HOME.
#
# Versions are pinned in docs/notes/tools.md. Change them there and here together.
#
# Usage:  tools/setup.sh            # installs SDK into ~/android-sdk, writes local.properties
#         ANDROID_HOME=/opt/sdk tools/setup.sh
set -euo pipefail

IMAGE="cimg/android"
TAG="2026.10.1"
# Manifest digest of cimg/android:2026.10.1 (linux/amd64) as seen on 2026-10-31.
# If the tag is re-pushed the digest check fails loudly instead of silently changing the SDK.
EXPECTED_MANIFEST_SHA256="${EXPECTED_MANIFEST_SHA256:-}"
SDK_PATH_IN_IMAGE="home/circleci/android-sdk"

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CACHE_DIR="${SUMIRE_PAD_LAYER_CACHE:-$HOME/.cache/sumire-pad/docker-layers}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

log() { printf '[setup] %s\n' "$*" >&2; }

need() { command -v "$1" >/dev/null 2>&1 || { log "missing tool: $1"; exit 1; }; }
need curl; need python3; need tar

mkdir -p "$ANDROID_HOME" "$CACHE_DIR"

if [ -x "$ANDROID_HOME/build-tools/37.0.0/aidl" ] && [ -f "$ANDROID_HOME/platforms/android-37/android.jar" ]; then
  log "Android SDK already present at $ANDROID_HOME"
else
  log "fetching Docker Hub token for $IMAGE"
  TOKEN=$(curl -fsS "https://auth.docker.io/token?service=registry.docker.io&scope=repository:${IMAGE}:pull" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
  AUTH=(-H "Authorization: Bearer $TOKEN")
  REG="https://registry-1.docker.io/v2/${IMAGE}"

  log "fetching manifest $IMAGE:$TAG"
  MANIFEST="$CACHE_DIR/manifest-$TAG.json"
  curl -fsS "${AUTH[@]}" \
    -H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
    "$REG/manifests/$TAG" -o "$MANIFEST"
  ACTUAL_SHA=$(sha256sum "$MANIFEST" | cut -d' ' -f1)
  log "manifest sha256: $ACTUAL_SHA"
  if [ -n "$EXPECTED_MANIFEST_SHA256" ] && [ "$ACTUAL_SHA" != "$EXPECTED_MANIFEST_SHA256" ]; then
    log "manifest digest changed (expected $EXPECTED_MANIFEST_SHA256). Refusing to continue."
    exit 1
  fi

  CONFIG_DIGEST=$(python3 -c 'import sys,json;print(json.load(open(sys.argv[1]))["config"]["digest"])' "$MANIFEST")
  CONFIG="$CACHE_DIR/config-$TAG.json"
  curl -fsSL "${AUTH[@]}" "$REG/blobs/$CONFIG_DIGEST" -o "$CONFIG"

  # Pick the layers whose Dockerfile step installed SDK pieces (cmdline-tools, sdkmanager).
  # The image config "history" lists one entry per layer (skipping empty_layer entries),
  # in the same order as manifest.layers.
  LAYERS=$(python3 - "$MANIFEST" "$CONFIG" <<'PY'
import sys, json
manifest = json.load(open(sys.argv[1])); config = json.load(open(sys.argv[2]))
history = [h for h in config["history"] if not h.get("empty_layer")]
layers = manifest["layers"]
assert len(history) == len(layers), (len(history), len(layers))
for h, l in zip(history, layers):
    cmd = h.get("created_by", "")
    if "sdkmanager" in cmd or "SDK_TOOLS_URL" in cmd:
        # skip the "extras;...;m2repository / fastlane / firebase" layer: not needed, 700 MB
        if "extras;" in cmd:
            continue
        print(l["digest"], l["size"])
PY
)
  if [ -z "$LAYERS" ]; then log "could not identify SDK layers in image history"; exit 1; fi

  while read -r DIGEST SIZE; do
    BLOB="$CACHE_DIR/${DIGEST#sha256:}.tar.gz"
    if [ -f "$BLOB" ] && [ "$(stat -c %s "$BLOB")" = "$SIZE" ]; then
      log "layer ${DIGEST:7:12} cached"
    else
      log "downloading layer ${DIGEST:7:12} ($((SIZE / 1000000)) MB)"
      curl -fSL --retry 5 --retry-delay 3 -C - "${AUTH[@]}" "$REG/blobs/$DIGEST" -o "$BLOB" --progress-bar
    fi
    GOT=$(sha256sum "$BLOB" | cut -d' ' -f1)
    if [ "sha256:$GOT" != "$DIGEST" ]; then log "checksum mismatch for $DIGEST"; rm -f "$BLOB"; exit 1; fi
    log "extracting $SDK_PATH_IN_IMAGE from ${DIGEST:7:12}"
    tar -xzf "$BLOB" -C "$ANDROID_HOME" --strip-components=3 --no-same-owner \
      --wildcards "$SDK_PATH_IN_IMAGE/*" 2>/dev/null || true
  done <<<"$LAYERS"

  # The image installs "platforms;android-37.0". AGP looks for platforms/android-37.
  if [ ! -e "$ANDROID_HOME/platforms/android-37" ] && [ -d "$ANDROID_HOME/platforms/android-37.0" ]; then
    ln -s android-37.0 "$ANDROID_HOME/platforms/android-37"
  fi
  mkdir -p "$ANDROID_HOME/licenses"
  # sdkmanager already recorded accepted licenses inside the image; keep them.
fi

log "SDK contents:"; ls "$ANDROID_HOME" "$ANDROID_HOME/platforms" "$ANDROID_HOME/build-tools" >&2

LOCAL_PROPS="$REPO_ROOT/local.properties"
if ! grep -q '^sdk.dir=' "$LOCAL_PROPS" 2>/dev/null; then
  echo "sdk.dir=$ANDROID_HOME" >>"$LOCAL_PROPS"
  log "wrote sdk.dir to local.properties"
fi

# Gradle init script: Maven Central mirror first (the proxy rate-limits Central with HTTP 429)
# and, only when dl.google.com is blocked, a google() redirect (see tools/sumire-pad-init.gradle).
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
mkdir -p "$GRADLE_USER_HOME/init.d"
cp "$REPO_ROOT/tools/sumire-pad-init.gradle" "$GRADLE_USER_HOME/init.d/sumire-pad-init.gradle"
log "installed $GRADLE_USER_HOME/init.d/sumire-pad-init.gradle"
if curl -fsSI --max-time 15 https://dl.google.com/dl/android/maven2/master-index.xml >/dev/null 2>&1; then
  log "dl.google.com reachable: Gradle uses google() directly"
else
  log "dl.google.com NOT reachable: tools/build.sh and tools/test.sh will set SUMIRE_PAD_REDIRECT_GOOGLE=1"
fi
# Retry transient repository errors a few more times than Gradle's default.
GRADLE_PROPS="$GRADLE_USER_HOME/gradle.properties"
touch "$GRADLE_PROPS"
grep -q '^systemProp.org.gradle.internal.repository.max.retries=' "$GRADLE_PROPS" || \
  printf '%s\n' 'systemProp.org.gradle.internal.repository.max.retries=8' 'systemProp.org.gradle.internal.repository.initial.backoff=2000' >>"$GRADLE_PROPS"

# JDK: the Gradle daemon needs JDK 21+ (gradle/gradle-daemon-jvm.properties).
if ! command -v javac >/dev/null 2>&1; then
  log "WARNING: no JDK on PATH. Install OpenJDK 21 (apt-get install openjdk-21-jdk) before building."
fi

log "done. Build with: tools/build.sh"
