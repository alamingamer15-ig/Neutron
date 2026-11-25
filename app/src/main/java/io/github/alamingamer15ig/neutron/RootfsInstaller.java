package io.github.alamingamer15ig.neutron;

import android.content.Context;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.stream.Stream;

public final class RootfsInstaller {
    private static final String ROOTFS_ARCHIVE = "neutron-aarch64-glibc.tar.gz";
    private static final String BASE_URL =
            "https://raw.githubusercontent.com/alamingamer15-ig/Neutron/main/rootfs/images/";
    
    private static final long MAX_DOWNLOAD_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 50000;

    public interface ProgressListener {
        // A negative value means extraction has started.
        void onProgress(int percent);
    }

    private RootfsInstaller() {
    }

    public static File getRootfsDirectory(Context context) {
        return new File(context.getFilesDir(), "rootfs/neutron-aarch64-glibc");
    }

    public static boolean isInstalled(Context context) {
        File root = getRootfsDirectory(context);
        File loaderFile = new File(root, "lib64/ld-linux-aarch64.so.1");
        if (!loaderFile.isFile()) loaderFile = new File(root, "lib/ld-linux-aarch64.so.1");
        File busyboxFile = new File(root, "bin/busybox");
        boolean loader = isAarch64Elf(loaderFile) && loaderFile.canExecute();
        return root.isDirectory() && root.canExecute()
                && new File(root, "bin").isDirectory() && new File(root, "bin").canExecute()
                && new File(root, "lib").isDirectory() && new File(root, "lib").canExecute()
                && new File(root, "home").isDirectory() && new File(root, "home").canExecute()
                && new File(root, "dev").isDirectory() && new File(root, "dev").canExecute()
                && new File(root, "proc").isDirectory() && new File(root, "proc").canExecute()
                && new File(root, "etc/neutron-rootfs-release").isFile()
                && new File(root, "etc/neutron-android-glibc-release").isFile()
                && isAarch64Elf(new File(root, "lib/libc.so.6"))
                && isAarch64Elf(new File(root, "lib/libdl.so.2"))
                && isAarch64Elf(new File(root, "lib/libm.so.6"))
                && isAarch64Elf(new File(root, "lib/libpthread.so.0"))
                && loader
                && isAarch64Elf(busyboxFile) && busyboxFile.canExecute();
    }

    public static File install(Context context, ProgressListener listener) throws Exception {
        File root = getRootfsDirectory(context);
        if (isInstalled(context)) {
            return root;
        }

        File work = new File(context.getCacheDir(), "rootfs-install");
        if (!work.exists() && !work.mkdirs()) {
            throw new IllegalStateException("Cannot create installer workspace");
        }
        File archive = new File(work, ROOTFS_ARCHIVE + ".part");
        File staging = new File(root.getParentFile(), "neutron-aarch64-glibc.installing");
        deleteRecursively(staging.toPath());

        String expectedHash = readExpectedHash(new URL(BASE_URL + ROOTFS_ARCHIVE + ".sha256"));
        download(new URL(BASE_URL + ROOTFS_ARCHIVE), archive, listener);
        String actualHash = sha256(archive);
        if (!actualHash.equalsIgnoreCase(expectedHash)) {
            throw new SecurityException("Neutron rootfs SHA-256 verification failed");
        }

        if (listener != null) {
            listener.onProgress(-1);
        }
        extractArchive(archive, staging);
        ensureRuntimePermissions(staging);
        File parent = root.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create rootfs storage");
        }
        deleteRecursively(root.toPath());
        if (!staging.renameTo(root)) {
            throw new IllegalStateException("Could not activate the extracted rootfs");
        }
        archive.delete();

