package io.github.alamingamer15ig.neutron;

import java.nio.charset.StandardCharsets;

/** Native command-output bridge for the ARM64 BusyBox terminal. */
public final class NeutronTerminal {
    static {
        System.loadLibrary("neutron_native");
    }

    private NeutronTerminal() {
    }

    private static native byte[] nativeRunCommand(
            String loaderPath, String busyboxPath, String rootfsPath, String command);

    private static native String nativeRunPlatformDiagnostics(String filesDirectory);

    public static String runPlatformDiagnostics(String filesDirectory) {
        String output = nativeRunPlatformDiagnostics(filesDirectory);
        return output == null ? "Neutron: platform diagnostics bridge failed.\n" : output;
    }

    public static String runCommand(
            String loaderPath, String busyboxPath, String rootfsPath, String command) {
        byte[] output = nativeRunCommand(loaderPath, busyboxPath, rootfsPath, command);
        if (output == null) {
            return "Neutron: native terminal output bridge failed.\n";
        }
        return new String(output, StandardCharsets.UTF_8);
    }
}
