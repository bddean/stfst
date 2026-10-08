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

# An editable artifact names a file on this machine, so none goes. `user.json`
# goes as itself instead, which the keyboard reads as an editable dictionary, so
# an entry added on the desktop needs a push and not a recompile.
# `stfst-user.json` is the phone's own and is not here.
for manifest in "$out"/dicts/*.stfst/manifest.toml; do
    if grep -q '^\[editable\]' "$manifest"; then rm -rf "$(dirname "$manifest")"; fi
done
mkdir -p "$out/editable"
cp android/editable.toml "$out/editable/"
cp dictionaries/user.json "$out/editable/"
# The desktop's machine options and keymaps, read by the keyboard as they are.
if [ -f ~/.config/plover/plover.cfg ]; then
    cp ~/.config/plover/plover.cfg "$out/"
fi
if [ -d build/morph ]; then
    mkdir -p "$out/morph"
    cp build/morph/*.hfstol "$out/morph/" 2>/dev/null || true
fi
du -sh "$out"
