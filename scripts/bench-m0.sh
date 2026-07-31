#!/usr/bin/env bash
# M0 acceptance measurement: artifact size and per-lookup latency for
# typey-type-full, against Plover's in-memory dictionary.
set -euo pipefail
cd "$(dirname "$0")/.."

JSON=${1:-$HOME/.config/plover/typey-type-full.json}
SYS=systems/stenotype-extended.toml
B=./target/release/stfst
mkdir -p build
cd build

echo "== import"
../$B import "$JSON" --system "../$SYS" --align pairwise -o m0.lexc --report m0.skipped
echo "== compile"
/usr/bin/time -l hfst-lexc m0.lexc -o m0.hfst 2>m0.lexc.time >/dev/null
hfst-fst2fst -O -i m0.hfst -o m0.hfstol

size() { stat -c%s "$1" 2>/dev/null || stat -f%z "$1"; }
rss() { grep "maximum resident" "$1" | awk '{print $1}'; }

echo "== probes"
python3 - "$JSON" <<'PY'
import json, random, sys
d = json.load(open(sys.argv[1]))
random.seed(7)
open('probe.outlines', 'w').write('\n'.join(random.sample(list(d), 20000)) + '\n')
PY
../$B tape --system "../$SYS" < probe.outlines | cut -f2 > probe.tape

echo "== lookup throughput"
/usr/bin/time -l hfst-optimized-lookup -q m0.hfstol < probe.tape > m0.out 2> m0.look.time
LOOK_REAL=$(grep -E "^ *[0-9.]+ real" m0.look.time | awk '{print $1}')

python3 - "$JSON" <<'PY'
import json, resource, sys, time
raw = json.load(open(sys.argv[1]))
d = {tuple(k.split('/')): v for k, v in raw.items()}
rev = {}
for k, v in d.items():
    rev.setdefault(v, []).append(k)
peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / (1024 * 1024)
probes = [tuple(l.strip().split('/')) for l in open('probe.outlines')]
t = time.perf_counter()
hits = sum(1 for p in probes if p in d)
el = time.perf_counter() - t
print(f"plover-style resident   {peak:8.1f} MB  (dict + reverse index)")
print(f"plover-style lookup     {el / len(probes) * 1e6:8.3f} us/probe  ({hits} hits)")
PY

echo "artifact hfstol         $(size m0.hfstol) bytes (mmap-able, zero heap)"
echo "source json             $(size "$JSON") bytes"
echo "compile peak rss        $(rss m0.lexc.time) bytes"
echo "hfstol lookup wall      ${LOOK_REAL}s for 20000 probes"
echo "hfstol hit lines        $(grep -c . m0.out || true)"
