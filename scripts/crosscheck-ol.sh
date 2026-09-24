#!/usr/bin/env bash
# Hold stfst-ol to hfst-optimized-lookup. The hfstol layout is not specified
# anywhere we can cite, so agreement with HFST's own reader is the only
# evidence our reading of it is right.
#
# Usage: scripts/crosscheck-ol.sh <artifact> <system.toml> [dictionary.json]
#
# Tapes come from three places: every outline in the dictionary if one is
# given, every tape in the golden files, and random key sequences. The random
# ones matter most, because a reader goes wrong on dead ends and partial paths
# rather than on entries that exist.
set -euo pipefail

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
    jq -r 'keys[]' "$dict" | "$stfst" tape --system "$system" | cut -f2 \
        | grep -v '^!$' >> "$work/tapes"
fi
cat tests/golden/*.tsv build/golden/*.tsv 2>/dev/null | grep -v '^!' | cut -f1 >> "$work/tapes"

python3 - "$order" >> "$work/tapes" <<'PY'
import random, sys
keys = sys.argv[1] + '/'
random.seed(20260731)  # a fixed seed, so a failure is reproducible
for _ in range(40000):
    print(''.join(random.choice(keys) for _ in range(random.randint(1, 12))))
PY

sort -u "$work/tapes" | grep -v '^$' > "$work/unique"
echo "$(wc -l < "$work/unique") distinct tapes"

for engine in stfst hfst; do
    "$stfst" lookup "$artifact" --system "$system" --tape --engine "$engine" \
        < "$work/unique" > "$work/$engine"
done

# Records are a set, so compare them as one.
for engine in stfst hfst; do
    python3 - "$work/$engine" > "$work/$engine.sorted" <<'PY'
import sys
for line in open(sys.argv[1]):
    f = line.rstrip('\n').split('\t')
    print('\t'.join([f[1]] + sorted(f[2:])))
PY
done

if diff -q "$work/stfst.sorted" "$work/hfst.sorted" > /dev/null; then
    echo "agreed on every tape"
else
    diff "$work/stfst.sorted" "$work/hfst.sorted" | head -40
    echo "DISAGREED: $(diff "$work/stfst.sorted" "$work/hfst.sorted" | grep -c '^<') tapes" >&2
    exit 1
fi
