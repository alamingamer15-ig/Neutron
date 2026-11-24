# Neutron native core

This directory contains Neutron's Android-native runtime components.

## Current milestone: Zydis decoder adapter

- Zydis v4.1.1 is fetched and pinned by CMake.
- Zycore is fetched recursively as Zydis' required submodule.
- The build currently targets Android arm64-v8a.
- The JNI facade exposes one-instruction x86-64 decoding to Java.
- The decoder returns Intel-syntax disassembly. It does not execute the instruction or generate Neutron IR yet.

Builds are driven by the Android Gradle Plugin through the NDK and CMake. Do not build this native library for the x86-64 guest ABI: it runs on the ARM64 Android host and decodes guest x86-64 bytes.
