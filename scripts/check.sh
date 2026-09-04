#!/usr/bin/env bash
# Everything that can fail: unit tests, then the golden files against the
# compiled artifacts.
#
# It compiles first, and that is not a convenience. A stale artifact agrees with
# the golden file that was generated from the same stale compiler, so the check
# passes over a bug in both: `{^}` attached on the wrong side for two commits
# because nothing here rebuilt. Set STFST_NO_COMPILE=1 to skip it when iterating
# on a checker, and know what you are giving up.
set -euo pipefail
cd "$(dirname "$0")/.."

cargo test --workspace
cargo build --release -q
B=./target/release/stfst

# A stroke fixture: what these dictionaries write for a stream, against what they
# wrote when the file was recorded. Translation is not a per-tape question, so a
# golden file cannot ask it.
fixtures() {
    local dir=$1 case
    shift
    for case in "$dir"/*.strokes; do
        if $B translate "$@" --system systems/stenotype-extended.toml \
            --strokes "$case" | diff -u "${case%.strokes}.expected" - > /dev/null; then
            echo "$(basename "$case") ok"
        else
            $B translate "$@" --system systems/stenotype-extended.toml \
                --strokes "$case" | diff -u "${case%.strokes}.expected" - || true
            echo "$(basename "$case") FAILED" >&2
            exit 1
        fi
    done
}

if [ -z "${STFST_NO_COMPILE:-}" ]; then
    echo "== compile"
    $B compile recipe.toml > /dev/null
    $B compile tests/recipe.toml --out build/test-dicts --work build/test-work > /dev/null
fi

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

# The Python halves of the Plover plugin: the record language and the reader. Both
# run under any python, because a golden generator uses the first as an oracle and
# an oracle that needed Plover would not run where the generators do.
echo "== plugin units"
python3 -m unittest discover plover-plugin/tests 2>&1 | tail -3

# The Python `hfstol` reader against the Rust one, over the same kind of tape list
# `crosscheck-ol.sh` uses. One artifact, because the answer does not vary by
# dictionary and the whole stack would cost a minute.
echo "== python reader"
scripts/crosscheck-py.sh build/dicts/typey-type.stfst systems/stenotype-extended.toml \
    | tail -1

# Every artifact in the stack, so adding one to the recipe cannot skip its tests.
# A dictionary whose answer depends on more than the tape has a stroke fixture
# under `tests/` instead, because a golden file has nowhere to put the buffer:
# see `tests/retro-words`.
for artifact in build/dicts/*.stfst; do
    dict=$(basename "$artifact" .stfst)
    echo "== $dict"
    if [ -f "tests/golden/$dict.tsv" ]; then
        $B test "$artifact" "tests/golden/$dict.tsv" | tail -1
        # The reverse direction has no oracle of its own; forward lookup is the check.
        scripts/check-reverse.py "$artifact" "tests/golden/$dict.tsv"
    elif [ -d "tests/$dict" ]; then
        # A frame is shown text another dictionary wrote, so a fixture may name
        # the artifacts it needs instead of taking its own.
        if [ -f "tests/$dict/artifacts" ]; then
            # shellcheck disable=SC2046
            fixtures "tests/$dict" $(cat "tests/$dict/artifacts")
        else
            fixtures "tests/$dict" "$artifact"
        fi
    else
        echo "$dict has neither tests/golden/$dict.tsv nor tests/$dict" >&2
        exit 1
    fi
done

# Host contract items 5 and 6 have no consumer in `recipe.toml`, so they are
# checked against a fixture instead of being taken on trust. Needs
# `stfst compile tests/recipe.toml --out build/test-dicts --work build/test-work`.
echo "== contract"
fixtures tests/probe build/test-dicts/probe.stfst

# A held stroke, both kinds: a frame that reads the text after its keys, and a
# record that only follows it. Three dictionaries, because the word comes from
# another one, and that is the whole reason the host holds anything.
echo "== held"
fixtures tests/held build/test-dicts/held.stfst build/test-dicts/ahead.stfst \
    build/test-dicts/words.stfst

# What a held suffix writes is the host's own orthography, so this fixture needs
# the artifacts that hold it, and is skipped rather than failed where they are
# absent.
echo "== ahead"
if [ -f build/morph/ortho.hfstol ]; then
    # shellcheck disable=SC2046
    fixtures tests/ahead $(cat tests/ahead/artifacts)
else
    echo "skipped: no build/morph artifacts"
fi

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
    # The golden files cannot catch the importer and the formatter agreeing on the
    # wrong thing, and for `{^}` they did not. This compares what lands in the
    # document against what Plover puts there, for every entry in the stack.
    echo "== translation against Plover"
    "$PLOVER_PYTHON" scripts/check-translate.py
    # And the plugin, which is the same question asked of a compiled artifact
    # rather than of the importer: what Plover writes for the entry it holds.
    echo "== plover plugin"
    "$PLOVER_PYTHON" scripts/check-plugin.py
    # A held tail is the one mechanism whose Plover half is not a dictionary, so
    # it is checked through a whole engine against the same fixture the Rust host
    # answered.
    echo "== plover hold"
    "$PLOVER_PYTHON" scripts/check-hold.py
else
    echo "skipped: no build/morph artifacts, or no Plover python"
fi

# The retro fold is not in `recipe.toml`, because no host yet hands a dictionary its
# own run newest-first, so `build/dicts` never holds it and the loop above cannot
# reach it. Its grammar is checked all the same, and building it costs a quarter of
# an hour, so it is opt-in.
if [ -n "${STFST_RETRO:-}" ]; then
    echo "== phrasing-retro"
    $B compile recipe-retro.toml --out build/retro-dicts --work build/retro-work \
        > /dev/null
    $B test build/retro-dicts/phrasing-retro.stfst tests/golden/phrasing-retro.tsv \
        | tail -1
fi

# Translation is not a per-tape question, so it needs its own fixture: a stroke
# stream through the whole stack, where the answer depends on the history.
echo "== translate"
fixtures tests/translate build/dicts/*.stfst
