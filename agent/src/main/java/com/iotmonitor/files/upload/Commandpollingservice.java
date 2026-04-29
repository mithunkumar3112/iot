

package com.iotmonitor.files.upload;

import com.iotmonitor.config.AgentConfig;
import com.iotmonitor.service.AuthService;
import com.iotmonitor.service.FileSyncService;
import com.iotmonitor.service.ScreenshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Polls the backend's /commands/{deviceId} endpoint every
 * {@code agent.command-poll-interval-ms} milliseconds for new device commands
 * and executes them if found.
 *
 * Supported commands:
 *  - TAKE_SCREENSHOT : Capture and upload an immediate screenshot
 *  - SYNC_FILES      : Trigger a full file sync scan
 *  - DELETE_FILE     : Optional placeholder command (not implemented)
 *  - SHUTDOWN        : Shut down the host machine
 *  - RESTART_AGENT   : Exit the JVM so an external wrapper may restart it
 */
@Service
public class Commandpollingservice {

    private static final Logger log = LoggerFactory.getLogger(Commandpollingservice.class);

    private final AgentConfig config;
    private final AuthService authService;
    private final ScreenshotService screenshotService;
    private final FileSyncService fileSyncService;
    private final RestTemplate rest = new RestTemplate();

    public Commandpollingservice(AgentConfig config,
                                 AuthService authService,
                                 ScreenshotService screenshotService,
                                 FileSyncService fileSyncService) {
        this.config = config;
        this.authService = authService;
        this.screenshotService = screenshotService;
        this.fileSyncService = fileSyncService;
    }

    // -----------------------------------------------------------------------
    // Scheduled task
    // -----------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${agent.command-poll-interval-ms:5000}")
    public void pollCommand() {
        try {
            String deviceId = config.getDeviceId();
            if (deviceId == null || deviceId.isBlank()) {
                log.warn("Agent deviceId is not configured; skipping command poll");
                return;
            }

            HttpEntity<Void> request = new HttpEntity<>(authService.authHeaders());
            ResponseEntity<String> response = rest.exchange(
                config.getBackendUrl() + "/commands/" + deviceId,
                HttpMethod.GET,
                request,
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) return;

            String command = stripQuotes(response.getBody());
            if (command == null || command.isBlank() || command.equalsIgnoreCase("NONE")) return;

            log.info("Received command for {}: {}", deviceId, command);
            handleCommand(command, deviceId);

        } catch (HttpClientErrorException.Unauthorized e) {
            authService.invalidateToken();
        } catch (Exception ex) {
            log.debug("Command poll error: {}", ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Command handlers
    // -----------------------------------------------------------------------

    private void handleCommand(String command, String deviceId) {
        String normalized = command.trim().toUpperCase();
        switch (normalized) {
            case "TAKE_SCREENSHOT" -> executeScreenshot(deviceId, normalized);
            case "SYNC_FILES" -> executeSyncFiles(deviceId, normalized);
            case "DELETE_FILE" -> executeDeleteFile(deviceId, normalized);
            case "SHUTDOWN" -> executeShutdown(deviceId, normalized);
            case "RESTART_AGENT" -> executeRestartAgent(deviceId, normalized);
            default -> {
                log.warn("Unknown command received: {}", normalized);
                sendCommandResult(deviceId, normalized, "FAILED");
            }
        }
    }

    private void executeScreenshot(String deviceId, String command) {
        log.info("Executing TAKE_SCREENSHOT command");
        boolean success = screenshotService.captureAndUploadNow();
        sendCommandResult(deviceId, command, success ? "SUCCESS" : "FAILED");
    }

    private void executeSyncFiles(String deviceId, String command) {
        log.info("Executing SYNC_FILES command");
        try {
            fileSyncService.fullScanSync();
            sendCommandResult(deviceId, command, "SUCCESS");
        } catch (Exception ex) {
            log.error("Failed to execute SYNC_FILES: {}", ex.getMessage());
            sendCommandResult(deviceId, command, "FAILED");
        }
    }

    private void executeDeleteFile(String deviceId, String command) {
        log.info("DELETE_FILE command received, but no path was provided");
        sendCommandResult(deviceId, command, "FAILED");
    }

    private void executeShutdown(String deviceId, String command) {
        sendCommandResult(deviceId, command, "SUCCESS");
        log.info("Executing SHUTDOWN command");
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                Runtime.getRuntime().exec("shutdown /s /t 0");
            } else {
                Runtime.getRuntime().exec("shutdown -h now");
            }
        } catch (IOException ex) {
            log.error("Shutdown OS command failed: {}", ex.getMessage());
        }
        System.exit(0);
    }

    private void executeRestartAgent(String deviceId, String command) {
        sendCommandResult(deviceId, command, "SUCCESS");
        log.info("Executing RESTART_AGENT command – exiting JVM for restart");
        System.exit(2);
    }

    // -----------------------------------------------------------------------
    // Result reporting
    // -----------------------------------------------------------------------

    private void sendCommandResult(String deviceId, String command, String status) {
        try {
            String url = config.getBackendUrl() + "/commands/result";
            HttpHeaders headers = authService.authHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> payload = Map.of(
                    "deviceId", deviceId,
                    "command", command,
                    "status", status,
                    "timestamp", Instant.now().toString()
            );

            rest.postForEntity(url, new HttpEntity<>(payload, headers), Void.class);
            log.info("Command result posted: {} = {}", command, status);
        } catch (Exception ex) {
            log.warn("Failed to post command result: {}", ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /** Strip surrounding JSON quotes that the backend may include in the response body */
    private String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }
}
