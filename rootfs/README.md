# Neutron minimal glibc rootfs

Neutron's default guest filesystem is built with Buildroot, using an x86-64 target,
glibc, and BusyBox. It is intended as a minimal userspace base rather than a full
general-purpose distribution.

## Contents

- x86-64 glibc runtime and ELF dynamic loader
- BusyBox /bin/sh and basic Unix utilities
- /bin, /lib, /lib64, /home, /dev, /proc, /sys, /tmp, /run, /mnt
- /etc/neutron-rootfs-release marker

This is a userspace root filesystem, not a Linux kernel. Neutron still needs its ELF
loader, x86-64 execution engine, syscall layer, process model, and filesystem setup
before guest programs can run.

## Rebuild

The GitHub Actions workflow "Build minimal glibc rootfs" builds from the pinned
Buildroot 2026.08.2 release and commits the generated archive and SHA-256 file
directly to main. The Android app downloads both from this repository, verifies the
checksum, and extracts the archive into app-private storage. The archive is not
bundled into the APK.

Buildroot and included packages retain their upstream licenses. Include the generated
Buildroot license manifest when redistributing release packages.
