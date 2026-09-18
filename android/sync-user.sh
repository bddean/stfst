#!/usr/bin/env bash
# The phone's own dictionary, in both directions.
#
# `stfst-user.json` is what the keyboard defines into. It lives on external
# storage so adb can reach it without root, and this is the only thing that moves
# it: pull what the phone has, union it with the desktop's copy, push the result
# back. A network sync can replace this; nothing else has to change when it does.
#
# The desktop's `user.json` travels the other way and rides in the APK, so it is
# not here: `sync-assets.sh` puts it in the assets and an install unpacks it.
set -euo pipefail
cd "$(dirname "$0")/.."

ADB=${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}
REMOTE_DIR=/sdcard/Android/data/cx.yeet.stfst/files/dicts
REMOTE=$REMOTE_DIR/stfst-user.json
LOCAL=${STFST_USER:-$HOME/.config/plover/stfst-user.json}

# The keyboard makes this directory, and nothing here may make it first: a
# directory adb created belongs to the shell user, and then the app cannot write
# in it at all.
if ! "$ADB" shell "test -d $REMOTE_DIR" 2>/dev/null; then
    echo "the keyboard has not made $REMOTE_DIR yet."
    echo "open it once on the phone, then: make sync-user"
    exit 0
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
theirs=$work/phone.json

if "$ADB" shell "test -f $REMOTE" 2>/dev/null; then
    "$ADB" pull -a "$REMOTE" "$theirs" > /dev/null
else
    echo '{}' > "$theirs"
fi

python3 - "$theirs" "$LOCAL" <<'EOF'
import json, os, sys

phone, desktop = sys.argv[1], sys.argv[2]
theirs = json.load(open(phone))
ours = json.load(open(desktop)) if os.path.exists(desktop) else {}

# The phone's entry wins a disagreement: it is the one that was just made, and
# this file is edited nowhere else but here and by hand.
merged = dict(ours)
for outline, translation in theirs.items():
    was = ours.get(outline)
    if was is not None and was != translation:
        print("  %s: %r over %r" % (outline, translation, was))
    merged[outline] = translation

for outline in sorted(set(merged) - set(theirs)):
    print("  %s: %r, from here" % (outline, merged[outline]))

with open(desktop, "w") as f:
    json.dump(dict(sorted(merged.items())), f, indent=2, ensure_ascii=False)
    f.write("\n")
print("%d entries in %s" % (len(merged), desktop))
EOF

"$ADB" push "$LOCAL" "$REMOTE" > /dev/null
echo "pushed $(basename "$LOCAL") to the phone"
