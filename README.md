# Neutron

**Neutron** is an experimental compatibility layer for running x86-64 Linux applications on ARM64 Android devices.

The project explores a Proton-inspired approach for Android: translate guest x86-64 code to native AArch64 code, provide a Linux userspace environment, and bridge guest application interfaces to Android's Linux kernel and graphics/audio/input systems.

> **Status: early prototype.** The Android UI now has a persistent PTY-backed host shell and an offline installer for a pinned ARM64 glibc runtime plus Wayland/X11 runtime libraries and diagnostic tools. The x86-64 translator, Linux syscall compatibility layer, Android-backed desktop display server, and guest execution pipeline are not implemented yet.

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

## Android application

The initial Android application uses Java, Android Views/XML, AndroidX AppCompat, and Material Components.

- Application ID: `io.github.alamingamer15ig.neutron`
- Minimum Android version: Android 8.0 (API 26)
- Default appearance: dark
- Main sections: Home, Applications, Terminal, and Settings
- The Applications screen can record selected file names locally. It does not yet validate, copy, load, or execute ELF binaries.
- The Terminal screen runs Android's `/system/bin/sh` in a persistent PTY. It is a host shell, not the guest Linux shell.
- Settings can install the pinned ARM64 glibc runtime and ARM64 Wayland/X11 libraries and diagnostic tools into Neutron's private prefix.
- The current graphics bundle contains client/server libraries and tools, not a compositor or X server connected to Android. Graphical Linux applications cannot display windows yet.
- Runtime status and resource information are not connected to a native guest execution engine.

### Build

Open the repository in Android Studio and sync the Gradle project. Use JDK 17 and Android SDK platform 35.

With Gradle 8.9 installed, a debug APK can be built with:

```sh
gradle assembleDebug
```

The APK output is normally:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Documentation

- [Architecture specification](ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [License](LICENSE)

## Naming

The name **Neutron** is a nod to **Proton**: a compatibility-layer concept adapted to the Android environment.
