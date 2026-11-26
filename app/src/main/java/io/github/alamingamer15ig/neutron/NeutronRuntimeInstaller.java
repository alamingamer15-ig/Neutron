package io.github.alamingamer15ig.neutron;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Installs Neutron's glibc toolchain through its private Termux pkg environment. */
public final class NeutronRuntimeInstaller {
    private static final String MARKER = ".glibc-packages-installed";

    private NeutronRuntimeInstaller() { }

    public static boolean isInstalled(NeutronEnvironment environment) {
        if (environment == null || !NeutronBootstrapInstaller.isInstalled(environment)) return false;
        return new File(environment.getRootDirectory(), MARKER).isFile();
    }

    public static synchronized void install(Context context) throws IOException {
        NeutronEnvironment environment = NeutronEnvironment.initialize(context);
        if (!environment.isReady()) throw new IOException("Neutron prefix is unavailable");
        NeutronBootstrapInstaller.install(context);
        if (isInstalled(environment)) return;

        File prefix = environment.getRootDirectory();
        File home = environment.getHomeDirectory();
        String bash = new File(prefix, "bin/bash").getAbsolutePath();
        String script = "set -e; "
                + "pkg update -y; "
                + "pkg install -y glibc-repo; "
                + "pkg update -y; "
                + "pkg install -y glibc-runner gcc-glibc gcc-libs-glibc; "
                + "dpkg-query -W glibc-runner gcc-glibc gcc-libs-glibc >/dev/null; "
                + "touch \"$PREFIX/" + MARKER + "\"";

        ProcessBuilder builder = new ProcessBuilder(bash, "-c", script);
        builder.directory(home);
        builder.redirectErrorStream(true);
        builder.environment().put("PREFIX", prefix.getAbsolutePath());
        builder.environment().put("HOME", home.getAbsolutePath());
        builder.environment().put("PATH", prefix + "/bin:/system/bin:/system/xbin");
        builder.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
        builder.environment().put("TMPDIR", prefix + "/tmp");
        builder.environment().put("LANG", "C.UTF-8");
        builder.environment().put("TERM", "xterm-256color");

        StringBuilder output = new StringBuilder();
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 12000) output.delete(0, output.length() - 8000);
                output.append(line).append('\n');
            }
        } catch (IOException error) {
            process.destroy();
            throw error;
        }

        final int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new IOException("Interrupted while installing Termux glibc packages", error);
        }
        if (exitCode != 0) {
            throw new IOException("Termux pkg setup failed with exit code " + exitCode
                    + (output.length() == 0 ? "" : ":\n" + output));
        }
        if (!isInstalled(environment)) {
            throw new IOException("glibc package installation completed without its marker");
        }
    }
}
