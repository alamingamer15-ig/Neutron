package io.github.alamingamer15ig.neutron;

import android.content.Context;
import android.system.Os;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Installs Neutron's pinned ARM64 glibc, Wayland and X11 runtime bundle. */
public final class NeutronRuntimeInstaller {
    private static final String ASSET_NAME = "neutron-runtime-arm64.tar.gz";
    private static final String RUNTIME_MARKER = ".runtime-installed";
    private static final long MAX_UNPACKED_BYTES = 256L * 1024L * 1024L;

    private NeutronRuntimeInstaller() {
    }

    public static boolean isInstalled(NeutronEnvironment environment) {
        if (environment == null || !environment.isReady()) return false;
        File prefix = environment.getRootDirectory();
        return new File(prefix, RUNTIME_MARKER).isFile()
                && new File(prefix, "glibc/lib/ld-linux-aarch64.so.1").isFile()
                && new File(prefix, "glibc/lib/libc.so.6").isFile()
                && new File(prefix, "usr/lib/aarch64-linux-gnu/libwayland-client.so.0").exists()
                && new File(prefix, "usr/lib/aarch64-linux-gnu/libX11.so.6").exists()
                && new File(prefix, "bin/grun").isFile();
    }

    public static synchronized void install(Context context) throws IOException {
        NeutronEnvironment environment = NeutronEnvironment.initialize(context);
        if (!environment.isReady()) {
            throw new IOException("Neutron prefix directories could not be created");
        }
        if (isInstalled(environment)) return;

        File prefix = environment.getRootDirectory();
        File staging = File.createTempFile("runtime-", ".stage", prefix);
        if (!staging.delete() || !staging.mkdirs()) {
            throw new IOException("Could not create runtime staging directory");
        }

        boolean committed = false;
        try {
            extractAsset(context, staging);

            requireFile(staging, "glibc/lib/ld-linux-aarch64.so.1");
            requireFile(staging, "glibc/lib/libc.so.6");
            requireFile(staging, "glibc/lib/libm.so.6");
            requireFile(staging, "usr/lib/aarch64-linux-gnu/libwayland-client.so.0");
            requireFile(staging, "usr/lib/aarch64-linux-gnu/libX11.so.6");
            requireFile(staging, "usr/share/neutron/runtime-packages.txt");

            replaceDirectory(new File(staging, "glibc"), new File(prefix, "glibc"));
            replaceDirectory(new File(staging, "usr"), new File(prefix, "usr"));
            writeExecutable(new File(prefix, "bin/grun"), grunScript());
            writeExecutable(new File(prefix, "bin/glibc-check"), checkScript());

            File marker = new File(prefix, RUNTIME_MARKER);
            try (FileOutputStream output = new FileOutputStream(marker, false)) {
                output.write("neutron-arm64-runtime-1\n".getBytes("UTF-8"));
                output.flush();
                output.getFD().sync();
            }
            committed = true;
        } finally {
            deleteRecursively(staging);
            if (!committed) new File(prefix, RUNTIME_MARKER).delete();
        }
    }

