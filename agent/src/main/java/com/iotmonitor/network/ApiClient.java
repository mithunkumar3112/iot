package com.iotmonitor.network;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ApiClient {

    private final String backendUrl;
    private final String deviceId;
    private final RestTemplate restTemplate;

    public ApiClient(String backendUrl) {
        this.backendUrl = backendUrl;
        this.restTemplate = new RestTemplate();
        // Device ID can be configured via AGENT_DEVICE_ID environment variable.
        this.deviceId = System.getenv().getOrDefault("AGENT_DEVICE_ID", "Nandy-pc-66d0");
    }

    public void uploadFileToBackend(File file) {
        int maxRetries = 3;
        int retryCount = 0;
        boolean success = false;

        while (retryCount < maxRetries && !success) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new FileSystemResource(file));
                body.add("deviceId", deviceId);

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(backendUrl + "/upload", requestEntity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    System.out.println("✅ Sync success: " + file.getName());
                    success = true;
                } else {
                    System.err.println("❌ Sync failed (HTTP " + response.getStatusCode().value() + "): " + file.getName());
                    break;
                }

            } catch (Exception e) {
                retryCount++;
                System.err.println("⚠️ Network Error (Attempt " + retryCount + "): " + e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(2000); // 2 seconds delay
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    System.err.println("❌ Max retries reached for: " + file.getName());
                }
            }
        }
    }

    public void sendMetrics(String jsonData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> requestEntity = new HttpEntity<>(jsonData, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl + "/metrics", requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Metrics sent successfully");
            } else {
                System.err.println("❌ Failed to send metrics (HTTP " + response.getStatusCode().value() + ")");
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error sending metrics: " + e.getMessage());
        }
    }

    public void sendProcesses(Map<String, Object> processPayload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(processPayload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl + "/processes", requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Processes sent successfully to " + backendUrl + "/processes");
            } else {
                System.err.println("❌ Failed to send processes (HTTP " + response.getStatusCode().value() + ") to " + backendUrl + "/processes");
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error sending processes: " + e.getMessage());
        }
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getBackendUrl() {
        return backendUrl;
    }

    public void sendScreenshot(byte[] imageBytes) {
        sendSecurityScreenshot(imageBytes, null, null, "screenshot");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> sendSecurityAlert(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            payload.putIfAbsent("deviceId", deviceId);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(backendUrl + "/security/alerts", requestEntity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("Error sending security alert: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> sendUsbActivity(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            payload.putIfAbsent("deviceId", deviceId);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(backendUrl + "/security/usb", requestEntity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("Error sending USB activity: " + e.getMessage());
            return null;
        }
    }

    public void sendSecurityScreenshot(byte[] imageBytes, Long alertId, Long usbActivityId, String eventType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            StringBuilder url = new StringBuilder(backendUrl)
                    .append("/security/screenshot?deviceId=")
                    .append(URLEncoder.encode(deviceId, StandardCharsets.UTF_8))
                    .append("&eventType=")
                    .append(URLEncoder.encode(eventType == null ? "security" : eventType, StandardCharsets.UTF_8));
            if (alertId != null) url.append("&alertId=").append(alertId);
            if (usbActivityId != null) url.append("&usbActivityId=").append(usbActivityId);
            restTemplate.postForEntity(url.toString(), new HttpEntity<>(imageBytes, headers), String.class);
        } catch (Exception e) {
            System.err.println("Error sending security screenshot: " + e.getMessage());
        }
    }

    public void sendSessionUpdate(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            payload.putIfAbsent("deviceId", deviceId);
            restTemplate.postForEntity(backendUrl + "/sessions", new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            System.err.println("Error sending session update: " + e.getMessage());
        }
    }

    public String fetchPendingCommand(String deviceId) {
        try {
            String encodedDeviceId = URLEncoder.encode(deviceId, StandardCharsets.UTF_8);
            return restTemplate.getForObject(backendUrl + "/commands/" + encodedDeviceId, String.class);
        } catch (Exception e) {
            System.err.println("⚠️ Error polling command: " + e.getMessage());
            return null;
        }
    }

    public void postCommandResult(String deviceId, String command, String status) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> payload = Map.of(
                    "deviceId", deviceId,
                    "command", command,
                    "status", status,
                    "timestamp", java.time.Instant.now().toString()
            );

            restTemplate.postForEntity(backendUrl + "/commands/result", new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            System.err.println("⚠️ Error sending command result: " + e.getMessage());
        }
    }

    public void sendAppActivity(Map<String, Object> appActivityPayload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(appActivityPayload, headers);

            System.out.println("📤 Sending POST /apps/activity to: " + backendUrl + "/apps/activity");
            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl + "/apps/activity", requestEntity, String.class);
            
            System.out.println("📨 Response HTTP Status: " + response.getStatusCodeValue());
            System.out.println("📨 Response Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ App activity POST succeeded (HTTP " + response.getStatusCodeValue() + ")");
            } else {
                System.err.println("❌ Failed to send app activity (HTTP " + response.getStatusCode().value() + ")");
            }

        } catch (Exception e) {
            System.err.println("❌ Error sending app activity to " + backendUrl + "/apps/activity");
            System.err.println("   Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendBatteryData(Map<String, Object> batteryPayload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(batteryPayload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl + "/system/battery", requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Battery data sent successfully");
            } else {
                System.err.println("❌ Failed to send battery data (HTTP " + response.getStatusCode().value() + ")");
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error sending battery data: " + e.getMessage());
        }
    }

    public void sendActiveWindow(Map<String, Object> activeWindowPayload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(activeWindowPayload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl + "/activity/window", requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Active window sent successfully");
            } else {
                System.err.println("❌ Failed to send active window (HTTP " + response.getStatusCode().value() + ")");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error sending active window: " + e.getMessage());
        }
    }

    public void sendClipboardUpdate(Map<String, Object> clipboardPayload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(clipboardPayload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl + "/clipboard/update", requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Clipboard update sent successfully");
            } else {
                System.err.println("❌ Failed to send clipboard update (HTTP " + response.getStatusCode().value() + ")");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error sending clipboard update: " + e.getMessage());
        }
    }

}
