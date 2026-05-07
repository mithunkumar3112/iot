package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class SecurityEventReporter {
    private final ApiClient apiClient;
    private final ScreenMonitor screenMonitor;

    public SecurityEventReporter(ApiClient apiClient, ScreenMonitor screenMonitor) {
        this.apiClient = apiClient;
        this.screenMonitor = screenMonitor;
    }

    public void reportSecurityAlert(String type, String message, String severity, String source) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", apiClient.getDeviceId());
        payload.put("deviceName", apiClient.getDeviceId());
        payload.put("type", type);
        payload.put("message", message);
        payload.put("severity", severity);
        payload.put("source", source);
        payload.put("timestamp", LocalDateTime.now().toString());
        Map<String, Object> response = apiClient.sendSecurityAlert(payload);
        captureScreenshot(extractId(response), null, type);
    }

    public void reportScreenshotActivity(String processName, String trigger) {
        String appName = processName == null || processName.isBlank() ? "Unknown screenshot tool" : processName;
        String signal = trigger == null || trigger.isBlank() ? "screenshot activity" : trigger;
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", apiClient.getDeviceId());
        payload.put("deviceName", apiClient.getDeviceId());
        payload.put("processName", appName);
        payload.put("type", "SCREENSHOT_ACTIVITY");
        payload.put("message", "Screenshot activity detected: " + appName + " (" + signal + ")");
        payload.put("severity", "CRITICAL");
        payload.put("source", signal);
        payload.put("timestamp", LocalDateTime.now().toString());
        Map<String, Object> response = apiClient.sendSecurityAlert(payload);
        captureScreenshot(extractId(response), null, "SCREENSHOT_ACTIVITY");
    }

    public void reportUsbActivity(String deviceName, String connectionType, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", apiClient.getDeviceId());
        payload.put("deviceName", deviceName);
        payload.put("connectionType", connectionType);
        payload.put("status", status);
        payload.put("timestamp", LocalDateTime.now().toString());
        Map<String, Object> response = apiClient.sendUsbActivity(payload);
        captureScreenshot(null, extractId(response), "USB_" + status);
    }

    public void reportSession(String username, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", apiClient.getDeviceId());
        payload.put("username", username);
        payload.put("status", status);
        payload.put("loginTime", LocalDateTime.now().toString());
        apiClient.sendSessionUpdate(payload);
    }

    private void captureScreenshot(Long alertId, Long usbActivityId, String eventType) {
        if (screenMonitor == null) return;
        try {
            apiClient.sendSecurityScreenshot(screenMonitor.capturePng(), alertId, usbActivityId, eventType);
        } catch (Exception e) {
            System.err.println("Security screenshot capture skipped: " + e.getMessage());
        }
    }

    private Long extractId(Map<String, Object> response) {
        if (response == null || Boolean.TRUE.equals(response.get("duplicate"))) return null;
        Object id = response.get("id");
        if (id instanceof Number number) return number.longValue();
        if (id != null) {
            try {
                return Long.parseLong(String.valueOf(id));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
