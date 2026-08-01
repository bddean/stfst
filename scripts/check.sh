#!/usr/bin/env bash
# Everything that can fail: unit tests, then the golden files against the
# compiled artifacts. Needs `stfst compile recipe.toml` to have run.
set -euo pipefail
cd "$(dirname "$0")/.."

cargo test --workspace
cargo build --release -q
B=./target/release/stfst

for dict in emily-symbols emily-modifiers shrimple phrasing typey-type; do
    echo "== $dict"
    $B test "build/dicts/$dict.stfst" "tests/golden/$dict.tsv" | tail -1
    # The reverse direction has no oracle of its own; forward lookup is the check.
    scripts/check-reverse.py "build/dicts/$dict.stfst" "tests/golden/$dict.tsv"
done

# Translation is not a per-tape question, so it needs its own fixture: a stroke
# stream through the whole stack, where the answer depends on the history.
echo "== translate"
for case in tests/translate/*.strokes; do
    if $B translate build/dicts/*.stfst \
        --system systems/stenotype-extended.toml --strokes "$case" \
        | diff -u "${case%.strokes}.expected" - > /dev/null; then
        echo "$(basename "$case") ok"
    else
        $B translate build/dicts/*.stfst \
            --system systems/stenotype-extended.toml --strokes "$case" \
            | diff -u "${case%.strokes}.expected" - || true
        echo "$(basename "$case") FAILED" >&2
        exit 1
    fi
done
