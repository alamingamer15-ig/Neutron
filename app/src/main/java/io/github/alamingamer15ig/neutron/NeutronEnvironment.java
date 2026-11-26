package io.github.alamingamer15ig.neutron;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Owns Neutron's Termux-inspired application-private userspace layout.
 *
 * This bootstrap stage creates the prefix directories only. It deliberately
 * does not pretend that glibc or a Linux distribution has been installed.
 */
public final class NeutronEnvironment {
    private static final String[] DIRECTORIES = {"bin", "lib", "lib64", "home"};

    private final File rootDirectory;
    private final boolean ready;

    private NeutronEnvironment(File rootDirectory, boolean ready) {
        this.rootDirectory = rootDirectory;
        this.ready = ready;
    }

    public static NeutronEnvironment initialize(Context context) {
        File root = new File(context.getFilesDir(), "neutron");
        boolean success = root.exists() || root.mkdirs();
        for (String name : DIRECTORIES) {
            File directory = new File(root, name);
            if (!(directory.isDirectory() || directory.mkdirs())) {
                success = false;
            }
            if (directory.isDirectory()) {
                directory.setReadable(true, true);
                directory.setWritable(true, true);
                directory.setExecutable(true, true);
            }
        }
        return new NeutronEnvironment(root, success);
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public File getBinDirectory() {
        return new File(rootDirectory, "bin");
    }

    public File getLibDirectory() {
        return new File(rootDirectory, "lib");
    }

    public File getLib64Directory() {
        return new File(rootDirectory, "lib64");
    }

    public File getHomeDirectory() {
        return new File(rootDirectory, "home");
    }

    public boolean isReady() {
        if (!ready) return false;
        for (String name : DIRECTORIES) {
            if (!new File(rootDirectory, name).isDirectory()) return false;
        }
        return true;
    }

    /**
     * Runs a command through Android's supported system shell, with Neutron's
     * home as its working directory. Linux/glibc binaries are not supported by
     * this bootstrap shell until the native userspace runtime is integrated.
     */
    public static String runHostCommand(NeutronEnvironment environment, String command) {
        if (environment == null || !environment.isReady()) {
            return "Neutron: userspace directories are unavailable.\n";
        }

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh", "-c", command);
            builder.directory(environment.getHomeDirectory());
            builder.redirectErrorStream(true);
            Map<String, String> env = builder.environment();
            env.put("HOME", environment.getHomeDirectory().getAbsolutePath());
            env.put("PREFIX", environment.getRootDirectory().getAbsolutePath());
            env.put("PATH", environment.getBinDirectory().getAbsolutePath()
                    + ":/system/bin:/system/xbin");
            env.put("SHELL", "/system/bin/sh");
            env.put("TERM", "xterm-256color");

            process = builder.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                int total = 0;
                final int limit = 1024 * 1024;
                while ((count = input.read(buffer)) != -1) {
                    int accepted = Math.min(count, limit - total);
                    if (accepted > 0) {
                        output.write(buffer, 0, accepted);
                        total += accepted;
                    }
                    if (total >= limit) break;
                }
            }
            int exitCode = process.waitFor();
            String text = output.toString(StandardCharsets.UTF_8.name());
            if (text.length() == 0) text = "(no output)\n";
            return text + (text.endsWith("\n") ? "" : "\n")
                    + "[exit " + exitCode + "]\n";
        } catch (Exception error) {
            return "Neutron shell error: " + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage()) + "\n";
        } finally {
            if (process != null) process.destroy();
        }
    }
}
