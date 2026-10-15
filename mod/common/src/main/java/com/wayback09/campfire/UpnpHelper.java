package com.wayback09.campfire;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Lightweight, zero-dependency UPnP & Network helper for Campfire.
 * Discovers router gateway via SSDP and requests automatic port forwarding,
 * allowing friends to join across the internet without manual port-forwarding or a VPS.
 */
public final class UpnpHelper {
    public record HostNetworkInfo(String localIp, String publicIp, int port, boolean upnpSuccess) {}

    private UpnpHelper() {}

    /**
     * Resolves the primary local LAN IP.
     */
    public static String getLocalIp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 1000);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "127.0.0.1";
            }
        }
    }

    /**
     * Resolves the public IP address via lightweight public query.
     */
    public static String getPublicIp() {
        String[] providers = {
            "https://checkip.amazonaws.com",
            "https://api.ipify.org",
            "https://icanhazip.com"
        };
        for (String urlStr : providers) {
            try {
                URL url = URI.create(urlStr).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String ip = reader.readLine();
                    if (ip != null && !ip.isBlank()) {
                        return ip.trim();
                    }
                }
            } catch (Exception ignored) {}
        }
        return getLocalIp();
    }

    /**
     * Attempts to open the port via UPnP asynchronously.
     */
    public static CompletableFuture<HostNetworkInfo> openPort(int port) {
        return CompletableFuture.supplyAsync(() -> {
            String localIp = getLocalIp();
            boolean upnpSuccess = tryUpnp(localIp, port);
            String publicIp = upnpSuccess ? getPublicIp() : localIp;
            return new HostNetworkInfo(localIp, publicIp, port, upnpSuccess);
        });
    }

    private static boolean tryUpnp(String localIp, int port) {
        try {
            // SSDP discovery for WANIPConnection or WANPPPConnection
            String query = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "ST: urn:schemas-upnp-org:service:WANIPConnection:1\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n\r\n";

            byte[] sendData = query.getBytes(StandardCharsets.UTF_8);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                    InetAddress.getByName("239.255.255.250"), 1900);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(1500);
                socket.send(sendPacket);

                byte[] buffer = new byte[2048];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                String location = null;
                for (String line : response.split("\r\n")) {
                    if (line.toLowerCase().startsWith("location:")) {
                        location = line.substring(9).trim();
                        break;
                    }
                }

                if (location != null) {
                    return sendAddPortMapping(location, localIp, port);
                }
            }
        } catch (Exception ignored) {
            // UPnP not available or router rejected SSDP
        }
        return false;
    }

    private static boolean sendAddPortMapping(String location, String localIp, int port) {
        try {
            URL url = URI.create(location).toURL();
            String host = url.getProtocol() + "://" + url.getHost() + (url.getPort() > 0 ? ":" + url.getPort() : "");

            // Fetch device description to find controlURL
            HttpURLConnection descConn = (HttpURLConnection) url.openConnection();
            descConn.setConnectTimeout(2000);
            descConn.setReadTimeout(2000);
            String descXml;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(descConn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                descXml = sb.toString();
            }

            String controlUrl = null;
            String serviceType = "urn:schemas-upnp-org:service:WANIPConnection:1";
            if (descXml.contains("WANIPConnection:1")) {
                int serviceIdx = descXml.indexOf("WANIPConnection:1");
                int controlIdx = descXml.indexOf("<controlURL>", serviceIdx);
                if (controlIdx != -1) {
                    int endIdx = descXml.indexOf("</controlURL>", controlIdx);
                    controlUrl = descXml.substring(controlIdx + 12, endIdx).trim();
                }
            } else if (descXml.contains("WANPPPConnection:1")) {
                serviceType = "urn:schemas-upnp-org:service:WANPPPConnection:1";
                int serviceIdx = descXml.indexOf("WANPPPConnection:1");
                int controlIdx = descXml.indexOf("<controlURL>", serviceIdx);
                if (controlIdx != -1) {
                    int endIdx = descXml.indexOf("</controlURL>", controlIdx);
                    controlUrl = descXml.substring(controlIdx + 12, endIdx).trim();
                }
            }

            if (controlUrl == null) return false;
            String postUrl = controlUrl.startsWith("http") ? controlUrl : host + (controlUrl.startsWith("/") ? "" : "/") + controlUrl;

            String soap = "<?xml version=\"1.0\"?>\r\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
                    "<s:Body>\r\n" +
                    "<u:AddPortMapping xmlns:u=\"" + serviceType + "\">\r\n" +
                    "<NewRemoteHost></NewRemoteHost>\r\n" +
                    "<NewExternalPort>" + port + "</NewExternalPort>\r\n" +
                    "<NewProtocol>TCP</NewProtocol>\r\n" +
                    "<NewInternalPort>" + port + "</NewInternalPort>\r\n" +
                    "<NewInternalClient>" + localIp + "</NewInternalClient>\r\n" +
                    "<NewEnabled>1</NewEnabled>\r\n" +
                    "<NewPortMappingDescription>Campfire Minecraft</NewPortMappingDescription>\r\n" +
                    "<NewLeaseDuration>0</NewLeaseDuration>\r\n" +
                    "</u:AddPortMapping>\r\n" +
                    "</s:Body>\r\n" +
                    "</s:Envelope>";

            HttpURLConnection postConn = (HttpURLConnection) URI.create(postUrl).toURL().openConnection();
            postConn.setRequestMethod("POST");
            postConn.setDoOutput(true);
            postConn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            postConn.setRequestProperty("SOAPAction", "\"" + serviceType + "#AddPortMapping\"");
            postConn.setConnectTimeout(3000);
            postConn.setReadTimeout(3000);

            try (OutputStream os = postConn.getOutputStream()) {
                os.write(soap.getBytes(StandardCharsets.UTF_8));
            }

            return postConn.getResponseCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }
}
