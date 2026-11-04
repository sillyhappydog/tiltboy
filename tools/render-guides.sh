#!/usr/bin/env bash
# Render the on-screen guides headless (Java2D, same GuideLayout primitives as the Android view)
# at the RG477V's sizes and copy the PNGs into docs/cloud-log/assets/ for the report.
# Needs the Gradle build to work (see docs/notes/tools.md) and Japanese + emoji fonts on the
# machine (IPAGothic / Noto Color Emoji are installed in the cloud image).
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"
OUT="$REPO_ROOT/build/guide-renders"
rm -rf "$OUT"; mkdir -p "$OUT"
./gradlew -I tools/sumire-pad-init.gradle :app:testLiteFdroidDebugUnitTest \
  --tests 'com.kazumaproject.markdownhelperkeyboard.pad.PadGuideRenderTest' \
  --no-daemon --max-workers=2 --rerun-tasks -Dpad.guide.render.dir="$OUT" >/dev/null
# Gradle test workers do not inherit -D flags: the test also honours the default output dir.
if [ -z "$(ls -A "$OUT" 2>/dev/null)" ] && [ -d app/build/guide-renders ]; then cp app/build/guide-renders/*.png "$OUT"/; fi
mkdir -p docs/cloud-log/assets
cp "$OUT"/guide_*.png docs/cloud-log/assets/
ls -1 docs/cloud-log/assets/guide_*.png
