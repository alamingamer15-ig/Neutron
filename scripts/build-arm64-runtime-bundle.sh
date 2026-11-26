#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ASSET_DIR="$ROOT/app/build/generated/neutronAssets"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT HUP INT TERM
DEST="$STAGE/runtime"
mkdir -p "$DEST/glibc/etc" "$DEST/usr/share/neutron"

# Ubuntu 24.04 arm64 packages are used because they are glibc-linked and
# compatible with Neutron's pinned patched glibc runtime.
sudo dpkg --add-architecture arm64

# The runner's default Ubuntu mirrors do not publish arm64 indexes. Keep those
# sources amd64-only and use ports.ubuntu.com for the arm64 package set below.
if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then
    sudo sed -i '/^Architectures:/d; /^URIs:/a Architectures: amd64' \
        /etc/apt/sources.list.d/ubuntu.sources
fi
if [ -f /etc/apt/sources.list ]; then
    sudo sed -i 's/^deb /deb [arch=amd64] /' /etc/apt/sources.list
fi

sudo tee /etc/apt/sources.list.d/neutron-arm64.sources >/dev/null <<'EOF'
Types: deb
URIs: http://ports.ubuntu.com/ubuntu-ports
Suites: noble noble-updates noble-security
Components: main universe
Architectures: arm64
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
EOF
sudo apt-get update
sudo apt-get install -y --no-install-recommends dpkg-dev binutils
sudo apt-get install -y --download-only --no-install-recommends \
    libwayland-client0:arm64 \
    libwayland-server0:arm64 \
    libwayland-cursor0:arm64 \
    libwayland-egl1:arm64 \
    libx11-6:arm64 \
    libx11-xcb1:arm64 \
    libxext6:arm64 \
    libxrender1:arm64 \
    libxfixes3:arm64 \
    libxrandr2:arm64 \
    libxinerama1:arm64 \
    libxcursor1:arm64 \
    libxi6:arm64 \
    libxcomposite1:arm64 \
    libxdamage1:arm64 \
    libxkbcommon0:arm64 \
    libxkbcommon-x11-0:arm64 \
    libxcb1:arm64 \
    libxcb-render0:arm64 \
    libxcb-shm0:arm64 \
    libxcb-xfixes0:arm64 \
    libxcb-randr0:arm64 \
    libxcb-shape0:arm64 \
    libxcb-xinerama0:arm64 \
    libxcb-xkb1:arm64 \
    libxau6:arm64 \
    libxdmcp6:arm64

# Install the pinned Android-sandbox-compatible glibc loader and core DSOs.
sh "$ROOT/scripts/install-android-glibc.sh" rootfs "$DEST/glibc"

# Extract the resolved arm64 package dependency closure. The libc6 package is
# intentionally omitted: Neutron supplies its pinned patched glibc instead.
mkdir -p "$STAGE/debs"
find /var/cache/apt/archives -maxdepth 1 -type f -name '*.deb' -exec cp {} "$STAGE/debs/" \;
PACKAGE_LIST="$DEST/usr/share/neutron/runtime-packages.txt"
: > "$PACKAGE_LIST"
for package in "$STAGE"/debs/*.deb; do
    [ -f "$package" ] || continue
    architecture="$(dpkg-deb -f "$package" Architecture)"
    [ "$architecture" = arm64 ] || [ "$architecture" = all ] || continue
    name="$(dpkg-deb -f "$package" Package)"
    version="$(dpkg-deb -f "$package" Version)"
    if [ "$name" = libc6 ] || [ "$name" = libc-bin ]; then
        continue
    fi
    printf '%s %s %s\n' "$name" "$version" "$architecture" >> "$PACKAGE_LIST"
    dpkg-deb -x "$package" "$DEST"
done

grep -q '^libwayland-client0 ' "$PACKAGE_LIST"
grep -q '^libwayland-server0 ' "$PACKAGE_LIST"
grep -q '^libx11-6 ' "$PACKAGE_LIST"

# Confirm the SONAME links needed by the dynamic loader survived extraction.
test -e "$DEST/usr/lib/aarch64-linux-gnu/libwayland-client.so.0"
test -e "$DEST/usr/lib/aarch64-linux-gnu/libX11.so.6"

# Keep Debian copyright files in the bundle for license compliance.
mkdir -p "$ASSET_DIR"
tar --hard-dereference -czf "$ASSET_DIR/neutron-runtime-arm64.tar.gz" -C "$DEST" glibc usr
tar -tzf "$ASSET_DIR/neutron-runtime-arm64.tar.gz" >/dev/null
echo "Created Neutron ARM64 glibc + Wayland + X11 runtime bundle"
du -h "$ASSET_DIR/neutron-runtime-arm64.tar.gz"
