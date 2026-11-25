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
\n\n## Apache Commons Compress\n\nNeutron uses Apache Commons Compress 1.27.1 (Apache License 2.0) to safely read the compressed Alpine minirootfs archive.\n
## Android-compatible GNU C Library (glibc) 2.42.9

Neutron packages the AArch64 dynamic loader and rootfs runtime libraries from
[jjtseng93/glibc-for-android-native](https://github.com/jjtseng93/glibc-for-android-native),
pinned to commit `d7e375f9145b2041f479416eccfc924fd3bde3d6`. This is a modified
GNU C Library build intended to avoid Android app-sandbox SIGSYS failures. Its
documented changes adjust `set_robust_list` and `rseq` handling and patch the
resolver configuration path. The exact source and patch notes are available in
the pinned upstream repository: [glibc source archive](https://github.com/jjtseng93/glibc-for-android-native/blob/d7e375f9145b2041f479416eccfc924fd3bde3d6/glibc-2.42.9-revised.tar.gz),
[SOURCE_INFO](https://github.com/jjtseng93/glibc-for-android-native/blob/d7e375f9145b2041f479416eccfc924fd3bde3d6/SOURCE_INFO),
and [LGPL-2.1 license](https://github.com/jjtseng93/glibc-for-android-native/blob/d7e375f9145b2041f479416eccfc924fd3bde3d6/LICENSE).
The build downloads only pinned artifacts and verifies their Git blob object
IDs before packaging. The loader and libraries remain under the GNU LGPL terms.
