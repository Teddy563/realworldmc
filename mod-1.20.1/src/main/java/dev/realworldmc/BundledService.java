package dev.realworldmc;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Extracts release-owned code separately from the user's keys, cache and logs. */
public final class BundledService {
    private static final String RESOURCE = "/realworldmc-service.zip";
    private BundledService() {}

    public static Path prepare(Path dataDirectory) throws IOException {
        try (var hash = BundledService.class.getResourceAsStream("/realworldmc-service.sha256")) {
            if (hash == null) return dataDirectory; // Developer JARs use an external service.
            String expected = new String(hash.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            try (var archive = BundledService.class.getResourceAsStream(RESOURCE)) {
                if (archive == null) throw new IOException("Bundled service archive missing");
                return unpack(dataDirectory, archive, expected);
            }
        }
    }

    static Path unpack(Path dataDirectory, InputStream archive, String expected) throws IOException {
        if (!expected.matches("[a-f0-9]{64}")) throw new IOException("Invalid bundle checksum");
        Files.createDirectories(dataDirectory);
        Path data = dataDirectory.toRealPath();
        Path bundles = data.resolve(".bundles");
        if (Files.isSymbolicLink(bundles)) throw new IOException("Bundle folder must not be a link");
        Files.createDirectories(bundles);
        if (!bundles.toRealPath().startsWith(data)) throw new IOException("Bundle folder escapes instance");
        Path destination = bundles.resolve(expected);
        Path lockFile = bundles.resolve("install.lock");
        if (Files.isSymbolicLink(lockFile)) throw new IOException("Bundle lock must not be a link");
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another instance is preparing the service");
            if (Files.exists(destination)) {
                if (Files.isSymbolicLink(destination) || !Files.isRegularFile(destination.resolve(".complete")))
                    throw new IOException("Incomplete bundle folder; reinstall the release");
                return destination;
            }
            Path staging = Files.createTempDirectory(bundles, "unpack-");
            try {
                MessageDigest digest;
                try { digest = MessageDigest.getInstance("SHA-256"); }
                catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
                Path payload = staging.resolve("payload.zip");
                try (var checked = new DigestInputStream(archive, digest)) { Files.copy(checked, payload); }
                if (!HexFormat.of().formatHex(digest.digest()).equals(expected)) throw new IOException("Bundle checksum mismatch");
                try (var zip = new ZipFile(payload.toFile())) {
                    var entries = zip.entries();
                    long total = 0;
                    int count = 0;
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        String name = entry.getName();
                        if (++count > 20000 || name.contains("\\") || name.contains(":") || name.startsWith("/")
                            || Arrays.asList(name.split("/")).contains("..") || !allowed(name))
                            throw new IOException("Unsafe bundle entry");
                        Path target = staging.resolve(name).normalize();
                        if (!target.startsWith(staging)) throw new IOException("Bundle path escapes staging");
                        if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                        Files.createDirectories(target.getParent());
                        try (var input = zip.getInputStream(entry); var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                            byte[] buffer = new byte[65536];
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                total += read;
                                if (total > 512L * 1024 * 1024) throw new IOException("Bundle exceeds extraction limit");
                                output.write(buffer, 0, read);
                            }
                        }
                    }
                }
                for (String required : List.of("src/launcher.mjs", "src/server.mjs", "node_modules/jpeg-js/package.json", "runtime/win-x64/node.exe", "runtime/win-x64/LICENSE"))
                    if (!Files.isRegularFile(staging.resolve(required))) throw new IOException("Incomplete bundled service");
                Files.delete(payload);
                Files.writeString(staging.resolve(".complete"), expected);
                try { Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(staging, destination); }
                for (String example : List.of("config.example.json", "streetview.config.example.json")) {
                    if (Files.isRegularFile(destination.resolve(example)) && !Files.exists(data.resolve(example)))
                        Files.copy(destination.resolve(example), data.resolve(example));
                }
                return destination;
            } finally {
                if (Files.exists(staging)) try (var files = Files.walk(staging)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                }
            }
        }
    }

    private static boolean allowed(String name) {
        return name.startsWith("src/") || name.startsWith("preview/") || name.startsWith("node_modules/")
            || name.startsWith("runtime/") || Set.of("package.json", "package-lock.json", "config.example.json", "streetview.config.example.json").contains(name);
    }
}
