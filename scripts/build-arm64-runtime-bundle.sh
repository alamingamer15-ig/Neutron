#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ASSET_DIR="$ROOT/app/build/generated/neutronAssets"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT HUP INT TERM
DEST="$STAGE/runtime"
BOOT="$STAGE/bootstrap"
mkdir -p "$DEST/glibc/etc" "$DEST/usr/share/neutron" "$BOOT"

# Neutron's userspace is ARM64 glibc, not Termux/Bionic. Resolve one Ubuntu
# ARM64 package closure for both the graphics runtime and base bootstrap.
sudo dpkg --add-architecture arm64
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
# Resolve packages against an empty ARM64 dpkg database. The CI host is
# amd64 and already has amd64 versions of these tools installed; using its
# normal status file makes dpkg's Multi-Arch: same rules reject arm64 packages.
: > "$STAGE/empty-dpkg-status"
cat > "$STAGE/apt-arm64.conf" <<EOF
APT::Architecture "arm64";
APT::Architectures { "arm64"; };
Dir::State::status "$STAGE/empty-dpkg-status";
Dir::Cache::archives "/var/cache/apt/archives";
EOF
sudo env APT_CONFIG="$STAGE/apt-arm64.conf" apt-get install -y --download-only --no-install-recommends \
    bash:arm64 dash:arm64 coreutils:arm64 tar:arm64 gzip:arm64 xz-utils:arm64 \
    findutils:arm64 grep:arm64 sed:arm64 gawk:arm64 \
    dpkg:arm64 apt:arm64 ca-certificates:arm64 curl:arm64 wget:arm64 ubuntu-keyring:arm64 \
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

sh "$ROOT/scripts/install-android-glibc.sh" runtime "$DEST/glibc"

mkdir -p "$STAGE/debs" "$DEST/var/lib/dpkg" "$DEST/var/cache/apt/archives" \
    "$DEST/var/log/apt" "$DEST/etc/apt" "$DEST/bin"
