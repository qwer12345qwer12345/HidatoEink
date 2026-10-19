#!/usr/bin/env bash
# Reproducible geometry-guided local-window ordering benchmark.
set -euo pipefail
cd "$(dirname "$0")/../.."

catalog="${1:-app/src/main/assets/unique-puzzles.tsv}"
corpus="${2:-tools/offline/fixed-path-geometry-corpus.tsv}"
windows_csv="${3:-build/fixed-path-window-order/windows.csv}"
summary_csv="${4:-build/fixed-path-window-order/summary.csv}"
report_md="${5:-build/fixed-path-window-order/report.md}"
max_window="${6:-8}"
per_window_cap="${7:-64}"

command -v kotlinc >/dev/null || { echo "kotlinc is required" >&2; exit 1; }
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

kotlinc \
  app/src/main/java/com/eink/hidato/domain/Grids.kt \
  app/src/main/java/com/eink/hidato/domain/HamiltonianPaths.kt \
  app/src/main/java/com/eink/hidato/domain/HidatoSolver.kt \
  app/src/main/java/com/eink/hidato/domain/MinimumClueOptimizer.kt \
  app/src/main/java/com/eink/hidato/data/PrebuiltPuzzleCodec.kt \
  tools/offline/AnalyzeFixedPathWindowOrder.kt \
  -include-runtime -d "$tmp_dir/fixed-path-window-order.jar"

java -jar "$tmp_dir/fixed-path-window-order.jar" \
  "$catalog" "$corpus" "$windows_csv" "$summary_csv" "$report_md" \
  "$max_window" "$per_window_cap"
