#!/bin/sh
set -eu

MODE="${1:?usage: install-android-glibc.sh apk <jniLibs-dir> | rootfs <target-dir>}"
DEST="${2:?destination is required}"
UPSTREAM_COMMIT="d7e375f9145b2041f479416eccfc924fd3bde3d6"
BASE_URL="https://raw.githubusercontent.com/jjtseng93/glibc-for-android-native/${UPSTREAM_COMMIT}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT HUP INT TERM

fetch_verified() {
    name="$1"
    expected_blob="$2"
    output="$3"
    url="${BASE_URL}/${name}"

    curl --fail --location --retry 3 --silent --show-error "$url" -o "$output"
    actual_blob="$(git hash-object "$output")"
    if [ "$actual_blob" != "$expected_blob" ]; then
        echo "glibc artifact integrity check failed: $name" >&2
        echo "expected Git blob: $expected_blob" >&2
        echo "actual Git blob:   $actual_blob" >&2
        exit 1
    fi
    if ! readelf -h "$output" | grep -q 'Machine:.*AArch64'; then
        echo "glibc artifact is not an AArch64 ELF: $name" >&2
        exit 1
    fi
}

fetch_verified ld.so 68e68660d5b137c6ac2d76a253740fe74cccef2b "$WORK/ld.so"

case "$MODE" in
    apk)
        mkdir -p "$DEST"
        install -m 0755 "$WORK/ld.so" "$DEST/libldcustom.so"
        echo "Installed pinned Android-compatible AArch64 loader: $DEST/libldcustom.so"
        ;;
    rootfs)
        mkdir -p "$DEST/lib" "$DEST/lib64"
        fetch_verified libc.so.6 9b2aafea50d220b3512c6d38074627bc01d8c08d "$WORK/libc.so.6"
        fetch_verified libdl.so.2 020692fd584c95ac53dcb9353df5f5dc461bfa54 "$WORK/libdl.so.2"
        fetch_verified libm.so.6 0478a2614ea8b07490b8bc0fcf5c6690ec5ca3e4 "$WORK/libm.so.6"
        fetch_verified libpthread.so.0 4cd8180b3893c5c0c921ec99f86c44d612c8c335 "$WORK/libpthread.so.0"

        install -m 0755 "$WORK/ld.so" "$DEST/lib/ld-linux-aarch64.so.1"
        for library in libc.so.6 libdl.so.2 libm.so.6 libpthread.so.0; do
            rm -f "$DEST/lib/$library"
            install -m 0755 "$WORK/$library" "$DEST/lib/$library"
        done
        # Some AArch64 rootfs layouts make /lib64 a symlink to /lib.
        # Creating /lib64/ld-linux here would then replace the real loader in
        # /lib with a self-referential symlink. The /lib loader is sufficient
        # in that layout; only add the compatibility alias for a real directory.
        if [ ! -L "$DEST/lib64" ]; then
            ln -sfn ../lib/ld-linux-aarch64.so.1 "$DEST/lib64/ld-linux-aarch64.so.1"
        fi
        printf '%s\n' \
            "glibc 2.42.9 Android sandbox patch set" \
            "Upstream: https://github.com/jjtseng93/glibc-for-android-native" \
            "Pinned commit: $UPSTREAM_COMMIT" \
            "Patched set_robust_list and rseq handling; see THIRD_PARTY_NOTICES.md" \
            > "$DEST/etc/neutron-android-glibc-release"
        echo "Installed verified patched glibc 2.42.9 runtime into $DEST"
        ;;
    *)
        echo "unknown mode: $MODE" >&2
        exit 2
        ;;
esac
