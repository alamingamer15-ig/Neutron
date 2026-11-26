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

}
