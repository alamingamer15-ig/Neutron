# Neutron

**Neutron** is an experimental compatibility layer for running x86-64 Linux applications on ARM64 Android devices.

The project explores a Proton-inspired approach for Android: translate guest x86-64 code to native AArch64 code, provide a Linux userspace environment, and bridge guest application interfaces to Android's Linux kernel and graphics/audio/input systems.

> **Status: early planning / pre-implementation.** Neutron is not yet a working compatibility layer. Compatibility, performance, and hardware support have not been demonstrated.

## Project goals

- Run selected x86-64 Linux ELF applications on ARM64 Android.
- Work without root and keep Android SELinux enforcing.
- Use dynamic binary translation (DBT) with an AArch64 code generator.
- Provide a lightweight, app-managed Linux root filesystem.
- Integrate graphics, audio, input, filesystem, and networking with Android.
- Make resource limits and performance/thermal behavior configurable.

## Initial scope

- **Host:** Android on ARM64/AArch64.
- **Guest:** Linux x86-64 userspace and ELF applications.
- **Execution:** One active guest application at a time; guest multithreading is intended.
- **Runtime:** A shared Neutron runtime process with isolated guest execution state.
- **Rootfs:** A Neutron-managed userspace with the `apk` package manager planned.
- **Privileges:** No root requirement; no disabling SELinux.

Neutron aims for progressive compatibility. It does not promise that every Linux application, syscall, graphics feature, or distribution will work.

## Documentation

- [Architecture specification](ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [License](LICENSE)

## Current development state

The repository currently documents the intended architecture. Implementation, supported-feature matrices, tests, benchmarks, and release builds will be added as the project develops.

## Naming

The name **Neutron** is a nod to **Proton**: a compatibility-layer concept adapted to the Android environment.
