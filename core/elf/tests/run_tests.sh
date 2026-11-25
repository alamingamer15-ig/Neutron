#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
OUT="$(mktemp "${TMPDIR:-/tmp}/neutron-elf-loader-tests.XXXXXX")"
trap 'rm -f "$OUT"' EXIT HUP INT TERM

"${CXX:-c++}" \
    -std=c++17 \
    -Wall -Wextra -Werror \
    -pthread \
    -I"$ROOT/core/elf/include" \
    -I"$ROOT/core/memory/include" \
    "$ROOT/core/elf/src/elf_parser.cpp" \
    "$ROOT/core/elf/src/elf_validator.cpp" \
    "$ROOT/core/elf/src/elf_loader.cpp" \
    "$ROOT/core/memory/src/guest_memory.cpp" \
    "$ROOT/core/elf/tests/elf_loader_tests.cpp" \
    -o "$OUT"

"$OUT"
printf '%s\n' "ELF loader integration tests passed."
