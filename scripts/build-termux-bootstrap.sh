#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="$(mktemp -d)"
TERMUX_COMMIT="8eb3169dbd10d71ef26e2847191f9ae2a452ab2e"
TERMUX_REPO="$WORK/termux-packages"
ASSET_DIR="$ROOT/app/build/generated/neutronAssets"
trap 'rm -rf "$WORK"' EXIT HUP INT TERM

mkdir -p "$TERMUX_REPO"
git -C "$TERMUX_REPO" init
git -C "$TERMUX_REPO" remote add origin https://github.com/termux/termux-packages.git
git -C "$TERMUX_REPO" fetch --depth=1 origin "$TERMUX_COMMIT"
git -C "$TERMUX_REPO" checkout --detach FETCH_HEAD

PROPERTIES="$TERMUX_REPO/scripts/properties.sh"
# Build Termux's bootstrap as a fork for Neutron, not for com.termux.
# The non-usr-merged prefix is files/neutron and HOME is files/neutron/home.
sed -i \
  -e 's/^TERMUX_APP__PACKAGE_NAME="com.termux"$/TERMUX_APP__PACKAGE_NAME="io.github.alamingamer15ig.neutron"/' \
  -e 's/^TERMUX__HOME_SUBDIR="home"$/TERMUX__HOME_SUBDIR="neutron\/home"/' \
  -e 's/^TERMUX__PREFIX_SUBDIR="usr"$/TERMUX__PREFIX_SUBDIR="neutron"/' \
  -e 's/^__TERMUX_BUILD_PROPS__VALIDATE_TERMUX_PREFIX_USR_MERGE_FORMAT="true"$/__TERMUX_BUILD_PROPS__VALIDATE_TERMUX_PREFIX_USR_MERGE_FORMAT="false"/' \
  "$PROPERTIES"

grep -Fq 'TERMUX_APP__PACKAGE_NAME="io.github.alamingamer15ig.neutron"' "$PROPERTIES"
grep -Fq 'TERMUX__HOME_SUBDIR="neutron/home"' "$PROPERTIES"
grep -Fq 'TERMUX__PREFIX_SUBDIR="neutron"' "$PROPERTIES"
grep -Fq '__TERMUX_BUILD_PROPS__VALIDATE_TERMUX_PREFIX_USR_MERGE_FORMAT="false"' "$PROPERTIES"

cd "$TERMUX_REPO"
# This pinned Termux revision removed the standalone bzip2 package, but its
# upstream bootstrap list still names it. Omit that obsolete entry.
sed -i '/^\s*PACKAGES+=("bzip2")$/d' scripts/build-bootstraps.sh

# dialog is an optional interactive UI used by a few Termux helper commands.
# Its upstream archive mirror currently returns bytes that fail the pinned
# SHA-256 check. It is not needed by Neutron's pkg/apt/dpkg bootstrap, so keep
# the core package-manager bootstrap reproducible without building dialog.
sed -i 's/, dialog"$/"/' packages/termux-tools/build.sh
grep -Fq 'TERMUX_PKG_DEPENDS=' packages/termux-tools/build.sh
if grep -E '^TERMUX_PKG_DEPENDS=.*dialog' packages/termux-tools/build.sh; then
  echo "dialog must remain optional in the Neutron bootstrap" >&2
  exit 1
fi

# Termux's own build container compiles the bootstrap packages against Neutron's
# Android application id and private PREFIX, so no com.termux paths are baked in.
./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures aarch64 --add curl,wget,ca-certificates,unzip

BOOTSTRAP="$TERMUX_REPO/bootstrap-aarch64.zip"
test -s "$BOOTSTRAP"
unzip -Z1 "$BOOTSTRAP" | grep -Fxq 'SYMLINKS.txt'
unzip -Z1 "$BOOTSTRAP" | grep -Fxq 'bin/bash'
unzip -Z1 "$BOOTSTRAP" | grep -Fxq 'bin/pkg'
unzip -Z1 "$BOOTSTRAP" | grep -Fxq 'var/lib/dpkg/status'

mkdir -p "$ASSET_DIR"
install -m 0644 "$BOOTSTRAP" "$ASSET_DIR/neutron-bootstrap-arm64.zip"
sha256sum "$ASSET_DIR/neutron-bootstrap-arm64.zip"
du -h "$ASSET_DIR/neutron-bootstrap-arm64.zip"
