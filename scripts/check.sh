#!/usr/bin/env bash
# Everything that can fail: unit tests, then the golden files against the
# compiled artifacts. Needs `stfst compile recipe.toml` to have run.
set -euo pipefail
cd "$(dirname "$0")/.."

cargo test --workspace
cargo build --release -q
B=./target/release/stfst

# The two outline parsers have to agree, or a golden generator writes tapes that
# are not the tapes the runtime looks up. Cheap to check over a real dictionary.
echo "== outline parse"
python3 - <<'EOF' > /tmp/stfst-tapes-py.tsv
import json, os, sys
sys.path.insert(0, "scripts")
import plover_tape
system = plover_tape.System.load("systems/stenotype-extended.toml")
raw = json.load(open(os.path.expanduser("~/.config/plover/typey-type-full.json")))
print("\n".join("%s\t%s" % (o, system.tape(o)) for o in raw))
EOF
cut -f1 /tmp/stfst-tapes-py.tsv \
    | $B tape --system systems/stenotype-extended.toml \
    | cut -f1,2 > /tmp/stfst-tapes-rs.tsv
diff -q /tmp/stfst-tapes-py.tsv /tmp/stfst-tapes-rs.tsv \
    && echo "$(wc -l < /tmp/stfst-tapes-py.tsv) outlines agree"

# Every artifact in the stack, so adding one to the recipe cannot skip its tests.
# A dictionary with no golden file has a checker of its own instead, because what
# it answers depends on more than the tape: see retro-words.
for artifact in build/dicts/*.stfst; do
    dict=$(basename "$artifact" .stfst)
    echo "== $dict"
    if [ -f "tests/golden/$dict.tsv" ]; then
        $B test "$artifact" "tests/golden/$dict.tsv" | tail -1
        # The reverse direction has no oracle of its own; forward lookup is the check.
        scripts/check-reverse.py "$artifact" "tests/golden/$dict.tsv"
    else
        checker="scripts/check-$dict.py"
        if [ ! -x "$checker" ]; then
            echo "$dict has neither a golden file nor $checker" >&2
            exit 1
        fi
        "$checker" "$artifact"
    fi
done

# Host contract items 5 and 6 have no consumer in `recipe.toml`, so they are
# checked against a fixture instead of being taken on trust. Needs
# `stfst compile tests/recipe.toml --out build/test-dicts --work build/test-work`.
echo "== contract"
for case in tests/probe/*.strokes; do
    if $B translate build/test-dicts/probe.stfst \
        --system systems/stenotype-extended.toml --strokes "$case" \
        | diff -u "${case%.strokes}.expected" - > /dev/null; then
        echo "$(basename "$case") ok"
    else
        $B translate build/test-dicts/probe.stfst \
            --system systems/stenotype-extended.toml --strokes "$case" \
            | diff -u "${case%.strokes}.expected" - || true
        echo "$(basename "$case") FAILED" >&2
        exit 1
    fi
done

# Morphology. Skipped rather than failed where the artifacts are absent: they are
# built from data that ships with Plover rather than checked in, so a machine can
# have this repo and not have them. Plover's python is also the oracle here, which
# is why one condition covers both.
echo "== morphology"
PLOVER_PYTHON=${PLOVER_PYTHON:-/Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13}
if [ -f build/morph/ortho.hfstol ] && [ -x "$PLOVER_PYTHON" ]; then
    "$PLOVER_PYTHON" scripts/check-ortho-rules.py build/morph/ortho.hfst 400
    "$PLOVER_PYTHON" scripts/check-ortho.py 4000
    "$PLOVER_PYTHON" scripts/check-infl.py 20000 | head -1
else
    echo "skipped: no build/morph artifacts, or no Plover python"
fi

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
