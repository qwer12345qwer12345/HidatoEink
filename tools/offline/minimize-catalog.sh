#!/usr/bin/env bash
# JDK 17 + Kotlin 1.9.x CLI; board size changes cost, not correctness.
set -euo pipefail
cd "$(dirname "$0")/../.."
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
kotlinc \
  app/src/main/java/com/eink/hidato/domain/Grids.kt \
  app/src/main/java/com/eink/hidato/domain/HamiltonianPaths.kt \
  app/src/main/java/com/eink/hidato/domain/HidatoSolver.kt \
  app/src/main/java/com/eink/hidato/domain/MinimumClueOptimizer.kt \
  app/src/main/java/com/eink/hidato/data/PrebuiltPuzzleCodec.kt \
  tools/offline/MinimizeCatalog.kt \
  -include-runtime -d "$tmp_dir/minimum-clues.jar"
java -jar "$tmp_dir/minimum-clues.jar" "${1:-app/src/main/assets/unique-puzzles.tsv}" "${2:-1}" "${3:-30000}"
