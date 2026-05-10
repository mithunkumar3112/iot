package com.iotmonitor.service;

import com.iotmonitor.config.AgentConfig;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects system metrics every {@code agent.metrics-interval-ms} milliseconds
 * and POSTs them to {@code /metrics}.
 *
 * Metrics collected:
 *  - cpu     : system-wide CPU load percentage  (0–100)
 *  - ram     : used heap / total heap percentage (0–100)
 *  - uptime  : JVM uptime in seconds
 *  - battery : always -1 on desktop JVMs (no native battery API in pure Java)
 *
 * Note: The {@code com.sun.management.OperatingSystemMXBean} extension is
 * available on all Oracle/OpenJDK JVMs and gives CPU load without any
 * native library. Battery information requires a native call or OS command;
 * a cross-platform approach using {@code Runtime.exec} is included for
 * Windows and Linux / macOS.
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final AgentConfig config;
    private final AuthService authService;
    private final RestTemplate rest = new RestTemplate();

    private final OperatingSystemMXBean osMxBean;
    private final long startTimeMs = System.currentTimeMillis();

    @PostConstruct
    public void init() {
        System.out.println("📊 MetricsService initialized");
    }

    public MetricsService(AgentConfig config, AuthService authService) {
        this.config      = config;
        this.authService = authService;
        this.osMxBean    = (OperatingSystemMXBean)
                ManagementFactory.getOperatingSystemMXBean();
    }

    // -----------------------------------------------------------------------
    // Scheduled task
    // -----------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${agent.metrics-interval-ms:5000}")
    public void pushMetrics() {
        System.out.println("🔄 Pushing metrics to backend...");
        try {
            double cpu    = osMxBean.getCpuLoad() * 100.0;
            if (cpu < 0) cpu = osMxBean.getProcessCpuLoad() * 100.0; // fallback

            long totalMem = osMxBean.getTotalMemorySize();
            long freeMem  = osMxBean.getFreeMemorySize();
            double ram    = totalMem > 0
                            ? (double)(totalMem - freeMem) / totalMem * 100.0
                            : 0.0;

            long uptimeSec = (System.currentTimeMillis() - startTimeMs) / 1000;
            double battery = readBatteryPercent();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cpu",     Math.max(0, Math.min(100, cpu)));
            payload.put("ram",     Math.max(0, Math.min(100, ram)));
            payload.put("uptime",  uptimeSec);
            payload.put("battery", battery);
            payload.put("deviceId", config.getDeviceId());

            HttpEntity<Map<String, Object>> req =
                new HttpEntity<>(payload, jsonAuthHeaders());

            rest.postForEntity(config.getBackendUrl() + "/metrics", req, Void.class);
            log.debug("Metrics pushed — CPU: {:.1f}% RAM: {:.1f}%", cpu, ram);
            System.out.println("📊 Metrics sent to " + config.getBackendUrl() + "/metrics - CPU: " + String.format("%.1f", cpu) + "% RAM: " + String.format("%.1f", ram) + "%");

        } catch (HttpClientErrorException.Unauthorized e) {
            authService.invalidateToken();
            System.err.println("❌ Metrics upload failed: Unauthorized - invalid token");
        } catch (Exception ex) {
            log.warn("Metrics push failed: {}", ex.getMessage());
            System.err.println("❌ Metrics upload failed: " + ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Battery (best-effort, cross-platform)
    // -----------------------------------------------------------------------

    private double readBatteryPercent() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();

            if (os.contains("win")) {
                // Windows: WMIC
                Process p = Runtime.getRuntime()
                    .exec("WMIC Path Win32_Battery Get EstimatedChargeRemaining");
                String out = new String(p.getInputStream().readAllBytes()).trim();
                for (String line : out.split("\\r?\\n")) {
                    line = line.trim();
                    if (line.matches("\\d+")) return Double.parseDouble(line);
                }

            } else if (os.contains("linux")) {
                // Linux: /sys/class/power_supply/BAT0/capacity
                java.nio.file.Path batPath =
                    java.nio.file.Paths.get("/sys/class/power_supply/BAT0/capacity");
                if (java.nio.file.Files.exists(batPath)) {
                    return Double.parseDouble(
                        java.nio.file.Files.readString(batPath).trim());
                }

            } else if (os.contains("mac")) {
                // macOS: pmset -g batt
                Process p = Runtime.getRuntime().exec("pmset -g batt");
                String out = new String(p.getInputStream().readAllBytes());
                java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(\\d+)%").matcher(out);
                if (m.find()) return Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) { /* no battery or permission denied */ }
        return -1.0;
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private HttpHeaders jsonAuthHeaders() {
        HttpHeaders h = authService.authHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}