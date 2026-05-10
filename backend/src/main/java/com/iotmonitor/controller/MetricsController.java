package com.iotmonitor.controller;

import com.iotmonitor.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    @Autowired
    private AlertService alertService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

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

        System.out.println("📊 Received metrics from agent: CPU=" + json.get("cpu") + "%, RAM=" + json.get("ram") + "%, Battery=" + json.get("battery") + "%, Uptime=" + json.get("uptime") + "s");

        // Broadcast metrics update via WebSocket
        messagingTemplate.convertAndSend("/topic/metrics", json);

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

}
