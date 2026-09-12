#!/usr/bin/env bash
# Compile lib/filter.sc against the fx framework classes and build every
# model's SynthDef in a headless sclang. Exits nonzero on any failure.
#
# usage: ci/check_sc.sh <dir with FxBase.sc/setup.sc> [extra class dirs...]
# env:   SCLANG  path to sclang binary (default: sclang on PATH)
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
sclang="${SCLANG:-sclang}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

conf="$work/sclang_conf.yaml"
{
  echo "includePaths:"
  echo "    - $here/lib"
  for d in "$@"; do echo "    - $d"; done
  echo "excludePaths: []"
  echo "postInlineWarnings: false"
} > "$conf"

log="$work/sclang.log"
runner=()
if command -v xvfb-run >/dev/null 2>&1; then runner=(xvfb-run -a); fi
# Linux CI has no display; macOS sclang ships without the offscreen plugin.
if [ "$(uname)" != "Darwin" ]; then export QT_QPA_PLATFORM=offscreen; fi

set +e
timeout 300 ${runner[@]+"${runner[@]}"} "$sclang" -l "$conf" "$here/ci/check_synthdefs.scd" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
set -e

if grep -q "^ALL_OK$" "$log"; then
  echo "sc check passed"
  exit 0
fi
echo "sc check failed (sclang exit $status)"
grep -nE "ERROR|FAIL|not been compiled" "$log" || true
exit 1
