#!/usr/bin/env bash
# A stroke fixture: what a stack writes for each `DIR/*.strokes`, against the
# `.expected` beside it. Translation is not a per-tape question, so a golden file
# cannot ask it.
#
# Usage: scripts/fixture.sh DIR [--update]
#
# The stack is the artifacts `DIR/artifacts` names, as shell words so a glob
# works, or `build/dicts/NAME.stfst` for a directory named after a dictionary.
# `DIR/requires` names files the fixture needs, and it is skipped where one is
# absent. A directory with an `editable.toml` is a dictionary the strokes write
# to, so each case runs on a copy of it and the directory is never written.
# `--update` writes what the stack wrote as the new expectation.
set -euo pipefail
cd "$(dirname "$0")/.."

dir=${1:?usage: scripts/fixture.sh DIR [--update]}
dir=${dir%/}
update=${2:-}
name=$(basename "$dir")
B=./target/release/stfst

ls "$dir"/*.strokes > /dev/null 2>&1 || { echo "$dir has no .strokes files" >&2; exit 1; }

if [ -f "$dir/requires" ]; then
    for need in $(cat "$dir/requires"); do
        [ -e "$need" ] || { echo "$name skipped: no $need"; exit 0; }
    done
fi

if [ -f "$dir/artifacts" ]; then
    # shellcheck disable=SC2046
    set -- $(cat "$dir/artifacts")
else
    set -- "build/dicts/$name.stfst"
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

for case in "$dir"/*.strokes; do
    editable=()
    if [ -f "$dir/editable.toml" ]; then
        rm -rf "$work/editable"
        mkdir "$work/editable"
        cp "$dir"/editable.toml "$work/editable/"
        editable=(--editable "$work/editable")
    fi
    expected=${case%.strokes}.expected
    if ! $B translate "$@" ${editable[@]+"${editable[@]}"} --system systems/stenotype-extended.toml \
        --strokes "$case" > "$work/got" 2> "$work/err"; then
        cat "$work/err" >&2
        echo "$(basename "$case") FAILED" >&2
        exit 1
    fi
    if [ "$update" = --update ]; then
        cp "$work/got" "$expected"
        echo "$(basename "$case") written"
    elif diff -u "$expected" "$work/got"; then
        echo "$(basename "$case") ok"
    else
        echo "$(basename "$case") FAILED" >&2
        exit 1
    fi
done
