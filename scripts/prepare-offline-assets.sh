#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
# Pinned Three.js version; MediaPipe 0.5 is the version used by the existing game.
npm install --prefix "$TMP" --no-audit --no-fund --ignore-scripts --silent 'three@0.160.1' '@mediapipe/pose@0.5'
mkdir -p "$ASSETS/vendor/mediapipe" "$ASSETS/vendor/three"
cp -R "$TMP/node_modules/@mediapipe/pose/." "$ASSETS/vendor/mediapipe/"
mkdir -p "$ASSETS/vendor/three/build" "$ASSETS/vendor/three/examples"
cp "$TMP/node_modules/three/build/three.module.js" "$ASSETS/vendor/three/build/"
cp -R "$TMP/node_modules/three/examples/jsm" "$ASSETS/vendor/three/examples/"
for f in pose.js pose_solution_packed_assets.data pose_solution_simd_wasm_bin.wasm pose_solution_wasm_bin.wasm; do
  test -s "$ASSETS/vendor/mediapipe/$f" || { echo "Missing MediaPipe dependency: $f" >&2; exit 1; }
done
# Fail the build if any external script dependency is accidentally reintroduced.
if grep -Eq 'https://cdn.jsdelivr.net/npm/(three|@mediapipe)' "$ASSETS/index.html"; then
  echo 'External runtime dependency remains in index.html' >&2; exit 1
fi
echo 'Offline tracking and boxer libraries bundled.'
