#!/usr/bin/env bash
# Verifies the entire bundled offline puzzle pack using the exact Kotlin solver.
set -euo pipefail
cd "$(dirname "$0")/../.."
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
kotlinc \
  app/src/main/java/com/eink/hidato/domain/Grids.kt \
  app/src/main/java/com/eink/hidato/domain/HamiltonianPaths.kt \
  app/src/main/java/com/eink/hidato/domain/HidatoSolver.kt \
  app/src/main/java/com/eink/hidato/data/PrebuiltPuzzleCodec.kt \
  tools/offline/VerifyCatalog.kt \
  -include-runtime -d "$tmp_dir/check.jar"
java -jar "$tmp_dir/check.jar" "${1:-app/src/main/assets/unique-puzzles.tsv}"
