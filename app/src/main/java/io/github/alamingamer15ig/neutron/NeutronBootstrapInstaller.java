package io.github.alamingamer15ig.neutron;

import android.content.Context;
import android.system.Os;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs a Termux bootstrap built specifically for Neutron's package id and prefix. */
public final class NeutronBootstrapInstaller {
    private static final String ASSET_NAME = "neutron-bootstrap-arm64.zip";
    private static final String MARKER = ".termux-bootstrap-installed";
    private static final String BUILD_PREFIX =
            "/data/data/io.github.alamingamer15ig.neutron/files/neutron";
    private static final long MAX_UNPACKED_BYTES = 512L * 1024L * 1024L;

    private NeutronBootstrapInstaller() { }

    public static boolean isInstalled(NeutronEnvironment environment) {
        if (environment == null || !environment.isReady()) return false;
        File prefix = environment.getRootDirectory();
        return new File(prefix, MARKER).isFile()
                && new File(prefix, "bin/bash").canExecute()
                && new File(prefix, "bin/pkg").canExecute()
                && new File(prefix, "bin/apt-get").canExecute()
                && new File(prefix, "bin/dpkg").canExecute()
                && new File(prefix, "var/lib/dpkg/status").isFile();
    }

    public static synchronized void install(Context context) throws IOException {
        NeutronEnvironment environment = NeutronEnvironment.initialize(context);
        if (!environment.isReady()) {
            throw new IOException("Neutron prefix directories could not be created");
        }
        if (isInstalled(environment)) return;

        File prefix = environment.getRootDirectory();
        File staging = File.createTempFile("termux-bootstrap-", ".stage", prefix);
        if (!staging.delete() || !staging.mkdirs()) {
            throw new IOException("Could not create Termux bootstrap staging directory");
        }

        boolean committed = false;
        try {
            extractBootstrap(context, staging);
            requireFile(staging, "bin/bash");
            requireFile(staging, "bin/pkg");
            requireFile(staging, "bin/apt-get");
            requireFile(staging, "bin/dpkg");
            requireFile(staging, "var/lib/dpkg/status");
            requireFile(staging, "etc/apt/sources.list");

            File[] entries = staging.listFiles();
            if (entries == null) throw new IOException("Could not list extracted bootstrap");
            for (File entry : entries) {
                File destination = new File(prefix, entry.getName());
                deleteRecursively(destination);
                if (!entry.renameTo(destination)) {
                    throw new IOException("Could not install Termux prefix entry: " + entry.getName());
                }
            }

            File marker = new File(prefix, MARKER);
            try (FileOutputStream output = new FileOutputStream(marker, false)) {
                output.write("neutron-termux-bootstrap-1\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.getFD().sync();
            }
            committed = true;
        } finally {
            deleteRecursively(staging);
            if (!committed) new File(prefix, MARKER).delete();
        }
    }

    private static void extractBootstrap(Context context, File staging) throws IOException {
        String stagingPath = staging.getCanonicalPath();
        Path stagingRoot = Paths.get(stagingPath).toAbsolutePath().normalize();
        long totalBytes = 0L;
        byte[] buffer = new byte[16384];
        List<String[]> symlinks = new ArrayList<>();

        try (InputStream asset = context.getAssets().open(ASSET_NAME);
             ZipInputStream zip = new ZipInputStream(asset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizeEntry(entry.getName());
                if (name.isEmpty()) continue;
                if ("SYMLINKS.txt".equals(name)) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    int count;
                    while ((count = zip.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    String content = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                    for (String line : content.split("\\r?\\n")) {
                        if (line.isEmpty()) continue;
                        String[] parts = line.split("←", 2);
                        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                            throw new IOException("Malformed Termux SYMLINKS.txt entry");
                        }
                        symlinks.add(parts);
                    }
                    continue;
                }

                File output = safeOutput(staging, stagingRoot, name);
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) {
                        throw new IOException("Could not create bootstrap directory: " + name);
                    }
                    continue;
                }

                File parent = output.getParentFile();
                if (parent == null || !(parent.isDirectory() || parent.mkdirs())) {
                    throw new IOException("Could not create parent for bootstrap entry: " + name);
                }
                try (BufferedOutputStream file = new BufferedOutputStream(new FileOutputStream(output))) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        totalBytes += count;
                        if (totalBytes > MAX_UNPACKED_BYTES) {
                            throw new IOException("Termux bootstrap exceeds extraction size limit");
                        }
                        file.write(buffer, 0, count);
                    }
                }
                output.setReadable(true, false);
                output.setWritable(true, true);
                if (isExecutablePath(name)) output.setExecutable(true, false);
            }
        }

        if (symlinks.isEmpty()) throw new IOException("Termux bootstrap has no SYMLINKS.txt entries");
        for (String[] link : symlinks) {
            String target = link[0];
            String name = normalizeEntry(link[1]);
            File output = safeOutput(staging, stagingRoot, name);
            File parent = output.getParentFile();
            if (parent == null || !(parent.isDirectory() || parent.mkdirs())) {
                throw new IOException("Could not create symlink parent: " + name);
            }

            Path rawTarget = Paths.get(target);
            String linkTarget = target;
            if (rawTarget.isAbsolute()) {
                if (target.equals(BUILD_PREFIX) || target.startsWith(BUILD_PREFIX + "/")) {
                    Path suffix = Paths.get(BUILD_PREFIX).relativize(rawTarget);
                    Path resolved = stagingRoot.resolve(suffix).normalize();
                    if (!resolved.startsWith(stagingRoot) || resolved.equals(stagingRoot)) {
                        throw new IOException("Termux bootstrap symlink escapes its prefix: " + name);
                    }
                    linkTarget = output.toPath().toAbsolutePath().normalize().getParent()
                            .relativize(resolved).toString();
                } else if (!target.startsWith("/system/") && !target.startsWith("/apex/")) {
                    throw new IOException("Unexpected absolute Termux symlink: " + name);
                }
            } else {
                Path resolved = output.toPath().toAbsolutePath().normalize().getParent()
                        .resolve(rawTarget).normalize();
                if (!resolved.startsWith(stagingRoot) || resolved.equals(stagingRoot)) {
                    throw new IOException("Termux bootstrap symlink escapes its prefix: " + name);
                }
            }

            try {
                Os.symlink(linkTarget, output.getAbsolutePath());
            } catch (Exception error) {
                throw new IOException("Could not create Termux symlink: " + name, error);
            }
        }
    }

    private static String normalizeEntry(String name) throws IOException {
        while (name.startsWith("./")) name = name.substring(2);
        if (name.isEmpty()) return "";
        if (name.startsWith("/") || containsParentTraversal(name)) {
            throw new IOException("Unsafe Termux bootstrap path: " + name);
        }
        return name;
    }

    private static File safeOutput(File staging, Path stagingRoot, String name) throws IOException {
        File output = new File(staging, name);
        Path resolved = output.toPath().toAbsolutePath().normalize();
        if (!resolved.startsWith(stagingRoot) || resolved.equals(stagingRoot)) {
            throw new IOException("Termux bootstrap path escapes staging: " + name);
        }
        return output;
    }

    private static boolean isExecutablePath(String path) {
        return path.startsWith("bin/")
                || path.startsWith("libexec/")
                || path.equals("lib/apt/apt-helper")
                || path.startsWith("lib/apt/methods/");
    }

    private static boolean containsParentTraversal(String path) {
        for (String part : path.split("/")) if ("..".equals(part)) return true;
        return false;
    }

    private static void requireFile(File root, String path) throws IOException {
        if (!new File(root, path).isFile()) {
            throw new IOException("Termux bootstrap is missing " + path);
        }
    }

    private static boolean isSymlink(File file) {
        try {
            Os.readlink(file.getAbsolutePath());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists() && !isSymlink(file)) return;
        if (isSymlink(file) || !file.isDirectory()) {
            if (!file.delete()) throw new IOException("Could not remove " + file.getName());
            return;
        }
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        if (!file.delete()) throw new IOException("Could not remove " + file.getName());
    }
}
