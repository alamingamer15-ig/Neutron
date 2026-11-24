#!/bin/sh
set -eu
TARGET_DIR="$1"
mkdir -p "$TARGET_DIR/home" "$TARGET_DIR/dev" "$TARGET_DIR/proc" "$TARGET_DIR/sys" "$TARGET_DIR/tmp" "$TARGET_DIR/run" "$TARGET_DIR/mnt" "$TARGET_DIR/lib64"
chmod 1777 "$TARGET_DIR/tmp"
if [ ! -e "$TARGET_DIR/lib64/ld-linux-x86-64.so.2" ]; then
    if [ -e "$TARGET_DIR/lib/ld-linux-x86-64.so.2" ]; then
        ln -s ../lib/ld-linux-x86-64.so.2 "$TARGET_DIR/lib64/ld-linux-x86-64.so.2"
    else
        echo "glibc x86-64 dynamic loader was not produced" >&2
        exit 1
    fi
fi
printf '%s\n' "Neutron Buildroot x86-64 glibc rootfs" > "$TARGET_DIR/etc/neutron-rootfs-release"
