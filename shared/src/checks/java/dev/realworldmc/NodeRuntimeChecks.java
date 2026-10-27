package dev.realworldmc;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

/** Runs with only the JDK, including on macOS/Linux CI workers. */
public final class NodeRuntimeChecks {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Path script(Path root, String name, String body) throws IOException {
        Path file = root.resolve(name);
        Files.writeString(file, "#!/bin/sh\n" + body + "\n");
        if (!file.toFile().setExecutable(true)) throw new IOException("Cannot mark test runtime executable");
        return file;
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("RWMC Node paths with spaces ");
        try {
            int reservedPort;
            try (var reserved = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
                reservedPort = reserved.getLocalPort();
                check(!LocalServicePorts.available(reservedPort), "An occupied non-HTTP port must not be reused");
            }
            check(LocalServicePorts.available(reservedPort), "A released port must be available without an HTTP probe");
            check(NodeRuntime.supportedVersion("v22.0.0\n"), "Accept Node 22");
            check(NodeRuntime.supportedVersion("v24.21.0"), "Accept newer Node");
            check(!NodeRuntime.supportedVersion("v20.19.0"), "Reject old Node");
            check(!NodeRuntime.supportedVersion("not Node"), "Reject unrelated executables");
            check(NodeRuntime.executable(root, "/explicit/node").equals("/explicit/node"), "Preserve explicit configuration");
            Path versions = root.resolve("versions");
            for (String v : List.of("v9.11.2", "v22.9.0", "v22.10.0", "default", "v24.0.0")) Files.createDirectories(versions.resolve(v));
            var sorted = NodeRuntime.nvmCandidates(versions);
            check(sorted.size() == 4 && sorted.get(0).contains("v24.0.0") && sorted.get(1).contains("v22.10.0"), "Sort Node versions numerically");
            check(NodeRuntime.nvmCandidates(root.resolve("absent")).isEmpty(), "Missing NVM is optional");
            check(NodeRuntime.choose(List.of("node")).equals("node"), "Use working PATH installation");
            if (System.getProperty("os.name").startsWith("Windows")) {
                Path bundled = root.resolve("runtime/win-x64/node.exe");
                Files.createDirectories(bundled.getParent()); Files.writeString(bundled, "fixture");
                check(NodeRuntime.executable(root, "node").equals(bundled.toString()), "Prefer Windows bundle");
            } else {
                Path old = script(root, "old-node", "echo v20.19.0");
                Path supported = script(root, "new-node", "echo v22.10.0");
                Path denied = root.resolve("not-executable"); Files.writeString(denied, "echo v24.0.0");
                check(NodeRuntime.choose(List.of(denied.toString(), old.toString(), supported.toString())).equals(supported.toString()), "Skip non-executable and outdated installations");
                Path stalled = script(root, "stalled-node", "exec sleep 10");
                check(NodeRuntime.choose(List.of(stalled.toString(), supported.toString())).equals(supported.toString()), "Timed-out probes must not block fallback");
                try { NodeRuntime.choose(List.of(old.toString())); throw new AssertionError("Unsupported Node accepted"); }
                catch (IOException expected) { check(expected.getMessage().contains("22"), "Explain missing supported runtime"); }
            }
            System.out.println("Node runtime checks passed: version, ordering, paths, overrides and platform selection.");
        } finally {
            try (var files = Files.walk(root)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
}
