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

# `user.json` goes as itself, not as the artifact compiled from it: the keyboard
# reads it as an editable dictionary, so an entry added on the desktop needs a
# push and not a recompile. `stfst-user.json` is the phone's own and is not here.
rm -rf "$out/dicts/user.stfst"
mkdir -p "$out/editable"
cp android/editable.toml "$out/editable/"
cp ~/.config/plover/user.json "$out/editable/"
# The desktop's machine options and keymaps, read by the keyboard as they are.
if [ -f ~/.config/plover/plover.cfg ]; then
    cp ~/.config/plover/plover.cfg "$out/"
fi
if [ -d build/morph ]; then
    mkdir -p "$out/morph"
    cp build/morph/*.hfstol "$out/morph/" 2>/dev/null || true
fi
du -sh "$out"