find /var/cache/apt/archives -maxdepth 1 -type f -name '*.deb' -exec cp {} "$STAGE/debs/" \;
PACKAGE_LIST="$DEST/usr/share/neutron/runtime-packages.txt"
BOOTSTRAP_LIST="$DEST/usr/share/neutron/bootstrap-packages.txt"
: > "$PACKAGE_LIST"
: > "$BOOTSTRAP_LIST"
: > "$DEST/var/lib/dpkg/status"
for package in "$STAGE"/debs/*.deb; do
    [ -f "$package" ] || continue
    architecture="$(dpkg-deb -f "$package" Architecture)"
    [ "$architecture" = arm64 ] || [ "$architecture" = all ] || continue
    name="$(dpkg-deb -f "$package" Package)"
    version="$(dpkg-deb -f "$package" Version)"
    if [ "$name" = libc6 ] || [ "$name" = libc-bin ]; then
        # Record the host-provided patched glibc as installed without unpacking
        # Ubuntu's libc over Neutron's Android-compatible runtime.
        dpkg-deb -f "$package" >> "$DEST/var/lib/dpkg/status"
        printf 'Status: install ok installed\n\n' >> "$DEST/var/lib/dpkg/status"
        continue
    fi
    printf '%s %s %s\n' "$name" "$version" "$architecture" >> "$PACKAGE_LIST"
    dpkg-deb -x "$package" "$DEST"
    # Seed dpkg's database for the packages included in the offline bootstrap.
    dpkg-deb -f "$package" >> "$DEST/var/lib/dpkg/status"
    printf 'Status: install ok installed\n\n' >> "$DEST/var/lib/dpkg/status"
done

for required in bash coreutils tar gzip findutils grep sed gawk dpkg apt; do
    grep -q "^$required " "$PACKAGE_LIST"
    printf '%s\n' "$required" >> "$BOOTSTRAP_LIST"
done
printf '%s\n' curl wget xz-utils ubuntu-keyring >> "$BOOTSTRAP_LIST"
grep -q '^libwayland-client0 ' "$PACKAGE_LIST"
grep -q '^libwayland-server0 ' "$PACKAGE_LIST"
grep -q '^libx11-6 ' "$PACKAGE_LIST"
test -e "$DEST/usr/lib/aarch64-linux-gnu/libwayland-client.so.0"
test -e "$DEST/usr/lib/aarch64-linux-gnu/libX11.so.6"
test -x "$DEST/usr/bin/bash"
test -x "$DEST/usr/bin/apt-get"
test -x "$DEST/usr/bin/dpkg"
test -x "$DEST/usr/bin/ls"
test -x "$DEST/usr/bin/tar"

cat > "$DEST/etc/apt/sources.list" <<'EOF'
deb [signed-by=/usr/share/keyrings/ubuntu-archive-keyring.gpg] http://ports.ubuntu.com/ubuntu-ports noble main universe
deb [signed-by=/usr/share/keyrings/ubuntu-archive-keyring.gpg] http://ports.ubuntu.com/ubuntu-ports noble-updates main universe
deb [signed-by=/usr/share/keyrings/ubuntu-archive-keyring.gpg] http://ports.ubuntu.com/ubuntu-ports noble-security main universe
EOF
cat > "$DEST/etc/apt/apt.conf" <<'EOF'
Dir "/";
APT::Architecture "arm64";
APT::Architectures { "arm64"; };
Acquire::Languages "none";
Acquire::Retries "3";
EOF

cat > "$DEST/bin/neutron-exec" <<'EOF'
#!/system/bin/sh
set -eu
: "${PREFIX:?Neutron PREFIX is not set}"
LOADER="$PREFIX/glibc/lib/ld-linux-aarch64.so.1"
LIBPATH="$PREFIX/glibc/lib:$PREFIX/usr/lib/aarch64-linux-gnu:$PREFIX/usr/lib:$PREFIX/lib:$PREFIX/lib64"
if [ "$#" -lt 1 ]; then
    echo "usage: neutron-exec PROGRAM [ARGS...]" >&2
    exit 2
fi
PROGRAM="$1"
shift
case "$PROGRAM" in
    /*) PROGRAM="$PREFIX$PROGRAM" ;;
    */*) PROGRAM="$PREFIX/$PROGRAM" ;;
    *) PROGRAM="$PREFIX/usr/bin/$PROGRAM" ;;
esac
exec "$LOADER" --library-path "$LIBPATH" "$PROGRAM" "$@"
EOF
chmod 0755 "$DEST/bin/neutron-exec"

for command in bash apt apt-get dpkg ls cat cp mv rm mkdir rmdir chmod chown ln readlink \
    pwd env printenv uname id whoami date sleep touch mkdir find xargs grep sed awk \
    tar gzip xz curl wget; do
    [ -e "$DEST/usr/bin/$command" ] || [ -L "$DEST/usr/bin/$command" ] || continue
    cat > "$DEST/bin/$command" <<EOF
#!/system/bin/sh
exec "\$PREFIX/bin/neutron-exec" "$command" "\$@"
EOF
    chmod 0755 "$DEST/bin/$command"
done

# apt/dpkg must operate inside Neutron's private prefix, never Android's root.
cat > "$DEST/bin/apt-get" <<'EOF'
#!/system/bin/sh
: "${PREFIX:?}"
export APT_CONFIG="$PREFIX/etc/apt/apt.conf"
exec "$PREFIX/bin/neutron-exec" apt-get -o "Dir::Root=$PREFIX" \
    -o "Dir::State::status=$PREFIX/var/lib/dpkg/status" \
    -o "Dir::State::lists=$PREFIX/var/lib/apt/lists" \
    -o "Dir::Cache=$PREFIX/var/cache/apt" \
    -o "Dir::Etc::sourcelist=$PREFIX/etc/apt/sources.list" \
    -o "Dir::Etc::sourceparts=-" \
    -o "Dir::Bin::dpkg=$PREFIX/bin/dpkg" \
    -o "Dir::Bin::Methods=$PREFIX/usr/lib/apt/methods" "$@"
