package dev.realworldmc;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.nio.file.*;

public final class ServiceRuntimeChecks {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static int port() throws Exception { try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); } }
    public static void main(String[] args) throws Exception {
        Path game = Files.createTempDirectory("rwmc import with spaces ");
        Path dir = ServiceRuntime.directory(game, "");
        int map=port(), photo=port();
        while (photo==map) photo=port();
        var runtime = new ServiceRuntime(map, photo);
        var second = new ServiceRuntime(map, photo);
        try {
            check(dir.equals(game.resolve("realworldmc-service")), "Import path must resolve inside instance");
            check(ServiceRuntime.directory(game, dir.toString()).equals(dir), "Legacy absolute folders must work");
            Files.createDirectories(dir.resolve("src"));
            // Real child-process lifecycle, isolated ports, no map provider requests.
            Files.writeString(dir.resolve("src/launcher.mjs"), """
                import http from 'node:http';
                for (const port of [process.env.RWMC_PORT,process.env.RWMC_STREETVIEW_PORT]) {
                  http.createServer((req,res)=>res.end(JSON.stringify({ok:true,instanceId:process.env.RWMC_INSTANCE_ID}))).listen(Number(port),'127.0.0.1');
                }
                process.stdin.resume(); process.stdin.on('end',()=>process.exit(0));
                """);
            check(runtime.start(dir, "node"), "Both services must start: " + runtime.error());
            check(runtime.start(dir, "node"), "Repeated world requests must reuse one child");
            check(!second.start(dir, "node"), "Another game must not adopt this game's child");
            second.stop();
            check(runtime.start(dir, "node"), "Stopping a non-owner must not stop our child");
            runtime.stop();
            check(second.start(dir, "node"), "Ports must be released after game shutdown: " + second.error());
            second.stop();
            Files.delete(dir.resolve("src/launcher.mjs"));
            check(!runtime.start(dir, "node") && runtime.error().contains(".mrpack"), "Missing pack must have an actionable message");
            System.out.println("Portable paths, startup, reuse, ownership, shutdown, restart and missing-pack checks passed.");
        } finally {
            runtime.stop(); second.stop();
            try (var paths = Files.walk(game)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
}
