#!/usr/bin/env bash
# Run every automated Sumire Pad test, headless and deterministic. One command.
#
#   1. Engine tests without Gradle (kotlinc + JUnit): tools/test-engine.sh. Works in the
#      cloud session.
#   1b. Type check of the app-side pad code without Gradle: tools/typecheck-app.sh.
#   2. Gradle unit tests (needs Google Maven access): the pad_input module tests again under
#      Gradle, plus the app-level pad tests (Robolectric service-level test, converter test,
#      pass-through test).
#   3. Emulator test: only when /dev/kvm exists (never in the cloud session).
#
# Exit status is non-zero if any stage that ran failed. A summary is printed at the end.
set -uo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

RESULTS=()
record() { RESULTS+=("$1|$2"); }

echo "== 1/4 engine tests (kotlinc + JUnit)"
if tools/test-engine.sh build/engine-tests; then record "engine (kotlinc+JUnit)" PASS; else record "engine (kotlinc+JUnit)" FAIL; fi

echo "== 2/4 type check of the app-side pad code (kotlinc + android.jar + stubs)"
if tools/typecheck-app.sh; then record "app pad code type check" PASS; else record "app pad code type check" FAIL; fi

echo "== 3/4 gradle unit tests"
INIT_ARGS=(-I tools/sumire-pad-init.gradle)
# Only redirect google() when dl.google.com is blocked (see tools/sumire-pad-init.gradle).
if ! curl -fsSI --max-time 15 https://dl.google.com/dl/android/maven2/master-index.xml >/dev/null 2>&1; then
  export SUMIRE_PAD_REDIRECT_GOOGLE=1
fi
if [ "${SKIP_GRADLE:-0}" = "1" ]; then
  record "gradle unit tests" SKIPPED
elif ./gradlew "${INIT_ARGS[@]}" --no-daemon \
      :pad_input:test \
      :app:testLiteFdroidDebugUnitTest --tests 'com.kazumaproject.markdownhelperkeyboard.pad.*' ; then
  record "gradle unit tests (pad_input + app pad.*)" PASS
else
  record "gradle unit tests (pad_input + app pad.*)" FAIL
fi

echo "== 4/4 emulator"
if [ -e /dev/kvm ] && [ "${SKIP_EMULATOR:-0}" != "1" ]; then
  if ./gradlew "${INIT_ARGS[@]}" --no-daemon :app:connectedLiteFdroidDebugAndroidTest \
       -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.pad.PadTypingInstrumentedTest; then
    record "emulator typing test" PASS
  else
    record "emulator typing test" FAIL
  fi
else
  record "emulator typing test" "SKIPPED (no /dev/kvm)"
fi

echo
echo "== summary"
STATUS=0
for r in "${RESULTS[@]}"; do
  name="${r%%|*}"; res="${r##*|}"
  printf '  %-45s %s\n' "$name" "$res"
  case "$res" in FAIL*) STATUS=1;; esac
done
exit $STATUS
