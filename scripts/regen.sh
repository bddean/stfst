#!/usr/bin/env bash
# Run the generators until each one's --check passes, compiling the stack between
# rounds, since the generators read each other's output and the compiled stack.
# Then write the pipelines' stage golden files.
#
# Usage: scripts/regen.sh [--composed]
#
# --composed also runs find-typey-composed.py first, under Plover's python.
# Run in the dev shell (HFST). Fails if the generators do not settle.
set -euo pipefail
cd "$(dirname "$0")/.."

PY=${GEN_PYTHON:-$HOME/.config/plover/phrasing/.venv/bin/python}
PLOVER_PYTHON=${PLOVER_PYTHON:-/Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13}
ROUNDS=${ROUNDS:-5}
# Dependency order: the lexicon reads the vowel theory's Typey Type, the theory's
# tables read it too, the pipelines read both, and the vowel theory reads
# two-syllable.json and the affixes' golden file.
GENERATORS=(gen-typey-vowels gen-lexicon gen-theory gen-two-syllable gen-two-word gen-affixes)

cargo build --release -q -p stfst-cli
compile() {
    ./target/release/stfst compile recipe.toml >build/regen-compile.log 2>&1 || { cat build/regen-compile.log; exit 1; }
    grep -v ': up to date$' build/regen-compile.log || true
}

if [ "${1:-}" = --composed ]; then
    compile
    "$PLOVER_PYTHON" scripts/find-typey-composed.py
fi

for round in $(seq "$ROUNDS"); do
    compile
    stale=()
    for g in "${GENERATORS[@]}"; do
        "$PY" "scripts/$g.py" --check >/dev/null 2>&1 || stale+=("$g")
    done
    if [ ${#stale[@]} -eq 0 ]; then
        echo "regen: every generator is current after $((round - 1)) rounds"
        for d in tests/golden/*/; do scripts/stages.sh "$(basename "$d")" --update; done
        exit 0
    fi
    echo "regen: round $round: stale: ${stale[*]}"
    for g in "${stale[@]}"; do
        echo "== $g"
        "$PY" "scripts/$g.py" >"build/regen-$g.log" || { cat "build/regen-$g.log"; exit 1; }
        tail -3 "build/regen-$g.log"
    done
done
echo "regen: still stale after $ROUNDS rounds: ${stale[*]}" >&2
exit 1
