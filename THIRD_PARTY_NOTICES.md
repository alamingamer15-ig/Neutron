# Third-party notices

Neutron uses the following third-party native libraries. Their source is fetched at build time by CMake; these notices are retained for source and binary redistribution.

## Zydis v4.1.1

Source: https://github.com/zyantific/zydis  
License: MIT

The MIT License (MIT)

Copyright (c) 2014-2024 Florian Bernd
Copyright (c) 2014-2024 Joel Höner

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Zycore v1.5.0

Source: https://github.com/zyantific/zycore-c  
License: MIT

The MIT License (MIT)

Copyright (c) 2018-2024 Florian Bernd
Copyright (c) 2018-2024 Joel Höner

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
\n\n## Apache Commons Compress\n\nNeutron uses Apache Commons Compress 1.27.1 (Apache License 2.0) to safely read Neutron's compressed ARM64 runtime bundle.\n
## Ubuntu 24.04 ARM64 Wayland and X11 runtime packages

The offline runtime bundle includes selected ARM64 binary packages from the official Ubuntu 24.04 repositories for Wayland and X11 runtime libraries. The exact package names, versions, and architectures are recorded in `usr/share/neutron/runtime-packages.txt` inside the installed prefix. Package copyright and license files shipped under `/usr/share/doc` are retained in the bundle under the Neutron prefix. Each package remains subject to its own upstream license.

## Android-compatible GNU C Library (glibc) 2.42.9

Neutron packages the AArch64 dynamic loader and glibc runtime libraries from
[jjtseng93/glibc-for-android-native](https://github.com/jjtseng93/glibc-for-android-native),
pinned to commit `d7e375f9145b2041f479416eccfc924fd3bde3d6`. This is a modified
GNU C Library build intended to avoid Android app-sandbox SIGSYS failures. Its
documented changes adjust `set_robust_list` and `rseq` handling and patch the
resolver configuration path. The exact source and patch notes are available in
the pinned upstream repository: [glibc source archive](https://github.com/jjtseng93/glibc-for-android-native/blob/d7e375f9145b2041f479416eccfc924fd3bde3d6/glibc-2.42.9-revised.tar.gz),
[SOURCE_INFO](https://github.com/jjtseng93/glibc-for-android-native/blob/d7e375f9145b2041f479416eccfc924fd3bde3d6/SOURCE_INFO),
and [LGPL-2.1 license](https://github.com/jjtseng93/glibc-for-android-native/blob/d7e375f9145b2041f479416eccfc924fd3bde3d6/LICENSE). A copy of the license and the upstream patch notes are also retained under `licenses/` in this repository.
The build downloads only pinned artifacts and verifies their Git blob object
IDs before packaging. The loader and libraries remain under the GNU LGPL terms.


## Termux terminal libraries 0.118.0

Neutron uses the upstream `terminal-view` and `terminal-emulator` Android library modules from [termux/termux-app](https://github.com/termux/termux-app), pinned to version `0.118.0`. These reusable library modules are licensed under Apache License 2.0; this does not relicense or include the full Termux application, which is GPL-3.0-only. See [Termux's license information](https://github.com/termux/termux-app/blob/master/LICENSE.md) and the included Apache-2.0 license text at `licenses/Apache-2.0-Termux-terminal-libraries.txt`.
