# Neutron audio output

This module is the Android-side PCM playback endpoint.

Implemented:
- Oboe output stream.
- PCM16 mono/stereo input, converted to float32 stereo.
- 44.1 kHz and 48 kHz stream rates.
- Bounded single-producer/single-consumer ring buffer.
- Underruns are filled with silence; excess input frames are dropped.
- Output only. No microphone capture or recording permission.

Not implemented yet:
- Guest-side PulseAudio server/sink and capture.
- Transport from the guest rootfs into the native endpoint.

The PulseAudio client library alone is not a system-wide capture endpoint.
A guest-side server/sink or compatible monitor source must capture application
playback and forward PCM frames to the host bridge. The JNI byte-array API is
an initial integration boundary; the runtime should later use a native
transport/FD path to avoid copying every audio block through Java.
