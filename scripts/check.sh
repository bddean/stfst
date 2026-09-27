#!/usr/bin/env bash
# Everything that can fail: unit tests, then the golden files against the
# compiled artifacts.
#
# Usage: scripts/check.sh [DICT...]
#
# With names, only what belongs to those dictionaries: each is compiled, then its
# generator's `--check`, its golden files and its stroke fixture run. The recipe
# says which generator and golden files a dictionary has.
#
# It compiles first, and that is not a convenience. A stale artifact agrees with
# the golden file that was generated from the same stale compiler, so the check
# passes over a bug in both: `{^}` attached on the wrong side for two commits
# because nothing here rebuilt. Set STFST_NO_COMPILE=1 to skip it when iterating
# on a checker, and know what you are giving up.
set -euo pipefail
cd "$(dirname "$0")/.."

B=./target/release/stfst
SYSTEM=$(python3 scripts/recipe.py recipe.toml system)
# The generators need cmudict and wordfreq, which the phrasing venv has, so a
# generator check is skipped where that is absent.
GEN_PYTHON=${GEN_PYTHON:-${VOWELS_PYTHON:-$HOME/.config/plover/phrasing/.venv/bin/python}}
PLOVER_PYTHON=${PLOVER_PYTHON:-/Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13}
# Generators already run, so two dictionaries one script writes cost one run.
checked=" "

# A dictionary imported as it stands, a JSON file or a plain `.dict`, is its own
# expectation: its entries are outline to record already. So its golden file is
# written here from the current source rather than committed, where it was a copy
# that went stale with every entry. What it checks is the outline parse and the
# compile; `check-plugin.py` holds the same entries to Plover.
source_golden() {
    local name=$1 kind=$2 source=$3 sub out
    case $kind in
        json) sub=import ;;
        plain) sub=plain ;;
        *) return 0 ;;
    esac
    mkdir -p build/golden
    out=$($B "$sub" --system "$SYSTEM" "$source" -o /dev/null \
        --golden "build/golden/$name.tsv" 2>&1) || { echo "$out" >&2; exit 1; }
}

# Every check that belongs to one dictionary. A dictionary whose answer depends
# on more than the tape has a stroke fixture under `tests/` instead of a golden
# file, because a golden file has nowhere to put the buffer: see
# `tests/retro-words`.
check_dict() {
    local name=$1 generator=$2 goldens=$3 artifact=build/dicts/$1.stfst golden
    echo "== $name"
    if [ "$generator" != - ] && [ "${checked#* $generator }" = "$checked" ]; then
        checked="$checked$generator "
        if [ -x "$GEN_PYTHON" ]; then
            "$GEN_PYTHON" "$generator" --check && echo "$(basename "$generator") --check: current"
        else
            echo "skipped $generator: no $GEN_PYTHON"
        fi
    fi
    golden=tests/golden/$name.tsv
    [ -f "$golden" ] || golden=build/golden/$name.tsv
    [ -f "$golden" ] || [ -d "tests/$name" ] || {
        echo "$name has no golden file and no tests/$name" >&2
        exit 1
    }
    if [ -f "$golden" ]; then
        $B test "$artifact" "$golden" | tail -30
        # The reverse direction has no oracle of its own; forward lookup is the check.
        scripts/check-reverse.py "$artifact" "$golden"
    fi
    if [ "$goldens" != - ]; then
        for golden in $goldens; do
            $B test "$artifact" "$golden" | tail -30
        done
    fi
    if [ -d "tests/$name" ]; then
        scripts/fixture.sh "tests/$name"
    fi
}

dicts=$(python3 scripts/recipe.py recipe.toml dicts)
names=" $(cut -f1 <<< "$dicts" | tr '\n' ' ')"
in_recipe() { [ "${names#* $1 }" != "$names" ]; }

if [ $# -gt 0 ]; then
    for name in "$@"; do
        in_recipe "$name" || {
            echo "recipe.toml has no dictionary named $name" >&2
            exit 1
        }
    done
    cargo build --release -q
    if [ -z "${STFST_NO_COMPILE:-}" ]; then
        # shellcheck disable=SC2046
        $B compile recipe.toml $(printf -- '--dict %s ' "$@") > /dev/null
    fi
    while IFS=$'\t' read -r name kind source generator goldens; do
        for want in "$@"; do
            if [ "$name" = "$want" ]; then
                source_golden "$name" "$kind" "$source"
                check_dict "$name" "$generator" "$goldens"
            fi
        done
    done <<< "$dicts"
    exit 0
fi

cargo test --workspace
cargo build --release -q

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
    | $B tape --system "$SYSTEM" \
    | cut -f1,2 > /tmp/stfst-tapes-rs.tsv
diff -q /tmp/stfst-tapes-py.tsv /tmp/stfst-tapes-rs.tsv \
    && echo "$(wc -l < /tmp/stfst-tapes-py.tsv) outlines agree"

# The Python halves of the Plover plugin: the record language and the reader. Both
# run under any python, because a golden generator uses the first as an oracle and
# an oracle that needed Plover would not run where the generators do.
echo "== plugin units"
python3 -m unittest discover plover-plugin/tests 2>&1 | tail -3

# Plover loads the same dictionaries in its own list, which has to agree.
echo "== plover.cfg"
python3 scripts/check-plover-cfg.py

echo "== source goldens"
rm -rf build/golden
while IFS=$'\t' read -r name kind source _ _; do
    source_golden "$name" "$kind" "$source"
done <<< "$dicts"
ls build/golden | wc -l | xargs echo "written:"

# The Python `hfstol` reader against the Rust one, over the same kind of tape list
# `crosscheck-ol.sh` uses. One artifact, because the answer does not vary by
# dictionary and the whole stack would cost a minute.
echo "== python reader"
scripts/crosscheck-py.sh build/dicts/typey-type.stfst "$SYSTEM" | tail -1

# Every dictionary in the recipe, so adding one cannot skip its tests.
while IFS=$'\t' read -r name _ _ generator goldens; do
    check_dict "$name" "$generator" "$goldens"
done <<< "$dicts"

# The fixtures no one dictionary owns: the host contract items no dictionary in
# `recipe.toml` uses yet, a held stroke across three dictionaries, the phrasing
# hole, and the whole stack.
for dir in tests/*/; do
    name=$(basename "$dir")
    ls "$dir"*.strokes > /dev/null 2>&1 || continue
    in_recipe "$name" && continue
    echo "== $name"
    scripts/fixture.sh "$dir"
done

# Morphology. Skipped rather than failed where the artifacts are absent: they are
# built from data that ships with Plover rather than checked in, so a machine can
# have this repo and not have them. Plover's python is also the oracle here, which
# is why one condition covers both.
echo "== morphology"
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
