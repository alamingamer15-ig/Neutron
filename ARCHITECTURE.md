# Neutron architecture specification

**Status:** Initial design baseline; subject to revision as feasibility is tested.  
**Target:** native ARM64 Linux userspace plus translated x86-64 Linux applications on ARM64 Android.

This document records the current project decisions. A design choice is not a claim that the feature is already implemented or universally possible on Android hardware.

## 1. Goals and constraints

- Host ABI: Android ARM64/AArch64.
- Native userspace ABI: Linux AArch64/ARM64, matching the Android host architecture.
- Translated application ABI: Linux x86-64 only for the initial scope; no x86 32-bit ABI commitment.
- No root requirement.
- Android SELinux remains enforcing.
- One active guest application at a time. That application may create guest threads and, as compatibility is implemented, child processes.
- One shared Neutron runtime and managed root filesystem.
- Progressive compatibility rather than a promise of complete Linux distribution or application support.

Android's Linux kernel is not equivalent to a general-purpose desktop Linux installation. Kernel features, permissions, executable-memory policy, devices, namespaces, and process behavior must be checked on supported Android versions and devices.

## 2. High-level architecture

```text
Android application / UI
        |
Neutron runtime and resource manager
        |
Guest process model, memory, signals, file descriptors
        |
x86-64 ELF loader and guest dynamic-linker support
        |
x86-64 decoder -> intermediate representation (IR)
        |
AArch64 code generator -> translation cache
        |
Native ARM64 execution threads
        |
Android Linux kernel + Android graphics/audio/input APIs
```

The default root filesystem is AArch64/ARM64: its shell, BusyBox utilities, and base userspace run natively on the Android host. Selected x86-64 application ELFs and their x86-64 libraries are translated by Neutron. The root filesystem is not a kernel, CPU translator, or substitute for Android's host kernel.

## 3. Dynamic binary translation

DBT is the initial execution strategy.

### Translation pipeline

1. Load and validate an x86-64 ELF object.
2. Map guest segments into the guest address-space model.
3. Decode x86-64 instructions into basic blocks using **Zydis** (external decoder dependency).
4. Convert Zydis instruction details into Neutron's custom, architecture-neutral IR.
5. Generate AArch64 machine code.
6. Store generated code in the translation cache.
7. Execute translated blocks on native ARM64 threads.

### Translation behavior

- Basic-block translation first, followed by multi-level optimization.
- Hot blocks may be prioritized and upgraded from basic to optimized translations.
- Translation and execution use separate configurable worker pools that share an overall CPU budget.
- Workers should sleep when idle; thermal and battery policies may reduce concurrency.
- Runtime optimization can be disabled by the user.
- Use Zydis for x86-64 instruction decoding; Neutron owns instruction-to-IR lowering, IR semantics, optimization, and AArch64 code generation.
- Pin a stable Zydis release and track its required license notices. Use the decoder API, not Zydis as Neutron's translator or AArch64 code generator.
- Initial native integration pins Zydis v4.1.1 through CMake FetchContent, recursively fetches its Zycore submodule, and builds a JNI-backed ARM64 Android library. The JNI facade exposes one-instruction x86-64 decoding with Intel-syntax formatting; it does not execute instructions or produce IR.
- The Zydis adapter, IR, and code generator must have explicit feature boundaries and tests.

### Cache

- In-memory code cache plus persistent disk cache.
- User-configurable RAM and disk limits.
- Automatic and manual cleanup.
- Cache keys must include relevant translator, guest-feature, and runtime versions.
- Self-modifying code and writable executable mappings require invalidation or guarded recompilation. Exact invalidation mechanics remain an implementation decision.
- Persistent cache entries must be treated as untrusted data and validated before use.

### CPU state and instruction coverage

The guest CPU model must account for general-purpose registers, flags, control flow, and supported floating-point/SIMD state. x87 and SSE support are foundational; additional SIMD extensions are introduced progressively according to tested host support and correct emulation/translation behavior. AVX-family support must not be advertised until state management and instruction semantics are implemented and tested.

## 4. Guest process and memory model

- One active guest application at a time.
- Multiple threads within that application are intended.
- A shared host runtime does **not** mean guest threads share one undifferentiated register set, stack, signal state, or file-descriptor state.
- Guest address spaces, mappings, thread-local state, file descriptors, and signal dispositions need explicit guest-side representations.
- Guest memory limits are user-configurable and enforced through accounting and mapping policy where possible.
- `mmap`, shared mappings, memory-mapped files, executable mappings, and W^X constraints require Android-specific validation.
- Child-process, `fork()`, and `exec()` behavior will be implemented progressively; guest process semantics must not be assumed to map directly to Android process creation.

## 5. Syscall and kernel compatibility

Guest x86-64 syscall conventions are handled by a Neutron syscall-translation layer. The layer adapts syscall numbers, arguments, structures, errors, and guest memory references to supported host operations.

```text
Guest x86-64 syscall ABI
        -> Neutron syscall adapter
        -> guest/host structure and semantic conversion
        -> Android-compatible host operation
        -> translated result and guest-visible error
```

The rootfs provides userspace libraries and compatibility services where needed; it cannot replace or modify Android's kernel syscall implementation.

Compatibility is progressive. Unsupported or unsafe operations must fail clearly rather than being silently misrepresented. Signals, sockets, polling, clocks, futex-like synchronization, memory management, and file operations need dedicated compatibility tests.

## 6. Root filesystem and packages

