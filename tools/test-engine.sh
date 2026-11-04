#!/usr/bin/env bash
# Run the pad_input engine unit tests WITHOUT Gradle or the Android Gradle Plugin.
#
# Why: the cloud session cannot reach Google's Maven repository (see docs/notes/tools.md),
# so Gradle cannot even configure the project there. The engine and the kana tables it
# uses (:core key classes) are plain Kotlin, so kotlinc + JUnit 4 is enough to run every
# engine test. tools/test.sh runs the full Gradle test suite where Gradle works.
#
# Needs: a JDK (17+), curl, unzip. Downloads kotlinc 2.2.10 (GitHub) and JUnit (Maven Central)
# into ~/.cache/sumire-pad on first use.
set -euo pipefail

KOTLIN_VERSION=2.2.10
JUNIT_VERSION=4.13.2
HAMCREST_VERSION=1.3

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${SUMIRE_PAD_TOOL_CACHE:-$HOME/.cache/sumire-pad}"
OUT="${1:-$REPO_ROOT/build/engine-tests}"
mkdir -p "$CACHE" "$OUT"

log() { printf '[test-engine] %s\n' "$*" >&2; }

KOTLINC_DIR="$CACHE/kotlinc-$KOTLIN_VERSION"
if [ ! -x "$KOTLINC_DIR/kotlinc/bin/kotlinc" ]; then
  ZIP="$CACHE/kotlin-compiler-$KOTLIN_VERSION.zip"
  if [ ! -f "$ZIP" ]; then
    log "downloading kotlin-compiler-$KOTLIN_VERSION.zip"
    curl -fsSL -o "$ZIP" "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VERSION/kotlin-compiler-$KOTLIN_VERSION.zip"
  fi
  mkdir -p "$KOTLINC_DIR" && unzip -q -o "$ZIP" -d "$KOTLINC_DIR"
fi
KOTLINC="$KOTLINC_DIR/kotlinc/bin/kotlinc"

fetch() { # group/artifact version
  local path="$1" ver="$2" name
  name="$(basename "$path")-$ver.jar"
  if [ ! -f "$CACHE/$name" ]; then
    log "downloading $name"
    curl -fsSL --retry 6 --retry-delay 5 --retry-all-errors -o "$CACHE/$name" "https://repo1.maven.org/maven2/$path/$ver/$name"
  fi
  printf '%s' "$CACHE/$name"
}
JUNIT_JAR=$(fetch junit/junit "$JUNIT_VERSION")
HAMCREST_JAR=$(fetch org/hamcrest/hamcrest-core "$HAMCREST_VERSION")

# Sources: the kana tables from :core (plain Kotlin), the engine, and its tests.
CORE_SRC=(
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/key/Key.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/key/KeyInfo.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/key/KeyMap.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/state/GestureType.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/state/InputMode.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/data/gojuon/GojuonCapsLockState.kt"
  "$REPO_ROOT/tenkey/src/main/java/com/kazumaproject/tenkey/extensions/CharExtension.kt"
)

log "compiling engine + tests"
"$KOTLINC" -nowarn -jvm-target 17 -cp "$JUNIT_JAR:$HAMCREST_JAR" \
  -d "$OUT/classes" \
  "${CORE_SRC[@]}" \
  $(find "$REPO_ROOT/pad_input/src/main" "$REPO_ROOT/pad_input/src/test" -name '*.kt')

TESTS=$(cd "$REPO_ROOT/pad_input/src/test/java" && find . -name '*Test.kt' | sed 's#^\./##; s#\.kt$##; s#/#.#g' | sort)
log "running: $(echo $TESTS | tr '\n' ' ')"
set +e
java -cp "$OUT/classes:$KOTLINC_DIR/kotlinc/lib/kotlin-stdlib.jar:$JUNIT_JAR:$HAMCREST_JAR" \
  org.junit.runner.JUnitCore $TESTS | tee "$OUT/engine-tests.log"
STATUS=${PIPESTATUS[0]}
set -e
log "result written to $OUT/engine-tests.log"
exit "$STATUS"
