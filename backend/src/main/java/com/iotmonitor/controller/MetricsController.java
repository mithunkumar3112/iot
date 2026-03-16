package com.iotmonitor.controller;

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

    // Stores latest metrics for dashboard
    private static Map<String, Object> latestMetrics = new HashMap<>();

    // Used for ONLINE / OFFLINE status
    private static Instant lastSeen = Instant.now();

    // ================================
    // 📊 RECEIVE METRICS FROM AGENT
    // ================================
    @PostMapping(consumes = "application/json")
    public void receiveMetrics(@RequestBody Map<String, Object> json) {

        latestMetrics = json;
        lastSeen = Instant.now();

        // Optional logging
        if (json.containsKey("cpu")) {
            double cpu = Double.parseDouble(json.get("cpu").toString());
            double ram = Double.parseDouble(json.get("ram").toString());

            if (cpu > 80) {
                System.out.println("⚠ HIGH CPU: " + cpu + "%");
            }
            if (ram > 80) {
                System.out.println("⚠ HIGH RAM: " + ram + "%");
            }
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

        Path path = Paths.get(
            "src/main/resources/static/screenshots/latest.png"
        );

        Files.createDirectories(path.getParent());
        Files.write(path, image);
    }
    
}
