package com.iotmonitor.reverselink.network;

import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Sends a UDP broadcast on the local network to discover a PC running
 * the IoT Monitor backend with the same registered email address.
 *
 * Usage:
 *   String baseUrl = UdpDiscoveryClient.broadcast("user@example.com", 5000);
 *   // baseUrl is like "http://192.168.1.5:8080" or null if no PC found.
 */
public class UdpDiscoveryClient {

    private static final int UDP_PORT = 45678;

    /**
     * Broadcasts a UDP packet with the given email and waits for a reply.
     *
     * @param email          The email to broadcast.
     * @param timeoutMillis  How long to wait for a reply.
     * @return Base URL of the responding PC (e.g. "http://192.168.1.5:8080"), or null.
     */
    public static String broadcast(String email, int timeoutMillis) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMillis);

            // Build discovery packet: {"email":"user@example.com","ts":123}
            String payload = "{\"email\":\"" + email + "\",\"ts\":" + System.currentTimeMillis() + "}";
            byte[] sendData = payload.getBytes(StandardCharsets.UTF_8);

            // Send to broadcast address
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcastAddr, UDP_PORT);
            socket.send(sendPacket);

            // Also send to subnet broadcast (covers more router configs)
            try {
                InetAddress localBroadcast = getLocalBroadcastAddress();
                if (localBroadcast != null && !localBroadcast.getHostAddress().equals("255.255.255.255")) {
                    DatagramPacket subnetPacket = new DatagramPacket(sendData, sendData.length, localBroadcast, UDP_PORT);
                    socket.send(subnetPacket);
                }
            } catch (Exception ignored) {}

            // Wait for reply: {"ip":"192.168.1.5","port":8080}
            byte[] buf = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
            socket.receive(receivePacket); // blocks until timeout or reply

            String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8).trim();

            // Parse ip and port from response
            String ip = extractField(response, "ip");
            String portStr = extractField(response, "port");

            if (ip != null && portStr != null) {
                int port = Integer.parseInt(portStr.trim());
                return "http://" + ip + ":" + port;
            }

        } catch (SocketTimeoutException e) {
            // No response within timeout — PC not found / email mismatch
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Gets the subnet broadcast address (e.g. 192.168.1.255). */
    private static InetAddress getLocalBroadcastAddress() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                var iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (InterfaceAddress ifAddr : iface.getInterfaceAddresses()) {
                    InetAddress broadcast = ifAddr.getBroadcast();
                    if (broadcast != null) return broadcast;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Naive JSON string/number field extractor. */
    private static String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx + key.length());
        if (colon < 0) return null;
        // Value may be a quoted string or a bare number
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            int end = json.indexOf("\"", start + 1);
            return end < 0 ? null : json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }
}
