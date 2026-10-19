#!/usr/bin/env bash
# JDK 17 + Kotlin CLI 1.9.x; each corpus case uses identical budgets.
set -euo pipefail
cd "$(dirname "$0")/../.."

catalog="${1:-app/src/main/assets/unique-puzzles.tsv}"
corpus="${2:-tools/offline/minimum-clue-benchmark.tsv}"
raw_csv="${3:-build/minimum-clue-benchmark/raw.csv}"
summary_csv="${4:-build/minimum-clue-benchmark/summary.csv}"
budget_ms="${5:-30000}"
repetitions="${6:-3}"
master_nodes="${7:-1000000}"
oracle_nodes="${8:-50000000}"

command -v kotlinc >/dev/null || { echo "kotlinc is required" >&2; exit 1; }
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

kotlinc \
  app/src/main/java/com/eink/hidato/domain/Grids.kt \
  app/src/main/java/com/eink/hidato/domain/HamiltonianPaths.kt \
  app/src/main/java/com/eink/hidato/domain/HidatoSolver.kt \
  app/src/main/java/com/eink/hidato/domain/MinimumClueOptimizer.kt \
  app/src/main/java/com/eink/hidato/data/PrebuiltPuzzleCodec.kt \
  tools/offline/BenchmarkMinimumClues.kt \
  -include-runtime -d "$tmp_dir/minimum-clue-benchmark.jar"

java -jar "$tmp_dir/minimum-clue-benchmark.jar" \
  "$catalog" "$corpus" "$raw_csv" "$summary_csv" \
  "$budget_ms" "$repetitions" "$master_nodes" "$oracle_nodes"