        if (!isInstalled(context)) {
            throw new IllegalStateException("Rootfs failed ARM64 glibc validation");
        }
        return root;
    }

    private static void ensureRuntimePermissions(File root) throws Exception {
        File[] directories = {
                root, new File(root, "bin"), new File(root, "lib"),
                new File(root, "lib64"), new File(root, "home"),
                new File(root, "dev"), new File(root, "proc")
        };
        for (File directory : directories) {
            if (directory.isDirectory() && !directory.setExecutable(true, false)
                    && !directory.canExecute()) {
                throw new IllegalStateException(
                        "Cannot make rootfs directory searchable: " + directory.getPath());
            }
        }

        File busybox = new File(root, "bin/busybox");
        File loader = new File(root, "lib64/ld-linux-aarch64.so.1");
        if (!loader.isFile()) {
            loader = new File(root, "lib/ld-linux-aarch64.so.1");
        }
        File[] executables = {busybox, loader};
        for (File executable : executables) {
            if (!executable.isFile()) {
                throw new IllegalStateException(
                        "Required ARM64 executable is missing: " + executable.getPath());
            }
            if (!executable.setExecutable(true, false) && !executable.canExecute()) {
                throw new IllegalStateException(
                        "Cannot make ARM64 executable runnable: " + executable.getPath());
            }
            if (!executable.canExecute()) {
                throw new IllegalStateException(
                        "ARM64 executable permission verification failed: " + executable.getPath());
            }
        }
    }

    private static boolean isAarch64Elf(File file) {
        if (!file.isFile()) return false;
        try (InputStream input = new FileInputStream(file)) {
            byte[] header = new byte[20];
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
            int machine = (header[18] & 0xff) | ((header[19] & 0xff) << 8);
            return header[0] == 0x7f && header[1] == 'E' && header[2] == 'L'
                    && header[3] == 'F' && header[4] == 2 && header[5] == 1
                    && machine == 183;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readExpectedHash(URL url) throws Exception {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 4096) {
                    throw new IllegalStateException("Unexpected checksum response");
                }
                output.write(buffer, 0, count);
            }
            String value = new String(output.toByteArray(), "UTF-8").trim();
            String hash = value.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (!hash.matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("Invalid rootfs checksum response");
            }
            return hash;
        } finally {
            connection.disconnect();
        }
    }

    private static void download(URL url, File destination, ProgressListener listener)
            throws Exception {
        HttpURLConnection connection = open(url);
        try {
            long length = connection.getContentLengthLong();
            if (length > MAX_DOWNLOAD_BYTES) {
                throw new IllegalStateException("Rootfs archive is unexpectedly large");
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
                byte[] buffer = new byte[32768];
                long total = 0;
                int count;
                int lastPercent = -1;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new IllegalStateException("Download exceeded size limit");
                    }
                    output.write(buffer, 0, count);
                    if (length > 0 && listener != null) {
                        int percent = (int) Math.min(100, total * 100 / length);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(percent);
                        }
                    }
                }
                if (total == 0) {
                    throw new IllegalStateException("Downloaded archive is empty");
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Neutron-Android/0.1.0");
        connection.connect();
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("Rootfs server returned HTTP " + code);
        }
        return connection;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void extractArchive(File archive, File destination) throws Exception {
        Path root = destination.toPath().toAbsolutePath().normalize();
        Files.createDirectories(root);
        long expanded = 0;
        int entries = 0;

        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(
                        new BufferedInputStream(new FileInputStream(archive))))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalStateException("Rootfs archive has too many entries");
                }
                String name = entry.getName();
                Path output = root.resolve(name).normalize();
                if (!output.startsWith(root)) {
                    throw new SecurityException("Archive contains an unsafe path");
                }
                if (output.equals(root)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else if (entry.isSymbolicLink()) {
                    Files.createDirectories(output.getParent());
                    Path linkTarget = Paths.get(entry.getLinkName());
                    Path resolved = linkTarget.isAbsolute()
                            ? root.resolve(linkTarget.toString().substring(1)).normalize()
                            : output.getParent().resolve(linkTarget).normalize();
                    if (!resolved.startsWith(root)) {
                        throw new SecurityException("Archive symlink escapes rootfs");
                    }
                    Path relativeTarget = output.getParent().relativize(resolved);
                    Files.deleteIfExists(output);
                    Files.createSymbolicLink(output, relativeTarget);
                } else if (entry.isLink()) {
                    Path target = root.resolve(entry.getLinkName()).normalize();
                    if (!target.startsWith(root) || !Files.exists(target)) {
                        throw new SecurityException("Archive contains an unsafe hard link");
                    }
                    Files.createDirectories(output.getParent());
                    Files.deleteIfExists(output);
                    Files.createLink(output, target);
                } else if (entry.isFile()) {
                    if (entry.getSize() < 0 || expanded + entry.getSize() > MAX_EXTRACTED_BYTES) {
                        throw new IllegalStateException("Rootfs exceeds extraction size limit");
                    }
                    expanded += entry.getSize();
                    Files.createDirectories(output.getParent());
                    try (OutputStream file = new BufferedOutputStream(
                            new FileOutputStream(output.toFile()))) {
                        byte[] buffer = new byte[32768];
                        int count;
                        while ((count = tar.read(buffer)) != -1) {
                            file.write(buffer, 0, count);
                        }
                    }
                    int mode = entry.getMode();
                    output.toFile().setReadable((mode & 0444) != 0, false);
                    output.toFile().setWritable((mode & 0222) != 0, true);
                    output.toFile().setExecutable((mode & 0111) != 0, false);
                }
                // Device nodes and FIFOs are intentionally not created on Android.
            }
        }
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            Path[] ordered = paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .toArray(Path[]::new);
            for (Path item : ordered) {
                Files.deleteIfExists(item);
            }
        }
    }
}
