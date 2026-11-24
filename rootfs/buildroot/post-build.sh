#!/bin/sh
set -eu
TARGET_DIR="$1"
mkdir -p "$TARGET_DIR/home" "$TARGET_DIR/dev" "$TARGET_DIR/proc" "$TARGET_DIR/sys" "$TARGET_DIR/tmp" "$TARGET_DIR/run" "$TARGET_DIR/mnt" "$TARGET_DIR/lib64"
chmod 1777 "$TARGET_DIR/tmp"
if [ ! -e "$TARGET_DIR/lib64/ld-linux-aarch64.so.1" ]; then
    if [ -e "$TARGET_DIR/lib/ld-linux-aarch64.so.1" ]; then
        ln -s ../lib/ld-linux-aarch64.so.1 "$TARGET_DIR/lib64/ld-linux-aarch64.so.1"
    else
        echo "glibc AArch64 dynamic loader was not produced" >&2
        exit 1
    fi
fi
printf '%s\n' "Neutron Buildroot AArch64 glibc rootfs" > "$TARGET_DIR/etc/neutron-rootfs-release"
