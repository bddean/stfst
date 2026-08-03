#!/usr/bin/env bash
# Build the morphology artifacts: generate the two lexicons, then compile all
# three. Separate from `stfst compile` because morphology is not a dictionary,
# and separate from the Rust build because the lexicons come from data that is
# installed with Plover rather than checked in here.
set -euo pipefail
cd "$(dirname "$0")/.."

PLOVER_PYTHON=${PLOVER_PYTHON:-/Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13}

if [ ! -x "$PLOVER_PYTHON" ]; then
    echo "no Plover python at $PLOVER_PYTHON; set PLOVER_PYTHON" >&2
    exit 1
fi

"$PLOVER_PYTHON" scripts/gen-morph.py build/morph
cargo build --release -q
./target/release/stfst morph
ls -la build/morph/*.hfstol
