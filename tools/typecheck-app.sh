#!/usr/bin/env bash
# Type-check the Sumire Pad app-side Kotlin (everything under .../markdownhelperkeyboard/pad/,
# the symbol_keyboard additions, the app pad tests and the instrumented test) WITHOUT Gradle.
#
# Why: the cloud session cannot reach Google's Maven repository, so the app module cannot be
# compiled there. This script compiles the pad files with kotlinc against android.jar (from
# tools/setup.sh) plus small hand-written stubs for the AndroidX / Hilt / test classes they use
# (tools/typecheck-stubs/). It catches the usual mistakes (wrong stdlib call, unresolved
# reference, type mismatch) before CI runs. It does NOT check the IMEService.kt edits, which
# reference hundreds of upstream classes; keep those small and simple.
#
# Needs: tools/setup.sh (android.jar), kotlinc (tools/test-engine.sh downloads it), junit jar.
set -euo pipefail

KOTLIN_VERSION=2.2.10
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${SUMIRE_PAD_TOOL_CACHE:-$HOME/.cache/sumire-pad}"
OUT="$REPO_ROOT/build/typecheck-app"
ANDROID_HOME="${ANDROID_HOME:-$(grep '^sdk.dir=' "$REPO_ROOT/local.properties" 2>/dev/null | cut -d= -f2-)}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-37/android.jar"
[ -f "$ANDROID_JAR" ] || { echo "android.jar not found at $ANDROID_JAR; run tools/setup.sh" >&2; exit 1; }

KOTLINC="$CACHE/kotlinc-$KOTLIN_VERSION/kotlinc/bin/kotlinc"
[ -x "$KOTLINC" ] || { echo "kotlinc missing; run tools/test-engine.sh once" >&2; exit 1; }
JUNIT_JAR="$CACHE/junit-4.13.2.jar"
[ -f "$JUNIT_JAR" ] || { echo "junit jar missing; run tools/test-engine.sh once" >&2; exit 1; }

mkdir -p "$OUT"
log() { printf '[typecheck] %s\n' "$*" >&2; }

# Generate the R class from our resources (only the ids the pad code uses).
python3 - "$REPO_ROOT" "$OUT/RStub.kt" <<'PY'
import re, sys
root, out = sys.argv[1], sys.argv[2]
names = []
for f in [f"{root}/app/src/main/res/values/pad_strings.xml"]:
    for m in re.finditer(r'<(?:string|string-array)\s+name="([^"]+)"', open(f, encoding="utf-8").read()):
        names.append(m.group(1))
strings = [n for n in names]
with open(out, "w", encoding="utf-8") as w:
    w.write("package com.kazumaproject.markdownhelperkeyboard\n\nobject R {\n")
    w.write("    object string {\n")
    for i, n in enumerate(sorted(set(strings))):
        w.write(f"        const val {n}: Int = {0x7f100000 + i}\n")
    w.write("    }\n    object array {\n")
    for i, n in enumerate(sorted(set(strings))):
        w.write(f"        const val {n}: Int = {0x7f110000 + i}\n")
    w.write("    }\n    object xml {\n        const val pref_pad: Int = 0x7f120000\n    }\n")
    w.write("    object id {\n        const val padDiagnosticsFragment: Int = 0x7f130000\n        const val padPreferenceFragment: Int = 0x7f130001\n    }\n}\n")
PY

CORE_SRC=(
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/key/Key.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/key/KeyInfo.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/key/KeyMap.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/state/GestureType.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/domain/state/InputMode.kt"
  "$REPO_ROOT/core/src/main/java/com/kazumaproject/core/data/gojuon/GojuonCapsLockState.kt"
)
TENKEY_SRC=("$REPO_ROOT/tenkey/src/main/java/com/kazumaproject/tenkey/extensions/CharExtension.kt")

log "compiling pad engine + app pad code + tests against android.jar"
"$KOTLINC" -nowarn -jvm-target 17 \
  -cp "$ANDROID_JAR:$JUNIT_JAR" \
  -d "$OUT/classes" \
  "${CORE_SRC[@]}" "${TENKEY_SRC[@]}" \
  $(find "$REPO_ROOT/pad_input/src/main" -name '*.kt') \
  "$REPO_ROOT/symbol_keyboard/src/main/java/com/kazumaproject/symbol_keyboard/PadSelectionDecoration.kt" \
  $(find "$REPO_ROOT/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/pad" -name '*.kt') \
  $(find "$REPO_ROOT/app/src/test/java/com/kazumaproject/markdownhelperkeyboard/pad" -name '*.kt') \
  $(find "$REPO_ROOT/app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/pad" -name '*.kt') \
  $(find "$REPO_ROOT/tools/typecheck-stubs" -name "*.kt") \
  "$OUT/RStub.kt"
log "OK: pad app code type-checks"
