package io.github.alamingamer15ig.neutron;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns one native pseudo-terminal and its interactive Android host shell.
 * The reader thread must call destroy() after read() returns -1.
 */
public final class NeutronPty {
    static {
        System.loadLibrary("neutron_native");
    }

    private final long handle;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private NeutronPty(long handle) {
        this.handle = handle;
    }

    public static NeutronPty start(NeutronEnvironment environment) {
        if (environment == null || !environment.isReady()) {
            throw new IllegalStateException("Neutron userspace is not initialized");
        }
        long handle = nativeStart(
                environment.getRootDirectory().getAbsolutePath(),
                environment.getHomeDirectory().getAbsolutePath(),
                environment.getBinDirectory().getAbsolutePath());
        if (handle == 0L) {
            throw new IllegalStateException("Could not create the terminal PTY");
        }
        return new NeutronPty(handle);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int read(byte[] buffer) {
        if (buffer == null || buffer.length == 0 || destroyed.get()) return -1;
        return nativeRead(handle, buffer);
    }

    public void write(String value) {
        if (value == null || closed.get() || destroyed.get()) return;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        nativeWrite(handle, bytes);
    }

    public void resize(int rows, int columns) {
        if (!closed.get() && !destroyed.get()) {
            nativeResize(handle, rows, columns);
        }
    }

    public void close() {
        if (closed.compareAndSet(false, true) && !destroyed.get()) {
            nativeStop(handle);
        }
    }

    /** Called by the PTY reader after its blocking read loop has ended. */
    public void destroy() {
        close();
        if (destroyed.compareAndSet(false, true)) {
            nativeDestroy(handle);
        }
    }

    private static native long nativeStart(String prefix, String home, String bin);
    private static native int nativeRead(long handle, byte[] buffer);
    private static native int nativeWrite(long handle, byte[] buffer);
    private static native void nativeResize(long handle, int rows, int columns);
    private static native void nativeStop(long handle);
    private static native void nativeDestroy(long handle);
}
