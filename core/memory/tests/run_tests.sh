#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
OUT="$(mktemp "\${TMPDIR:-/tmp}/neutron-memory-tests.XXXXXX")"
trap 'rm -f "$OUT"' EXIT HUP INT TERM

"\${CXX:-c++}" \
    -std=c++17 \
    -Wall -Wextra -Werror \
    -pthread \
    -I"$ROOT/core/memory/include" \
    "$ROOT/core/memory/src/guest_memory.cpp" \
    "$ROOT/core/memory/tests/guest_memory_tests.cpp" \
    -o "$OUT"

"$OUT"
printf '%s\n' "Guest memory tests passed."
