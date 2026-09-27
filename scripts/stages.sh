#!/usr/bin/env bash
# A dictionary's pipeline, stage by stage, against its golden files: what each
# prefix of the pipeline relates, which the compiler wrote under
# build/work/NAME/pipeline, and tests/golden/NAME/STAGE.tsv.
#
# Usage: scripts/stages.sh NAME [--update]
#
# `--update` writes what the pipeline relates as the new golden files.
set -euo pipefail
cd "$(dirname "$0")/.."

name=${1:?usage: scripts/stages.sh NAME [--update]}
update=${2:-}
built=build/work/$name/pipeline
golden=tests/golden/$name

ls "$built"/*.tsv > /dev/null 2>&1 || { echo "$built has no pipeline: compile $name" >&2; exit 1; }
if [ "$update" = --update ]; then
    rm -rf "$golden"
    mkdir -p "$golden"
    cp "$built"/*.tsv "$golden"/
    echo "$name: wrote $(ls "$golden" | wc -l | tr -d ' ') stage files"
    exit 0
fi

failed=0
for file in "$built"/*.tsv; do
    stage=$(basename "$file")
    if ! diff -q "$golden/$stage" "$file" > /dev/null 2>&1; then
        echo "$name: stage ${stage%.tsv} differs from $golden/$stage:" >&2
        diff "$golden/$stage" "$file" 2>&1 | head -20 >&2 || true
        failed=1
    fi
done
for file in "$golden"/*.tsv; do
    [ -f "$built/$(basename "$file")" ] || { echo "$name: no stage $(basename "$file" .tsv) any more" >&2; failed=1; }
done
[ $failed = 0 ] && echo "$name: $(ls "$built"/*.tsv | wc -l | tr -d ' ') stages as golden"
exit $failed