- Neutron-managed lightweight Linux userspace.
- `apk` is the planned package manager; external repositories are intended.
- The default rootfs contains ARM64 glibc and native ARM64 utilities; its shell runs natively on the host.
- Selected x86-64 application ELFs and their x86-64 libraries use the x86-64 guest ABI and DBT. Do not mix x86-64 guest libraries into the ARM64 base rootfs.
- Guest libc and dynamic-linker strategy remains an explicit design decision. Generic Linux ELF compatibility must not be confused with universal compatibility across glibc- and musl-based environments.
- Writable rootfs with protected system directories.
- Rootfs reset and recovery support.
- Virtual `/dev` rather than unrestricted access to Android device nodes.
- Environment variables and shell profiles.
- Rootfs updates are independent from Neutron engine updates.
- Package installation requires explicit user confirmation.

The rootfs is disk-backed in app-private storage. Files are loaded through normal filesystem and page-cache behavior as needed; the entire rootfs is not intended to be permanently resident in RAM.

## 7. Filesystem and Android storage

- One shared guest filesystem and one shared rootfs.
- App-private storage is the default boundary.
- Android shared folders are exposed only through explicit user selection.
- User-selected files and folders may be mapped into guest paths using Android-granted access.
- No assumption of unrestricted access to shared or external storage.
- Guest file descriptors and permissions must be tracked and translated by the runtime.

## 8. Graphics

### APIs and backends

- Support both desktop OpenGL and Vulkan guest-facing paths.
- Desktop OpenGL is translated to OpenGL ES.
- Vulkan uses the Android Vulkan backend where available; unsupported features may be emulated, substituted, or reported unavailable.
- Rendering architecture is configurable per application.
- Automatic backend selection with a manual override.
- Optional software-rendering fallback.
- Older-application compatibility mode.
- Desktop GL shader translation to GLES.

Feature translation is not guaranteed to be possible. A feature requiring unavailable hardware or kernel functionality must be reported as unsupported rather than claimed to work.

### Display and resource policy

- Per-application fullscreen/windowed selection.
- User-selected guest resolution, independent of Android display resolution where scaling permits.
- Manual resolution management.
- User-configurable graphics-memory budget.
- Automatic frame-rate behavior with an optional custom cap.

Android generally does not expose a universal mechanism for reserving or hard-capping physical GPU memory per application. Neutron's graphics-memory limit is therefore an accounting/budget policy with best-effort enforcement, not a promise of a hardware-enforced VRAM cap.

## 9. Audio

- Initial Android audio backend decision: OpenSL ES, behind an abstraction so another backend can be added if required by Android-version support.
- Linux-facing audio compatibility: PulseAudio protocol compatibility.
- Multiple output devices and streams are intended.
- Configurable audio latency.

The exact PulseAudio-compatible server/bridge design, stream formats, mixing, and device-routing behavior remain to be specified and tested.

## 10. Input

- Touchscreen, keyboard, and mouse.
- Relative mouse input and pointer capture where Android permits.
- Android-native gamepad support.
- Controller button remapping.
- Configurable touchscreen virtual keyboard and mouse controls.

## 11. Networking and isolation

- Linux-compatible guest socket layer translated to Android host networking.
- Per-application network permissions/profile.
- Android network permissions and platform restrictions remain authoritative.
- Guest network behavior must not imply unrestricted access or isolation guarantees that Android does not provide.

## 12. Scheduling, performance, and thermal behavior

- Native Android threads scheduled by the Android/Linux scheduler.
- Separate configurable translation and execution worker pools, sharing a total CPU budget.
- User-selectable worker counts, constrained by available resources and runtime housekeeping needs.
- Hot-code prioritization and optional multi-level optimization.
- Performance, Balanced, and Battery Saver policies.
- Automatic thermal response plus user-configurable policy.
- Worker counts should be reduced under thermal pressure; idle workers should not spin.
- CPU affinity and exact physical-core placement are not guaranteed.

## 13. Runtime interface and diagnostics

- Launch applications from a built-in terminal or graphical launcher.
- Per-application configuration profiles.
- User-configurable background behavior, subject to Android background-execution rules.
- Runtime statistics console.
- Selectable logging levels.
- On guest crash, terminate the guest and preserve diagnostic logs.
- Detailed crash records may include guest register state and the failing guest instruction address.
- Logs can be exported by the user.

## 14. Updates and recovery

- Engine updates are independent from rootfs updates.
- Rootfs reset/recovery is supported as a design goal.
- Persistent translation caches are versioned and safely invalidated when incompatible.
- Update and package operations must preserve integrity and provide clear failure reporting.

## 15. Open engineering decisions

The following require prototypes, tests, or explicit decisions before they can be considered settled:

1. x86-64 guest libc and dynamic-linker strategy (glibc, musl, or a deliberately separated compatibility arrangement); the base ARM64 rootfs uses glibc.
2. Exact IR design, instruction-to-IR lowering rules, and unsupported-instruction behavior.
3. Guest virtual-address layout and safe executable-memory strategy on Android.
4. Complete syscall support matrix and signal/futex semantics.
5. Self-modifying-code detection and translation-cache invalidation.
6. Correct guest `fork()`, `exec()`, and child-process semantics.
7. Vulkan feature negotiation and the boundary between emulation and unsupported features.
8. PulseAudio protocol/server implementation and audio backend lifecycle.
9. Rootfs image format, initial contents, delivery, and update integrity.
10. Minimum Android API level and supported-device test matrix.
11. Native dependency update policy, reproducible dependency pinning, and third-party license inventory.

## 16. Compatibility promise

Neutron is an experimental compatibility project. Support must be documented by tested application, Android version, host GPU, and enabled guest features. No statement of generic Linux compatibility should be interpreted as a guarantee that every Linux binary or distribution will run.
