package io.github.alamingamer15ig.neutron;

/** Output-only PCM playback bridge for Neutron guest audio. */
public final class NeutronAudio {
    static { System.loadLibrary("neutron_native"); }
    private NeutronAudio() {}

    /** Starts native playback at 44100 or 48000 Hz. */
    public static boolean start(int sampleRate) { return nativeStart(sampleRate); }

    /** Stops playback and releases the Android output stream. */
    public static void stop() { nativeStop(); }

    /**
     * Queues little-endian signed PCM16, interleaved mono or stereo.
     * @return number of frames accepted by the native queue
     */
    public static int writePcm16(byte[] pcm, int channels) {
        return nativeWritePcm16(pcm, channels);
    }

    private static native boolean nativeStart(int sampleRate);
    private static native void nativeStop();
    private static native int nativeWritePcm16(byte[] pcm, int channels);
}
