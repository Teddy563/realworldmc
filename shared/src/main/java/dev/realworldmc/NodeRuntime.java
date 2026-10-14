package dev.realworldmc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Locates a supported Node installation without depending on a launcher's shell setup. */
public final class NodeRuntime {
    private NodeRuntime() {}

    public static String executable(Path directory, String configured) throws IOException {
        if (configured != null && !configured.isBlank() && !configured.equals("node")) return configured;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "");
        Path bundled = directory.resolve("runtime/win-x64/node.exe");
        if (os.startsWith("windows") && (arch.equals("amd64") || arch.equals("x86_64")) && Files.isRegularFile(bundled))
            return bundled.toString();

        // A working PATH takes priority over other installations of Node.
        List<String> candidates = new ArrayList<>(List.of("node"));
        if (os.contains("mac") || os.contains("darwin") || os.contains("linux")) {
            Path home = Path.of(System.getProperty("user.home", "."));
            candidates.addAll(List.of(home.resolve(".local/node/bin/node").toString(),
                "/opt/homebrew/bin/node", "/usr/local/bin/node",
                home.resolve(".local/bin/node").toString(), "/usr/bin/node"));
            String nvm = System.getenv("NVM_DIR");
            Path versions = (nvm == null || nvm.isBlank() ? home.resolve(".nvm") : Path.of(nvm)).resolve("versions/node");
            candidates.addAll(nvmCandidates(versions));
        }
        return choose(candidates);
    }

    static List<String> nvmCandidates(Path versions) {
        if (!Files.isDirectory(versions)) return List.of();
        try (var dirs = Files.list(versions)) {
            return dirs.filter(Files::isDirectory).filter(p -> version(p) != null)
                .sorted(Comparator.comparing(NodeRuntime::version).reversed())
                .map(p -> p.resolve("bin/node").toString()).toList();
        } catch (IOException | SecurityException ignored) { return List.of(); }
    }

    private static Runtime.Version version(Path directory) {
        String name = directory.getFileName().toString();
        if (!name.matches("v\\d+\\.\\d+\\.\\d+")) return null;
        try { return Runtime.Version.parse(name.substring(1).replaceFirst("(?:\\.0)+$", "")); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    static boolean supportedVersion(String output) {
        if (!output.trim().matches("v\\d+\\.\\d+\\.\\d+")) return false;
        try { return Integer.parseInt(output.trim().split("\\.")[0].substring(1)) >= 22; }
        catch (NumberFormatException ignored) { return false; }
    }

    static String choose(List<String> candidates) throws IOException {
        for (String candidate : candidates) {
            if (!candidate.equals("node") && (!Files.isRegularFile(Path.of(candidate)) || !Files.isExecutable(Path.of(candidate)))) continue;
            Process probe = null;
            try {
                probe = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
                if (probe.waitFor(2, TimeUnit.SECONDS) && probe.exitValue() == 0
                    && supportedVersion(new String(probe.getInputStream().readNBytes(128), StandardCharsets.UTF_8))) return candidate;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Node detection interrupted", e);
            } catch (IOException | SecurityException ignored) {
                // Try the next installation if a candidate cannot run.
            } finally {
                if (probe != null) {
                    if (probe.isAlive()) probe.destroyForcibly();
                    try { probe.getInputStream().close(); probe.getOutputStream().close(); probe.getErrorStream().close(); }
                    catch (IOException ignored) {}
                }
            }
        }
        throw new IOException("Node.js 22 or newer was not found. Install Node 22+ or set nodeExecutable to its full path in config/realworldmc.json.");
    }
}
