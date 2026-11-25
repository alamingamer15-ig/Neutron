#!/bin/sh
set -eu
TARGET_DIR="$1"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

mkdir -p "$TARGET_DIR/home" "$TARGET_DIR/dev" "$TARGET_DIR/proc" \
    "$TARGET_DIR/sys" "$TARGET_DIR/tmp" "$TARGET_DIR/run" \
    "$TARGET_DIR/mnt" "$TARGET_DIR/lib64"
chmod 1777 "$TARGET_DIR/tmp"

# Replace Buildroot's stock glibc core with the pinned Android-sandbox-patched
# glibc build. The loader is also packaged separately in the APK's native lib dir.
sh "$SCRIPT_DIR/install-android-glibc.sh" rootfs "$TARGET_DIR"

if [ ! -s "$TARGET_DIR/lib/ld-linux-aarch64.so.1" ]; then
    echo "Android glibc installer did not produce /lib/ld-linux-aarch64.so.1" >&2
    find "$TARGET_DIR" -name 'ld-linux-aarch64.so.1' -print >&2
    exit 1
fi
readelf -h "$TARGET_DIR/lib/ld-linux-aarch64.so.1" | grep -q 'Machine:.*AArch64'
test -x "$TARGET_DIR/bin/busybox"

printf '%s\n' "Neutron AArch64 rootfs with Android-compatible glibc 2.42.9" \
    > "$TARGET_DIR/etc/neutron-rootfs-release"
