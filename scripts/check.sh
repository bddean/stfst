#!/usr/bin/env bash
# Everything that can fail: unit tests, then the golden files against the
# compiled artifacts. Needs `stfst compile recipe.toml` to have run.
set -euo pipefail
cd "$(dirname "$0")/.."

cargo test --workspace
cargo build --release -q
B=./target/release/stfst

for dict in emily-symbols typey-type; do
    echo "== $dict"
    $B test "build/dicts/$dict.stfst" "tests/golden/$dict.tsv" | tail -1
done
