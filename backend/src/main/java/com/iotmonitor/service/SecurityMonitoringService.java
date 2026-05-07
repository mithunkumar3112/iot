package com.iotmonitor.service;

import com.iotmonitor.model.Alert;
import com.iotmonitor.model.LoginSession;
import com.iotmonitor.model.SecurityScreenshot;
import com.iotmonitor.model.UsbActivity;
import com.iotmonitor.repository.AlertRepository;
import com.iotmonitor.repository.LoginSessionRepository;
import com.iotmonitor.repository.SecurityScreenshotRepository;
import com.iotmonitor.repository.UsbActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SecurityMonitoringService {

    private final AlertRepository alertRepository;
    private final UsbActivityRepository usbActivityRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final SecurityScreenshotRepository screenshotRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    @Value("${security.alert.cooldown-ms:30000}")
    private long alertCooldownMs;

    @Value("${app.file.storage-dir:C:/shared-files/screenshots}")
    private String screenshotDir;

    public SecurityMonitoringService(AlertRepository alertRepository,
                                     UsbActivityRepository usbActivityRepository,
                                     LoginSessionRepository loginSessionRepository,
                                     SecurityScreenshotRepository screenshotRepository,
                                     SimpMessagingTemplate messagingTemplate) {
        this.alertRepository = alertRepository;
        this.usbActivityRepository = usbActivityRepository;
        this.loginSessionRepository = loginSessionRepository;
        this.screenshotRepository = screenshotRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Optional<Alert> recordSecurityAlert(Alert incoming) {
        String deviceId = clean(incoming.getDeviceId(), "unknown-device", 120);
        String type = clean(incoming.getType(), "SECURITY_ALERT", 80);
        String message = clean(incoming.getMessage(), type + " on " + deviceId, 500);
        String key = "alert:" + deviceId + ":" + type + ":" + message;
        if (isCoolingDown(key)) return Optional.empty();

        Alert alert = new Alert(deviceId, type, message, clean(incoming.getSeverity(), "HIGH", 20));
        alert.setDeviceName(clean(incoming.getDeviceName(), deviceId, 160));
        alert.setProcessName(clean(incoming.getProcessName(), "", 160));
        alert.setSource(clean(incoming.getSource(), "agent", 60));
        if (incoming.getTimestamp() != null) alert.setTimestamp(incoming.getTimestamp());
        Alert saved = alertRepository.save(alert);
        messagingTemplate.convertAndSend("/topic/security-alerts", event("security_alert", saved));
        messagingTemplate.convertAndSend("/topic/alerts", saved);
        return Optional.of(saved);
    }

    public Optional<UsbActivity> recordUsbActivity(UsbActivity incoming) {
        String deviceId = clean(incoming.getDeviceId(), "unknown-device", 120);
        String deviceName = clean(incoming.getDeviceName(), "USB device", 180);
        String connectionType = clean(incoming.getConnectionType(), classifyConnectionType(deviceName), 60).toUpperCase();
        String status = clean(incoming.getStatus(), "CONNECTED", 30).toUpperCase();
        String key = "usb:" + deviceId + ":" + deviceName + ":" + connectionType + ":" + status;
        if (isCoolingDown(key)) return Optional.empty();

        UsbActivity activity = new UsbActivity();
        activity.setDeviceId(deviceId);
        activity.setDeviceName(deviceName);
        activity.setConnectionType(connectionType);
        activity.setStatus(status);
        activity.setTimestamp(incoming.getTimestamp() != null ? incoming.getTimestamp() : LocalDateTime.now());
        UsbActivity saved = usbActivityRepository.save(activity);
        messagingTemplate.convertAndSend("/topic/usb-alerts", event("usb_alert", saved));

        Alert alert = new Alert(deviceId, "USB_" + status, connectionType + " " + status + ": " + deviceName, "HIGH");
        alert.setDeviceName(deviceName);
        alert.setSource("agent");
        Alert savedAlert = alertRepository.save(alert);
        messagingTemplate.convertAndSend("/topic/security-alerts", event("security_alert", savedAlert));
        messagingTemplate.convertAndSend("/topic/alerts", savedAlert);
        return Optional.of(saved);
    }

    public LoginSession recordSession(LoginSession incoming) {
        LoginSession session = new LoginSession();
        session.setDeviceId(clean(incoming.getDeviceId(), "unknown-device", 120));
        session.setUsername(clean(incoming.getUsername(), "unknown", 160));
        session.setStatus(clean(incoming.getStatus(), "ACTIVE", 40).toUpperCase());
        session.setLoginTime(incoming.getLoginTime() != null ? incoming.getLoginTime() : LocalDateTime.now());
        session.setLogoutTime(incoming.getLogoutTime());
        LoginSession saved = loginSessionRepository.save(session);
        messagingTemplate.convertAndSend("/topic/session-updates", event("session_update", saved));
        return saved;
    }

    public SecurityScreenshot saveScreenshot(String deviceId, Long alertId, Long usbActivityId, String eventType, byte[] image) throws IOException {
        String safeDeviceId = clean(deviceId, "unknown-device", 120).replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeEvent = clean(eventType, "security", 60).replaceAll("[^a-zA-Z0-9._-]", "_");
        Path dir = Paths.get(screenshotDir).resolve("security").resolve(safeDeviceId);
        Files.createDirectories(dir);
        String fileName = safeEvent + "-" + System.currentTimeMillis() + ".png";
        Path file = dir.resolve(fileName);
        Files.write(file, image);

        String url = "/security/screenshots/" + safeDeviceId + "/" + fileName;
        SecurityScreenshot shot = new SecurityScreenshot();
        shot.setDeviceId(safeDeviceId);
        shot.setAlertId(alertId);
        shot.setUsbActivityId(usbActivityId);
        shot.setEventType(safeEvent);
        shot.setFilePath(file.toString());
        shot.setUrl(url);
        shot.setFileSize(image.length);
        shot.setTimestamp(LocalDateTime.now());
        SecurityScreenshot saved = screenshotRepository.save(shot);

        if (alertId != null) {
            alertRepository.findById(alertId).ifPresent(alert -> {
                alert.setScreenshotUrl(url);
                alertRepository.save(alert);
                messagingTemplate.convertAndSend("/topic/security-alerts", event("security_alert", alert));
            });
        }
        if (usbActivityId != null) {
            usbActivityRepository.findById(usbActivityId).ifPresent(activity -> {
                activity.setScreenshotUrl(url);
                usbActivityRepository.save(activity);
                alertRepository.findTop100ByDeviceIdOrderByTimestampDesc(activity.getDeviceId()).stream()
                        .filter(alert -> ("USB_" + activity.getStatus()).equals(alert.getType()))
                        .filter(alert -> alert.getScreenshotUrl() == null || alert.getScreenshotUrl().isBlank())
                        .findFirst()
                        .ifPresent(alert -> {
                            alert.setScreenshotUrl(url);
                            alertRepository.save(alert);
                            messagingTemplate.convertAndSend("/topic/security-alerts", event("security_alert", alert));
                        });
                messagingTemplate.convertAndSend("/topic/usb-alerts", event("usb_alert", activity));
            });
        }
        return saved;
    }

    private boolean isCoolingDown(String key) {
        long now = System.currentTimeMillis();
        Long previous = cooldowns.put(key, now);
        return previous != null && now - previous < alertCooldownMs;
    }

    private Map<String, Object> event(String event, Object data) {
        return Map.of("event", event, "type", event, "data", data);
    }

    private String clean(String value, String fallback, int maxLength) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String classifyConnectionType(String deviceName) {
        String lower = deviceName == null ? "" : deviceName.toLowerCase();
        if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("apple mobile")) return "IPHONE";
        if (lower.contains("android") || lower.contains("adb") || lower.contains("mtp") || lower.contains("samsung")
                || lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("oneplus")
                || lower.contains("oppo") || lower.contains("vivo") || lower.contains("realme")
                || lower.contains("huawei") || lower.contains("motorola") || lower.contains("pixel")) {
            return "ANDROID_PHONE";
        }
        if (lower.contains("hdd") || lower.contains("hard drive") || lower.contains("external") || lower.contains("seagate")
                || lower.contains("wd ") || lower.contains("western digital") || lower.contains("toshiba")) {
            return "EXTERNAL_HDD";
        }
        if (lower.contains("flash") || lower.contains("pendrive") || lower.contains("pen drive") || lower.contains("sandisk")
                || lower.contains("kingston") || lower.contains("mass storage") || lower.contains("storage")) {
            return "PENDRIVE";
        }
        return "USB_DEVICE";
    }
}
