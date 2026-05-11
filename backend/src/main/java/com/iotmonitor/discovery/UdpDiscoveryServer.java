package com.iotmonitor.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Listens on UDP port for Android broadcast discovery packets.
 * When Android sends {"email":"user@example.com"} and it matches
 * app.owner.email, this server replies with the PC's local IP + HTTP port.
 *
 * This allows the Android phone to automatically find the PC on the same WiFi
 * without scanning any QR code or manually entering an IP address.
 */
@Component
public class UdpDiscoveryServer {

    @Value("${app.owner.email}")
    private String ownerEmail;

    @Value("${app.discovery.udp.port:45678}")
    private int udpPort;

    @Value("${server.port:8080}")
    private int httpPort;

    private DatagramSocket socket;
    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        Thread thread = new Thread(() -> {
            try {
                socket = new DatagramSocket(udpPort);
                socket.setSoTimeout(0); // block forever until packet or close
                byte[] buf = new byte[512];
                System.out.println("[UdpDiscovery] Listening on UDP port " + udpPort + " for email: " + ownerEmail);

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    try {
                        socket.receive(packet);
                    } catch (SocketException e) {
                        // Socket closed during shutdown — expected
                        break;
                    }

                    String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    System.out.println("[UdpDiscovery] Received: " + received + " from " + packet.getAddress());

                    // Parse email from simple JSON {"email":"..."}
                    String incomingEmail = extractField(received, "email");

                    if (ownerEmail != null && ownerEmail.equalsIgnoreCase(incomingEmail)) {
                        // Email matches — respond with our local IP and HTTP port
                        String localIp = getLocalIpAddress();
                        String response = "{\"ip\":\"" + localIp + "\",\"port\":" + httpPort + "}";
                        byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket reply = new DatagramPacket(
                                respBytes, respBytes.length,
                                packet.getAddress(), packet.getPort()
                        );
                        socket.send(reply);
                        System.out.println("[UdpDiscovery] Replied to " + packet.getAddress() + " with " + response);
                    } else {
                        System.out.println("[UdpDiscovery] Email mismatch — ignoring.");
                    }
                }
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.setName("udp-discovery-server");
        thread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /** Naive JSON string field extractor (avoids extra dependency). */
    private String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx + key.length());
        if (colon < 0) return null;
        int start = json.indexOf("\"", colon + 1);
        if (start < 0) return null;
        int end = json.indexOf("\"", start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end).trim();
    }

    /** Gets the machine's LAN IP (first non-loopback IPv4 address). */
    private String getLocalIpAddress() {
        try {
            // Try to connect to a public host to get the preferred outbound NIC IP
            try (DatagramSocket s = new DatagramSocket()) {
                s.connect(InetAddress.getByName("8.8.8.8"), 80);
                return s.getLocalAddress().getHostAddress();
            }
        } catch (Exception e) {
            // Fallback: iterate interfaces
            try {
                var interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    var iface = interfaces.nextElement();
                    if (iface.isLoopback() || !iface.isUp()) continue;
                    var addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return "";
        }
    }
}
