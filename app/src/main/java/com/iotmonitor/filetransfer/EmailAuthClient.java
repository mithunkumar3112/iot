package com.iotmonitor.filetransfer;

import com.iotmonitor.reverselink.network.UdpDiscoveryClient;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class EmailAuthClient {

    private String baseUrl;
    private String token;

    /** Legacy constructor for direct-URL usage. */
    public EmailAuthClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Default constructor for use with discoverAndConnect(). */
    public EmailAuthClient() {
    }

    /**
     * Main entry point: discover PC on LAN via UDP, then authenticate with email.
     * No QR code or manual IP needed.
     *
     * @param email The email address signed in on both PC and phone.
     * @return true on success; baseUrl and token are then available.
     */
    public boolean discoverAndConnect(String email) {
        try {
            // Step 1: broadcast UDP to find PC's IP
            String pcBaseUrl = UdpDiscoveryClient.broadcast(email, 5000);
            if (pcBaseUrl == null) {
                return false; // no PC responded
            }
            this.baseUrl = pcBaseUrl;

            // Step 2: call /reverselink/discover to get JWT token
            URL url = new URL(baseUrl + "/reverselink/discover");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String body = "{\"email\":\"" + email + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                    String response = scanner.useDelimiter("\\A").hasNext() ? scanner.useDelimiter("\\A").next() : "";
                    JSONObject json = new JSONObject(response);
                    this.token = json.getString("token");
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Legacy email + password login (backup method). */
    public boolean login(String email, String password) {
        try {
            URL url = new URL(baseUrl + "/auth/email-login");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonInputString = String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() == 200) {
                try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                    String response = scanner.useDelimiter("\\A").hasNext() ? scanner.useDelimiter("\\A").next() : "";
                    JSONObject jsonObject = new JSONObject(response);
                    this.token = jsonObject.getString("token");
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getToken() {
        return token;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
