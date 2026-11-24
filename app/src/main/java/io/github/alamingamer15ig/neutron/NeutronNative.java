package io.github.alamingamer15ig.neutron;

/**
 * JNI entry points for Neutron's native runtime.
 *
 * Loading is lazy: the native library is loaded when this class is first used.
 */
public final class NeutronNative {
    static {
        System.loadLibrary("neutron_native");
    }

    private NeutronNative() {
    }

    /**
     * Decodes one x86-64 instruction and returns Intel-syntax disassembly.
     *
     * @param bytes guest instruction bytes beginning at the instruction boundary
     * @param runtimeAddress guest address used when formatting relative operands
     * @return formatted instruction or a diagnostic beginning with "decode error:"
     */
    public static native String decodeInstruction(byte[] bytes, long runtimeAddress);
}
