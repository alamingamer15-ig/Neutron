# Neutron minimal glibc rootfs

Neutron's default guest filesystem is built with Buildroot, using an AArch64 target,
glibc, and BusyBox. It is intended as a minimal native ARM64 userspace base rather than a full
general-purpose distribution.

## Android-compatible glibc

The default rootfs replaces Buildroot's core AArch64 glibc loader and runtime libraries with the pinned Android-sandbox-patched glibc 2.42.9 build from jjtseng93/glibc-for-android-native. The APK separately packages the matching loader in its extracted native-library directory so Android can execute it from the app installation location. The rootfs installer requires the Android-glibc release marker and automatically replaces older unpatched default rootfs installations.

## Contents

- AArch64 glibc runtime and ELF dynamic loader
- BusyBox /bin/sh and basic Unix utilities
- /bin, /lib, /lib64, /home, /dev, /proc, /sys, /tmp, /run, /mnt
- /etc/neutron-rootfs-release marker

This is a userspace root filesystem, not a Linux kernel. Neutron still needs its ELF
loader, AArch64 execution engine, syscall layer, process model, and filesystem setup
before guest programs can run.

## Rebuild

The GitHub Actions workflow "Build minimal glibc rootfs" builds from the pinned
Buildroot 2026.08.2 release and commits the generated archive and SHA-256 file
directly to main. The Android app downloads both from this repository, verifies the
checksum, and extracts the archive into app-private storage. The archive is not
bundled into the APK.

Buildroot and included packages retain their upstream licenses. Include the generated
Buildroot license manifest when redistributing release packages.
