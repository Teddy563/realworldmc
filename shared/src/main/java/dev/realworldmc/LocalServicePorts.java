package dev.realworldmc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/** Port availability is a bind check; HTTP health is only for service readiness. */
public final class LocalServicePorts {
    private LocalServicePorts() {}

    public static boolean available(int port) {
        try (var socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
