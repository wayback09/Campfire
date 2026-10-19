package com.wayback09.campfire;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Campfire High-Performance Go Relay Tunnel Client.
 * Connects to the Campfire Go relay server (sgp-two.enzonic.com:25631)
 * for NAT traversal and world tunneling.
 */
public final class RelayClient {
    public static final String DEFAULT_RELAY_HOST = "168.144.109.206";
    public static final int DEFAULT_RELAY_PORT = 25631;

    private static final Logger LOGGER = Logger.getLogger("CampfireRelay");

    public record RelaySession(String code, String host, int port, AutoCloseable tunnel) {}

    private static RelaySession activeSession = null;

    private RelayClient() {}

    public static synchronized RelaySession getActiveSession() {
        return activeSession;
    }

    public static synchronized void closeActiveSession() {
        if (activeSession != null) {
            try {
                activeSession.tunnel().close();
            } catch (Exception ignored) {}
            activeSession = null;
        }
    }

    public static CompletableFuture<RelaySession> openTunnel(int localPort) {
        return openTunnel(DEFAULT_RELAY_HOST, DEFAULT_RELAY_PORT, localPort);
    }

    public static CompletableFuture<RelaySession> openTunnel(String relayHost, int relayPort, int localPort) {
        CompletableFuture<RelaySession> future = new CompletableFuture<>();

        Thread worker = new Thread(() -> {
            try {
                LOGGER.info("[CampfireRelay] Connecting to relay at " + relayHost + ":" + relayPort);
                Socket controlSocket = new Socket();
                controlSocket.connect(new InetSocketAddress(relayHost, relayPort), 5000);
                controlSocket.setKeepAlive(true);

                OutputStream out = controlSocket.getOutputStream();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));

                out.write("CAMPFIRE_HOST\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                String line = reader.readLine();
                if (line == null || !line.startsWith("OK")) {
                    controlSocket.close();
                    LOGGER.severe("[CampfireRelay] Host registration rejected: " + line);
                    future.completeExceptionally(new IllegalStateException("Relay rejected host registration: " + line));
                    return;
                }

                String rawCode = line.substring(2).trim();
                String inviteCode = "CAMP-" + rawCode;
                LOGGER.info("[CampfireRelay] Tunnel active! Assigned Invite Code: " + inviteCode);

                AtomicBoolean running = new AtomicBoolean(true);

                AutoCloseable tunnel = () -> {
                    running.set(false);
                    try {
                        controlSocket.close();
                    } catch (Exception ignored) {}
                };

                RelaySession session = new RelaySession(inviteCode, relayHost, relayPort, tunnel);
                synchronized (RelayClient.class) {
                    closeActiveSession();
                    activeSession = session;
                }
                future.complete(session);

                long lastPing = System.currentTimeMillis();
                while (running.get() && !controlSocket.isClosed()) {
                    if (reader.ready()) {
                        String cmd = reader.readLine();
                        if (cmd == null) break;
                        cmd = cmd.trim();

                        if (cmd.startsWith("NEW_GUEST")) {
                            String[] parts = cmd.split("\\s+");
                            if (parts.length >= 2) {
                                String guestId = parts[1];
                                spawnGuestPipe(relayHost, relayPort, localPort, guestId);
                            }
                        }
                    }

                    if (System.currentTimeMillis() - lastPing > 10_000) {
                        try {
                            out.write("PING\n".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            lastPing = System.currentTimeMillis();
                        } catch (Exception e) {
                            LOGGER.warning("[CampfireRelay] Keepalive failed, closing tunnel: " + e.getMessage());
                            break;
                        }
                    }

                    Thread.sleep(100);
                }
                LOGGER.info("[CampfireRelay] Host session closed");

            } catch (Exception e) {
                LOGGER.severe("[CampfireRelay] Relay connection failed: " + e.getMessage());
                future.completeExceptionally(e);
            }
        }, "campfire-relay-host");

        worker.setDaemon(true);
        worker.start();
        return future;
    }

    private static void spawnGuestPipe(String relayHost, int relayPort, int localPort, String guestId) {
        Thread t = new Thread(() -> {
            try {
                Socket localServer = new Socket("127.0.0.1", localPort);
                Socket relayPipe = new Socket(relayHost, relayPort);

                relayPipe.getOutputStream().write(("CAMPFIRE_PIPE " + guestId + "\n").getBytes(StandardCharsets.UTF_8));
                relayPipe.getOutputStream().flush();

                Thread t1 = pipe(localServer, relayPipe);
                Thread t2 = pipe(relayPipe, localServer);

                t1.join();
                t2.join();

                localServer.close();
                relayPipe.close();
            } catch (Exception e) {
                LOGGER.warning("[CampfireRelay] Guest pipe closed: " + e.getMessage());
            }
        }, "campfire-guest-pipe-" + guestId);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Creates a local loopback forwarder for connecting to a relay-hosted world by code.
     */
    public static CompletableFuture<InetSocketAddress> startGuestForwarder(String rawCode) {
        CompletableFuture<InetSocketAddress> future = new CompletableFuture<>();

        Thread t = new Thread(() -> {
            try {
                ServerSocket localServer = new ServerSocket(0);
                int localPort = localServer.getLocalPort();
                future.complete(new InetSocketAddress("127.0.0.1", localPort));

                Socket clientSocket = localServer.accept();
                localServer.close();

                Socket relaySocket = new Socket(DEFAULT_RELAY_HOST, DEFAULT_RELAY_PORT);
                relaySocket.getOutputStream().write(("CAMPFIRE_JOIN " + rawCode + "\n").getBytes(StandardCharsets.UTF_8));
                relaySocket.getOutputStream().flush();

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(relaySocket.getInputStream(), StandardCharsets.UTF_8));
                String ack = reader.readLine();
                if (ack == null || !ack.startsWith("OK")) {
                    LOGGER.severe("[CampfireRelay] Relay rejected join for CAMP-" + rawCode + ": " + ack);
                    clientSocket.close();
                    relaySocket.close();
                    return;
                }

                LOGGER.info("[CampfireRelay] Bridge connected to world CAMP-" + rawCode);
                Thread t1 = pipe(clientSocket, relaySocket);
                Thread t2 = pipe(relaySocket, clientSocket);

                t1.join();
                t2.join();

                clientSocket.close();
                relaySocket.close();
            } catch (Exception e) {
                LOGGER.severe("[CampfireRelay] Guest forwarder error: " + e.getMessage());
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
            }
        }, "campfire-relay-guest");

        t.setDaemon(true);
        t.start();
        return future;
    }

    private static Thread pipe(Socket from, Socket to) {
        Thread t = new Thread(() -> {
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                in.transferTo(out);
            } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
