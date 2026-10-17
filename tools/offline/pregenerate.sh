#!/usr/bin/env bash
# Requires JDK 17+ and Kotlin CLI compiler 1.9.x. Run at repository root.
set -euo pipefail
cd "$(dirname "$0")/../.."
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
kotlinc \
  app/src/main/java/com/eink/hidato/domain/Grids.kt \
  app/src/main/java/com/eink/hidato/domain/HamiltonianPaths.kt \
  app/src/main/java/com/eink/hidato/domain/HidatoSolver.kt \
  app/src/main/java/com/eink/hidato/domain/PuzzleGenerator.kt \
  tools/offline/PreGenerate.kt \
  -include-runtime -d "$tmp_dir/pregen.jar"
java -jar "$tmp_dir/pregen.jar" "$@"
