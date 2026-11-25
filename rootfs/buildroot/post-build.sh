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

printf '%s\n' "Neutron AArch64 rootfs with Android-compatible glibc 2.42.9" \
    > "$TARGET_DIR/etc/neutron-rootfs-release"
