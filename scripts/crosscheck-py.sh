#!/usr/bin/env bash
# Hold the Python `hfstol` reader to the Rust one. Same shape as
# `crosscheck-ol.sh`, which holds the Rust one to `hfst-optimized-lookup`, so the
# chain runs Python to Rust to HFST and the format has one reading.
#
# Usage: scripts/crosscheck-py.sh <artifact> <system.toml> [dictionary.json]
#
# Tapes come from three places: every outline in the dictionary if one is given,
# every tape in the golden files, and random key sequences. The random ones
# matter most, because a reader goes wrong on dead ends and partial paths rather
# than on entries that exist.
set -euo pipefail
cd "$(dirname "$0")/.."

artifact=${1:?artifact}
system=${2:?system.toml}
dict=${3:-}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cargo build --release -q
stfst=target/release/stfst

order=$(python3 - "$system" <<'PY'
import re, sys
print(re.search(r'^order\s*=\s*"([^"]*)"', open(sys.argv[1]).read(), re.M).group(1))
PY
)

if [ -n "$dict" ]; then
    python3 -c "import json,sys; print('\n'.join(json.load(open(sys.argv[1]))))" "$dict" \
        | "$stfst" tape --system "$system" | cut -f2 | grep -v '^!$' >> "$work/tapes"
fi
cat tests/golden/*.tsv build/golden/*.tsv 2>/dev/null | grep -v '^!' | cut -f1 >> "$work/tapes"

python3 - "$order" >> "$work/tapes" <<'PY'
import random, sys
keys = sys.argv[1] + '/'
random.seed(20260824)  # a fixed seed, so a failure is reproducible
for _ in range(40000):
    print(''.join(random.choice(keys) for _ in range(random.randint(1, 12))))
PY

sort -u "$work/tapes" | grep -v '^$' > "$work/unique"
echo "$(wc -l < "$work/unique") distinct tapes"

"$stfst" lookup "$artifact" --system "$system" --tape < "$work/unique" \
    | cut -f2- > "$work/rust"

# The tape list is an argument and not stdin, because stdin is the script.
python3 - "$artifact" "$work/unique" > "$work/python" <<'PY'
import os, sys
sys.path.insert(0, "plover-plugin")
from plover_stfst.ol import Automaton

fst = Automaton.open(os.path.join(sys.argv[1], "forward.hfstol"))
for line in open(sys.argv[2]):
    tape = line.rstrip("\n")
    records = fst.lookup(tape)
    print("\t".join([tape] + (records if records else ["-"])))
PY

# Records are a set, so compare them as one.
for engine in rust python; do
    python3 - "$work/$engine" > "$work/$engine.sorted" <<'PY'
import sys
for line in open(sys.argv[1]):
    f = line.rstrip('\n').split('\t')
    print('\t'.join([f[0]] + sorted(f[1:])))
PY
done

if diff -q "$work/rust.sorted" "$work/python.sorted" > /dev/null; then
    echo "agreed on every tape"
else
    diff "$work/rust.sorted" "$work/python.sorted" | head -40
    echo "DISAGREED: $(diff "$work/rust.sorted" "$work/python.sorted" | grep -c '^<') tapes" >&2
    exit 1
fi
