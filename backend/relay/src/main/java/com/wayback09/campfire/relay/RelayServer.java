package com.wayback09.campfire.relay;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Author: wayback09.
 * Local TCP relay (Option B). Host + guest both dial out to the relay,
 * relay splices bytes both ways. Works behind NAT with zero port-forwarding.
 * Usage: RelayServer <listenPort>  (pairs arrive in twos: first=host, second=guest).
 */
public final class RelayServer {
    private RelayServer() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8091;
        try (var server = new ServerSocket(port)) {
            System.out.println("Campfire relay on 0.0.0.0:" + port + " (wayback09)");
            while (true) {
                Socket a = server.accept();
                Socket b = server.accept();
                new Thread(() -> splice(a, b), "campfire-relay").start();
            }
        }
    }

    private static void splice(Socket a, Socket b) {
        Thread t1 = pipe(a, b);
        Thread t2 = pipe(b, a);
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuiet(a);
            closeQuiet(b);
        }
    }

    private static Thread pipe(Socket from, Socket to) {
        Thread t = new Thread(() -> {
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                in.transferTo(out);
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void closeQuiet(Socket s) {
        try {
            s.close();
        } catch (Exception ignored) {}
    }
}
