package com.iotmonitor.controller;

import com.iotmonitor.service.AlertService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    @Autowired
    private AlertService alertService;

    // Directory where screenshots are stored (shared between backend + frontend)
    @Value("${app.file.storage-dir:C:/shared-files/screenshots}")
    private String screenshotDir;

    // Stores latest metrics for dashboard
    private static Map<String, Object> latestMetrics = new HashMap<>();

    // Used for ONLINE / OFFLINE status
    private static Instant lastSeen = Instant.now();

    private Path screenshotPath;

    @PostConstruct
    public void init() throws Exception {
        Path baseDir = Paths.get(screenshotDir);
        Files.createDirectories(baseDir);
        screenshotPath = baseDir.resolve("latest.png");
    }

    // ================================
    // 📊 RECEIVE METRICS FROM AGENT
    // ================================
    @PostMapping(consumes = "application/json")
    public void receiveMetrics(@RequestBody Map<String, Object> json) {

        latestMetrics = json;
        lastSeen = Instant.now();

        // Optional logging and alerts
        if (json.containsKey("cpu") && json.containsKey("ram")) {
            double cpu = Double.parseDouble(json.get("cpu").toString());
            double ram = Double.parseDouble(json.get("ram").toString());
            String deviceId = json.getOrDefault("deviceId", "default").toString();

            if (cpu > 80) {
                System.out.println("⚠ HIGH CPU: " + cpu + "%");
            }
            if (ram > 80) {
                System.out.println("⚠ HIGH RAM: " + ram + "%");
            }

            // Check for alerts
            alertService.checkAndCreateAlerts(deviceId, cpu, ram, 0); // processCpu not in metrics yet
        }
    }

    // ================================
    // 📈 SEND METRICS TO FRONTEND
    // ================================
    @GetMapping("/latest")
    public Map<String, Object> getLatestMetrics() {
        return latestMetrics;
    }

    // ================================
    // 🟢 ONLINE / 🔴 OFFLINE STATUS
    // ================================
    @GetMapping("/status")
    public String getStatus() {

        long seconds =
                Instant.now().getEpochSecond() -
                lastSeen.getEpochSecond();

        if (seconds > 15) {
            return "OFFLINE";
        }
        return "ONLINE";
    }

    // ================================
    // 🖼 RECEIVE SCREENSHOT FROM AGENT
    // ================================
    @PostMapping(
        value = "/screenshot",
        consumes = "application/octet-stream"
    )
    public void receiveScreenshot(@RequestBody byte[] image)
            throws Exception {

        Files.write(screenshotPath, image);
    }
    
}