    private static void extractAsset(Context context, File staging) throws IOException {
        String stagingPath = staging.getCanonicalPath();
        long totalBytes = 0L;
        byte[] buffer = new byte[16384];

        try (InputStream asset = new BufferedInputStream(context.getAssets().open(ASSET_NAME));
             GzipCompressorInputStream gzip = new GzipCompressorInputStream(asset);
             TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                String name = entry.getName();
                while (name.startsWith("./")) name = name.substring(2);
                if (name.isEmpty()) continue;
                if (name.startsWith("/") || containsParentTraversal(name)) {
                    throw new IOException("Unsafe runtime archive path: " + name);
                }

                File output = new File(staging, name);
                String canonical = output.getCanonicalPath();
                if (!canonical.startsWith(stagingPath + File.separator)) {
                    throw new IOException("Runtime archive path escapes prefix");
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
                    if (linkName == null || linkName.isEmpty()
                            || new File(linkName).isAbsolute()) {
                        throw new IOException("Unsafe absolute runtime symlink: " + name);
                    }
                    Path target = Paths.get(output.getParent()).resolve(linkName).normalize();
                    if (!target.toFile().getCanonicalPath()
                            .startsWith(stagingPath + File.separator)) {
                        throw new IOException("Runtime symlink escapes prefix: " + name);
                    }
                    if (output.exists() || isSymlink(output)) output.delete();
                    try {
                        Os.symlink(linkName, output.getAbsolutePath());
                    } catch (Exception error) {
                        throw new IOException("Could not create runtime symlink: " + name, error);
                    }
                    continue;
                }

                if (entry.isLink()) {
                    throw new IOException("Hard links are not supported in runtime bundle");
                }
                if (!entry.isFile()) continue;

                try (BufferedOutputStream file = new BufferedOutputStream(
                        new FileOutputStream(output, false))) {
                    int count;
                    while ((count = tar.read(buffer)) != -1) {
                        totalBytes += count;
                        if (totalBytes > MAX_UNPACKED_BYTES) {
                            throw new IOException("Runtime bundle exceeds extraction limit");
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

    private static void requireFile(File root, String relativePath) throws IOException {
        if (!new File(root, relativePath).isFile()) {
            throw new IOException("Runtime bundle is missing " + relativePath);
        }
    }

    private static void replaceDirectory(File source, File destination) throws IOException {
        if (!source.isDirectory()) {
            throw new IOException("Runtime bundle directory missing: " + source.getName());
        }
        deleteRecursively(destination);
        if (!source.renameTo(destination)) {
            throw new IOException("Could not install runtime directory: " + destination.getName());
        }
    }

    private static void writeExecutable(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent == null || !(parent.isDirectory() || parent.mkdirs())) {
            throw new IOException("Could not create Neutron bin directory");
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(content.getBytes("UTF-8"));
            output.flush();
            output.getFD().sync();
        }
        file.setReadable(true, false);
        file.setWritable(true, true);
        file.setExecutable(true, false);
    }

    private static String grunScript() {
        return "#!/system/bin/sh\n"
                + "set -eu\n"
                + ": \"${PREFIX:?Neutron PREFIX is not set}\"\n"
                + "GLIBC_ROOT=\"${GLIBC_PREFIX:-$PREFIX/glibc}\"\n"
                + "LOADER=\"$GLIBC_ROOT/lib/ld-linux-aarch64.so.1\"\n"
                + "if [ ! -x \"$LOADER\" ]; then echo \"Neutron glibc runtime is missing\" >&2; exit 127; fi\n"
                + "if [ \"$#\" -eq 0 ] || [ \"$1\" = \"--help\" ] || [ \"$1\" = \"--version\" ]; then exec \"$LOADER\" \"$@\"; fi\n"
                + "PROGRAM=\"$1\"\n"
                + "shift\n"
                + "case \"$PROGRAM\" in\n"
                + "  \"$PREFIX\"/*) ;;\n"
                + "  /*) PROGRAM=\"$PREFIX$PROGRAM\" ;;\n"
                + "  */*) PROGRAM=\"$PWD/$PROGRAM\" ;;\n"
                + "  *) if [ -x \"$PREFIX/usr/bin/$PROGRAM\" ]; then PROGRAM=\"$PREFIX/usr/bin/$PROGRAM\"; elif [ -x \"$PREFIX/bin/$PROGRAM\" ]; then PROGRAM=\"$PREFIX/bin/$PROGRAM\"; else echo \"grun: command not found: $PROGRAM\" >&2; exit 127; fi ;;\n"
                + "esac\n"
                + "if [ ! -x \"$PROGRAM\" ]; then echo \"grun: not executable: $PROGRAM\" >&2; exit 126; fi\n"
                + "LIBPATH=\"$GLIBC_ROOT/lib:$PREFIX/usr/lib/aarch64-linux-gnu:$PREFIX/usr/lib:$PREFIX/lib:$PREFIX/lib64\"\n"
                + "exec \"$LOADER\" --library-path \"$LIBPATH\" \"$PROGRAM\" \"$@\"\n";
    }

    private static String checkScript() {
        return "#!/system/bin/sh\n"
                + "set -eu\n"
                + "exec \"$PREFIX/bin/grun\" --help\n";
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
