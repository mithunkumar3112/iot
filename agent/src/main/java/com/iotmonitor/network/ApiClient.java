package com.iotmonitor.network;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {

    private final String baseUrl;
    private String token;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        authenticate();
    }

    // 🔐 LOGIN
    private void authenticate() {
        try {
            URL url = new URL(baseUrl + "/auth/login");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            String body = "{\"username\":\"admin\",\"password\":\"admin123\"}";

            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes());
            }

            String response = new String(con.getInputStream().readAllBytes());
            token = response.split(":")[1]
                    .replace("\"", "")
                    .replace("}", "");

            System.out.println("🔑 Agent authenticated");

        } catch (Exception e) {
            System.out.println("❌ Authentication failed");
        }
    }

    // 📊 SEND METRICS
    public void sendMetrics(String json) {
        try {
            HttpURLConnection con =
                    openConnection("/metrics", "POST", "application/json");

            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes());
            }

            System.out.println("📊 Metrics sent | HTTP " + con.getResponseCode());

        } catch (Exception e) {
            System.out.println("⚠ Token expired, re-authenticating");
            authenticate();
        }
    }
// 🔍 CHECK COMMAND STATUS
public boolean isMonitoringEnabled() {
    try {
        HttpURLConnection con =
                openConnection("/commands/status", "GET", null);

        String response =
                new String(con.getInputStream().readAllBytes());

        return Boolean.parseBoolean(response);

    } catch (Exception e) {
        return true; // default ON
    }
}

    // 🖼 SEND SCREENSHOT (FIXED)
public void uploadScreenshot(byte[] image) {
    try {
        HttpURLConnection con =
                openConnection("/screenshot", "POST", "application/octet-stream");

        try (OutputStream os = con.getOutputStream()) {
            os.write(image);
        }

        System.out.println("🖼 Screenshot sent | HTTP " + con.getResponseCode());

    } catch (Exception e) {
        System.out.println("⚠ Screenshot failed");
    }
}
// 🖥onff
public String getLatestCommand() {
    try {
        HttpURLConnection con =
                openConnection("/commands/latest", "GET", null);
        return new String(con.getInputStream().readAllBytes());
    } catch (Exception e) {
        return "NONE";
    }
}



    // 🔧 CONNECTION HELPER
    private HttpURLConnection openConnection(
            String path,
            String method,
            String contentType) throws Exception {

        URL url = new URL(baseUrl + path);
        HttpURLConnection con =
                (HttpURLConnection) url.openConnection();

        con.setRequestMethod(method);
        con.setRequestProperty("Authorization", "Bearer " + token);
        con.setRequestProperty("Content-Type", contentType);
        con.setDoOutput(true);

        return con;
    }
}
