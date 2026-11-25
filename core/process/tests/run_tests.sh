#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
OUT="$(mktemp "${TMPDIR:-/tmp}/neutron-process-stack-tests.XXXXXX")"
trap 'rm -f "$OUT"' EXIT HUP INT TERM

"${CXX:-c++}" \
    -std=c++17 \
    -Wall -Wextra -Werror \
    -pthread \
    -I"$ROOT/core/process/include" \
    -I"$ROOT/core/elf/include" \
    -I"$ROOT/core/memory/include" \
    "$ROOT/core/process/src/initial_stack.cpp" \
    "$ROOT/core/memory/src/guest_memory.cpp" \
    "$ROOT/core/process/tests/initial_stack_tests.cpp" \
    -o "$OUT"

"$OUT"
printf '%s\n' "Initial process stack tests passed."
