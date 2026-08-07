#!/usr/bin/env bash
# Put the compiled dictionaries where Gradle packages them.
#
# Build them first: `stfst compile recipe.toml` and `scripts/build-morph.sh`. The
# morph artifacts are optional, and without them a suffix join is a plain join.
set -euo pipefail
cd "$(dirname "$0")/.."

out=android/app/src/main/assets/runtime
rm -rf "$out"
mkdir -p "$out"
cp -R build/dicts "$out/dicts"
cp systems/stenotype-extended.toml "$out/"
if [ -d build/morph ]; then
    mkdir -p "$out/morph"
    cp build/morph/*.hfstol "$out/morph/" 2>/dev/null || true
fi
du -sh "$out"
