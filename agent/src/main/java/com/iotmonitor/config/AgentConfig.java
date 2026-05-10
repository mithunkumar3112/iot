package com.iotmonitor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

import java.util.List;

/**
 * Centralised configuration holder for the laptop agent.
 * All values can be overridden in application.properties or via
 * environment variables (Spring Boot convention: agent.backend-url →
 * AGENT_BACKEND_URL).
 */
@Configuration
public class AgentConfig {

    // -----------------------------------------------------------------------
    // Backend connection
    // -----------------------------------------------------------------------

    /** Full URL of the Spring Boot backend, e.g. http://192.168.1.10:8080 */
    @Value("${agent.backend-url:http://localhost:8080}")
    private String backendUrl;

    /** Device ID used by the laptop agent when polling commands */
    @Value("${agent.device-id:}")
    private String deviceId;

    /** Email used to log in at /auth/email-login */
    @Value("${agent.email:your@email.com}")
    private String email;

    /** Password for the above email */
    @Value("${agent.password:admin123}")
    private String password;

    @PostConstruct
    public void init() {
        if (deviceId == null || deviceId.isBlank()) {
            String username = System.getProperty("user.name", "unknown");
            String hostname = "pc"; // Simplified hostname
            deviceId = username + "-" + hostname + "-" + Integer.toHexString((username + hostname).hashCode()).substring(0, 4);
        }
    }

    // -----------------------------------------------------------------------
    // Task intervals
    // -----------------------------------------------------------------------

    /** How often (ms) to push CPU/RAM/battery metrics */
    @Value("${agent.metrics-interval-ms:5000}")
    private long metricsIntervalMs;

    /** How often (ms) to capture and push a screenshot */
    @Value("${agent.screenshot-interval-ms:10000}")
    private long screenshotIntervalMs;

    /** How often (ms) to run the full directory scan (safety net) */
    @Value("${agent.sync-interval-ms:30000}")
    private long syncIntervalMs;

    /** How often (ms) to poll /commands/latest */
    @Value("${agent.command-poll-interval-ms:5000}")
    private long commandPollIntervalMs;

    // -----------------------------------------------------------------------
    // File sync
    // -----------------------------------------------------------------------

    /** Maximum individual file size (bytes) to sync; larger files are skipped */
    @Value("${agent.max-file-size-bytes:52428800}") // 50 MB default
    private long maxFileSizeBytes;

    /**
     * Comma-separated list of absolute paths to watch.
     * Defaults to ~/Desktop, ~/Documents, ~/Downloads.
     */
    @Value("${agent.watched-dirs:}")
    private String watchedDirsRaw;

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public String getBackendUrl()            { return backendUrl; }
    public String getDeviceId()               { return deviceId; }
    public String getEmail()                 { return email; }
    public String getPassword()              { return password; }
    public long   getMetricsIntervalMs()     { return metricsIntervalMs; }
    public long   getScreenshotIntervalMs()  { return screenshotIntervalMs; }
    public long   getSyncIntervalMs()        { return syncIntervalMs; }
    public long   getCommandPollIntervalMs() { return commandPollIntervalMs; }
    public long   getMaxFileSizeBytes()      { return maxFileSizeBytes; }

    /**
     * Returns the configured watched directories. Falls back to the three
     * standard user folders when the property is empty.
     */
    public List<String> getWatchedDirs() {
        if (watchedDirsRaw != null && !watchedDirsRaw.isBlank()) {
            return List.of(watchedDirsRaw.split(","))
                       .stream()
                       .map(String::trim)
                       .filter(s -> !s.isEmpty())
                       .toList();
        }
        String home = System.getProperty("user.home");
        return List.of(
            home + "/Desktop",
            home + "/Documents",
            home + "/Downloads"
        );
    }
}