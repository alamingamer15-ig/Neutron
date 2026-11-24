package io.github.alamingamer15ig.neutron;

import android.content.Context;

import java.io.File;
import java.util.Objects;

/**
 * Java-facing API for Neutron's process-wide native logger.
 *
 * Log files are written to the app-private files directory by default:
 * files/logs/neutron.log
 */
public final class NeutronLog {
    public enum Level {
        TRACE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),
        FATAL(5);

        final int nativeValue;

        Level(int nativeValue) {
            this.nativeValue = nativeValue;
        }
    }

    private static final long DEFAULT_MAX_FILE_BYTES = 1024L * 1024L;
    private static final int DEFAULT_BACKUP_COUNT = 3;
    private static volatile boolean initialized;

    static {
        System.loadLibrary("neutron_native");
    }

    private NeutronLog() {
    }

    private static native boolean nativeInit(String directory, long maxFileBytes,
                                             int backupCount, int minimumLevel);
    private static native void nativeLog(int level, String tag, String message);
    private static native void nativeSetMinimumLevel(int level);
    private static native void nativeFlush();
    private static native void nativeShutdown();

    /** Initializes logging under Context.getFilesDir()/logs. */
    public static synchronized boolean init(Context context) {
        Objects.requireNonNull(context, "context");
        return init(new File(context.getFilesDir(), "logs"),
                DEFAULT_MAX_FILE_BYTES, DEFAULT_BACKUP_COUNT, Level.TRACE);
    }

    /** Initializes logging with explicit rotation and filtering settings. */
    public static synchronized boolean init(File directory, long maxFileBytes,
                                             int backupCount, Level minimumLevel) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(minimumLevel, "minimumLevel");

        if (maxFileBytes <= 0 || backupCount < 0) {
            throw new IllegalArgumentException("Invalid log rotation settings");
        }
        if (!directory.exists() && !directory.mkdirs()) {
            initialized = false;
            return false;
        }
        if (!directory.isDirectory()) {
            initialized = false;
            return false;
        }

        initialized = nativeInit(directory.getAbsolutePath(), maxFileBytes,
                backupCount, minimumLevel.nativeValue);
        return initialized;
    }

    public static void trace(String tag, String message) {
        log(Level.TRACE, tag, message);
    }

    public static void debug(String tag, String message) {
        log(Level.DEBUG, tag, message);
    }

    public static void info(String tag, String message) {
        log(Level.INFO, tag, message);
    }

    public static void warn(String tag, String message) {
        log(Level.WARN, tag, message);
    }

    public static void error(String tag, String message) {
        log(Level.ERROR, tag, message);
    }

    public static void fatal(String tag, String message) {
        log(Level.FATAL, tag, message);
    }

    public static void log(Level level, String tag, String message) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(message, "message");
        if (initialized) {
            nativeLog(level.nativeValue, tag, message);
        }
    }

    public static void setMinimumLevel(Level level) {
        Objects.requireNonNull(level, "level");
        nativeSetMinimumLevel(level.nativeValue);
    }

    /** Forces buffered log data to disk. */
    public static void flush() {
        if (initialized) {
            nativeFlush();
        }
    }

    /** Flushes and closes the native log file. */
    public static synchronized void shutdown() {
        if (initialized) {
            nativeShutdown();
            initialized = false;
        }
    }
}