EOF
cat > "$DEST/bin/apt" <<'EOF'
#!/system/bin/sh
: "${PREFIX:?}"
export APT_CONFIG="$PREFIX/etc/apt/apt.conf"
exec "$PREFIX/bin/neutron-exec" apt -o "Dir::Root=$PREFIX" \
    -o "Dir::State::status=$PREFIX/var/lib/dpkg/status" \
    -o "Dir::State::lists=$PREFIX/var/lib/apt/lists" \
    -o "Dir::Cache=$PREFIX/var/cache/apt" \
    -o "Dir::Etc::sourcelist=$PREFIX/etc/apt/sources.list" \
    -o "Dir::Etc::sourceparts=-" \
    -o "Dir::Bin::dpkg=$PREFIX/bin/dpkg" \
    -o "Dir::Bin::Methods=$PREFIX/usr/lib/apt/methods" "$@"
EOF
cat > "$DEST/bin/apt-cache" <<'EOF'
#!/system/bin/sh
: "${PREFIX:?}"
export APT_CONFIG="$PREFIX/etc/apt/apt.conf"
exec "$PREFIX/bin/neutron-exec" apt-cache -o "Dir::Root=$PREFIX" \
    -o "Dir::State::status=$PREFIX/var/lib/dpkg/status" \
    -o "Dir::State::lists=$PREFIX/var/lib/apt/lists" \
    -o "Dir::Cache=$PREFIX/var/cache/apt" \
    -o "Dir::Etc::sourcelist=$PREFIX/etc/apt/sources.list" \
    -o "Dir::Etc::sourceparts=-" \
    -o "Dir::Bin::Methods=$PREFIX/usr/lib/apt/methods" "$@"
EOF
cat > "$DEST/bin/dpkg" <<'EOF'
#!/system/bin/sh
exec "$PREFIX/bin/neutron-exec" dpkg --root="$PREFIX" \
    --admindir="$PREFIX/var/lib/dpkg" "$@"
EOF
cat > "$DEST/bin/pkg" <<'EOF'
#!/system/bin/sh
set -eu
command="${1:-help}"
[ "$#" -eq 0 ] || shift
case "$command" in
    update) exec "$PREFIX/bin/apt-get" update "$@" ;;
    upgrade) exec "$PREFIX/bin/apt-get" upgrade "$@" ;;
    install) exec "$PREFIX/bin/apt-get" install "$@" ;;
    remove|uninstall) exec "$PREFIX/bin/apt-get" remove "$@" ;;
    search) exec "$PREFIX/bin/apt-cache" search "$@" ;;
    show) exec "$PREFIX/bin/apt-cache" show "$@" ;;
    list) exec "$PREFIX/bin/apt" list "$@" ;;
    help|-h|--help)
        echo "Neutron pkg: update | upgrade | install PKG... | remove PKG... | search TERM | show PKG | list"
        ;;
    *) echo "pkg: unknown command: $command" >&2; exit 2 ;;
esac
EOF
chmod 0755 "$DEST/bin/apt-get" "$DEST/bin/apt" "$DEST/bin/apt-cache" "$DEST/bin/dpkg" "$DEST/bin/pkg"

mkdir -p "$ASSET_DIR"
tar --hard-dereference -czf "$ASSET_DIR/neutron-runtime-arm64.tar.gz" \
    -C "$DEST" glibc usr
tar -tzf "$ASSET_DIR/neutron-runtime-arm64.tar.gz" >/dev/null
tar --hard-dereference -czf "$ASSET_DIR/neutron-bootstrap-arm64.tar.gz" \
    -C "$DEST" bin etc usr var
tar -tzf "$ASSET_DIR/neutron-bootstrap-arm64.tar.gz" >/dev/null
echo "Created Neutron ARM64 glibc + Wayland + X11 runtime bundle"
echo "Created Neutron ARM64 bootstrap (bash, coreutils, apt, dpkg, pkg, tar, gzip)"
du -h "$ASSET_DIR/neutron-runtime-arm64.tar.gz" "$ASSET_DIR/neutron-bootstrap-arm64.tar.gz"
