#!/usr/bin/env bash
# Reproducible Issue #13 geometry/counterexample analysis; JDK 17 + Kotlin CLI 1.9.x.
set -euo pipefail
cd "$(dirname "$0")/../.."

catalog="${1:-app/src/main/assets/unique-puzzles.tsv}"
corpus="${2:-tools/offline/fixed-path-geometry-corpus.tsv}"
witnesses="${3:-build/fixed-path-geometry/witnesses.csv}"
summary="${4:-build/fixed-path-geometry/summary.csv}"
report="${5:-build/fixed-path-geometry/report.md}"
max_window="${6:-8}"
per_window_cap="${7:-64}"
solver_nodes="${8:-500000}"
solver_time_ms="${9:-500}"

command -v kotlinc >/dev/null || { echo "kotlinc is required" >&2; exit 1; }
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

kotlinc \
  app/src/main/java/com/eink/hidato/domain/Grids.kt \
  app/src/main/java/com/eink/hidato/domain/HamiltonianPaths.kt \
  app/src/main/java/com/eink/hidato/domain/HidatoSolver.kt \
  app/src/main/java/com/eink/hidato/domain/MinimumClueOptimizer.kt \
  app/src/main/java/com/eink/hidato/data/PrebuiltPuzzleCodec.kt \
  tools/offline/AnalyzeFixedPathGeometry.kt \
  -include-runtime -d "$tmp_dir/fixed-path-geometry.jar"

java -jar "$tmp_dir/fixed-path-geometry.jar" \
  "$catalog" "$corpus" "$witnesses" "$summary" "$report" \
  "$max_window" "$per_window_cap" "$solver_nodes" "$solver_time_ms"
