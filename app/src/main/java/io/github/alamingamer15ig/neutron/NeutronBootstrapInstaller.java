package io.github.alamingamer15ig.neutron;

import android.content.Context;
import android.system.Os;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Installs Neutron's own ARM64 glibc userspace bootstrap. */
public final class NeutronBootstrapInstaller {
    private static final String ASSET_NAME = "neutron-bootstrap-arm64.tar";
    private static final String MARKER = ".bootstrap-installed";
    private static final long MAX_UNPACKED_BYTES = 512L * 1024L * 1024L;

    private NeutronBootstrapInstaller() {
    }

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
        // Bootstrap commands use the patched loader and runtime libraries.
        NeutronRuntimeInstaller.install(context);
        if (isInstalled(environment)) return;

        File prefix = environment.getRootDirectory();
        File staging = File.createTempFile("bootstrap-", ".stage", prefix);
        if (!staging.delete() || !staging.mkdirs()) {
            throw new IOException("Could not create bootstrap staging directory");
        }

        boolean committed = false;
        try {
            extractAsset(context, staging);
            requireFile(staging, "usr/bin/bash");
            requireFile(staging, "usr/bin/apt-get");
            requireFile(staging, "usr/bin/dpkg");
            requireFile(staging, "usr/bin/ls");
            requireFile(staging, "usr/bin/tar");
            requireFile(staging, "var/lib/dpkg/status");
            requireFile(staging, "etc/apt/sources.list");
            requireFile(staging, "bin/neutron-exec");
            requireFile(staging, "bin/pkg");

            replaceDirectory(new File(staging, "usr"), new File(prefix, "usr"));
            replaceDirectory(new File(staging, "etc"), new File(prefix, "etc"));
            replaceDirectory(new File(staging, "var"), new File(prefix, "var"));
            mergeDirectory(new File(staging, "bin"), new File(prefix, "bin"));

            File marker = new File(prefix, MARKER);
            try (FileOutputStream output = new FileOutputStream(marker, false)) {
                output.write("neutron-arm64-bootstrap-1\n".getBytes("UTF-8"));
                output.flush();
                output.getFD().sync();
            }
            committed = true;
        } finally {
            deleteRecursively(staging);
            if (!committed) new File(prefix, MARKER).delete();
        }
    }

    private static void extractAsset(Context context, File staging) throws IOException {
        String stagingPath = staging.getCanonicalPath();
        long totalBytes = 0L;
        byte[] buffer = new byte[16384];
        try (InputStream asset = new BufferedInputStream(context.getAssets().open(ASSET_NAME));
             TarArchiveInputStream tar = new TarArchiveInputStream(asset)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                String name = entry.getName();
                while (name.startsWith("./")) name = name.substring(2);
                if (name.isEmpty()) continue;
                if (name.startsWith("/") || containsParentTraversal(name)) {
                    throw new IOException("Unsafe bootstrap archive path: " + name);
                }
                File output = new File(staging, name);
                String canonical = output.getCanonicalPath();
                if (!canonical.startsWith(stagingPath + File.separator)) {
                    throw new IOException("Bootstrap archive path escapes staging directory");
                }
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) {
                        throw new IOException("Could not create " + name);
                    }
                    output.setReadable(true, false);
                    output.setExecutable(true, false);
                    continue;
                }
                File parent = output.getParentFile();
                if (parent == null || !(parent.isDirectory() || parent.mkdirs())) {
                    throw new IOException("Could not create parent for " + name);
                }
                if (entry.isSymbolicLink()) {
                    String linkName = entry.getLinkName();
                    if (linkName == null || linkName.isEmpty() || new File(linkName).isAbsolute()) {
                        throw new IOException("Unsafe bootstrap symlink: " + name);
                    }
                    Path target = Paths.get(output.getParent()).resolve(linkName).normalize();
                    if (!target.toFile().getCanonicalPath().startsWith(stagingPath + File.separator)) {
                        throw new IOException("Bootstrap symlink escapes staging directory: " + name);
                    }
                    if (output.exists() || isSymlink(output)) output.delete();
                    try {
                        Os.symlink(linkName, output.getAbsolutePath());
                    } catch (Exception error) {
                        throw new IOException("Could not create bootstrap symlink: " + name, error);
                    }
                    continue;
                }
                if (entry.isLink()) {
                    throw new IOException("Bootstrap hard links are not supported");
                }
                if (!entry.isFile()) continue;
                try (BufferedOutputStream file = new BufferedOutputStream(new FileOutputStream(output))) {
                    int count;
                    while ((count = tar.read(buffer)) != -1) {
                        totalBytes += count;
                        if (totalBytes > MAX_UNPACKED_BYTES) {
                            throw new IOException("Bootstrap exceeds extraction size limit");
                        }
                        file.write(buffer, 0, count);
                    }
                }
                output.setReadable(true, false);
                output.setWritable(true, true);
                output.setExecutable((entry.getMode() & 0111) != 0, false);
            }
        }
    }

    private static boolean containsParentTraversal(String path) {
        for (String part : path.split("/")) {
            if ("..".equals(part)) return true;
        }
        return false;
    }

    private static boolean isSymlink(File file) {
        try {
            Os.readlink(file.getAbsolutePath());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void requireFile(File root, String path) throws IOException {
        if (!new File(root, path).isFile()) {
            throw new IOException("Bootstrap is missing " + path);
        }
    }

    private static void replaceDirectory(File source, File destination) throws IOException {
        if (!source.isDirectory()) throw new IOException("Bootstrap directory missing: " + source.getName());
        deleteRecursively(destination);
        if (!source.renameTo(destination)) {
            throw new IOException("Could not install bootstrap directory: " + destination.getName());
        }
    }

    private static void mergeDirectory(File source, File destination) throws IOException {
        if (!source.isDirectory()) throw new IOException("Bootstrap bin directory missing");
        if (!destination.isDirectory() && !destination.mkdirs()) {
            throw new IOException("Could not create Neutron bin directory");
        }
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(destination, child.getName());
            if (child.isDirectory() && !isSymlink(child)) {
                mergeDirectory(child, target);
            } else {
                if (target.exists() || isSymlink(target)) deleteRecursively(target);
                if (isSymlink(child)) {
                    try {
                        Os.symlink(Os.readlink(child.getAbsolutePath()), target.getAbsolutePath());
                    } catch (Exception error) {
                        throw new IOException("Could not merge bootstrap symlink: " + child.getName(), error);
                    }
                } else if (!child.renameTo(target)) {
                    throw new IOException("Could not install bootstrap command: " + child.getName());
                }
            }
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists() && !isSymlink(file)) return;
        if (isSymlink(file) || !file.isDirectory()) {
            if (!file.delete()) throw new IOException("Could not remove " + file.getName());
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) throw new IOException("Could not remove " + file.getName());
    }
}
