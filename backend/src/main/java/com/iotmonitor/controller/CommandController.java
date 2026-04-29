package com.iotmonitor.controller;

import com.iotmonitor.dto.CommandRequest;
import com.iotmonitor.dto.CommandResultRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/commands")
public class CommandController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 🔴🟢 Monitoring state
    private static final AtomicBoolean MONITORING_ENABLED =
            new AtomicBoolean(true);

    // 🧾 Dashboard command & result history
    private static final List<String> HISTORY = new ArrayList<>();

    // 📡 Latest global command for agent compatibility
    private static volatile String LAST_COMMAND = "NONE";

    // ✅ Pending per-device commands
    private static final Map<String, String> DEVICE_COMMANDS = new ConcurrentHashMap<>();

    // ==========================
    // 🔍 AGENT CHECKS STATUS
    // ==========================
    @GetMapping("/status")
    public boolean getStatus() {
        return MONITORING_ENABLED.get();
    }

    // ==========================
    // 🟢 TURN MONITORING ON
    // ==========================
    @PostMapping("/on")
    public void on() {
        MONITORING_ENABLED.set(true);
        HISTORY.add("ON at " + Instant.now());
        System.out.println("🟢 Monitoring ENABLED");
    }

    // ==========================
    // 🔴 TURN MONITORING OFF
    // ==========================
    @PostMapping("/off")
    public void off() {
        MONITORING_ENABLED.set(false);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        HISTORY.add("OFF at " + formatter.format(Instant.now()));
        System.out.println("🔴 Monitoring DISABLED");
    }

    // ==========================
    // ⏹ SHUTDOWN COMMAND
    // ==========================
    @PostMapping("/shutdown")
    public void shutdown() {
        LAST_COMMAND = "SHUTDOWN";
        HISTORY.add("SHUTDOWN at " + Instant.now());
        System.out.println("⏹ Shutdown command sent");
    }

    // ==========================
    // � SLEEP COMMAND
    // ==========================
    @PostMapping("/sleep")
    public void sleep() {
        LAST_COMMAND = "SLEEP";
        HISTORY.add("SLEEP at " + Instant.now());
        System.out.println("💤 Sleep command sent");
    }

    // ==========================
    // �🔁 RESTART AGENT
    // ==========================
    @PostMapping("/restart-agent")
    public void restartAgent() {
        LAST_COMMAND = "RESTART_AGENT";
        HISTORY.add("RESTART_AGENT at " + Instant.now());
        System.out.println("🔁 Restart agent command sent");
    }

    // ==========================
    // 📡 AGENT POLLS LATEST GLOBAL COMMAND
    // ==========================
    @GetMapping("/latest")
    public String latestCommand() {
        return LAST_COMMAND;
    }

    // ==========================
    // 📡 AGENT POLLS DEVICE-SPECIFIC COMMANDS
    // ==========================
    @GetMapping("/{deviceId}")
    public String getDeviceCommand(@PathVariable String deviceId) {
        String command = DEVICE_COMMANDS.remove(deviceId);
        return command == null ? "NONE" : command;
    }

    // ==========================
    // 📨 SEND COMMAND TO A DEVICE
    // ==========================
    @PostMapping("/send")
    public ResponseEntity<?> sendCommand(@RequestBody CommandRequest request) {
        if (request == null || request.getDeviceId() == null || request.getDeviceId().isBlank() ||
                request.getCommand() == null || request.getCommand().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "deviceId and command are required"));
        }

        String deviceId = request.getDeviceId().trim();
        String command = request.getCommand().trim().toUpperCase();
        DEVICE_COMMANDS.put(deviceId, command);
        HISTORY.add("SEND " + command + " to " + deviceId + " at " + Instant.now());
        System.out.println("📨 Command queued for " + deviceId + ": " + command);
        return ResponseEntity.ok(Map.of("status", "queued", "deviceId", deviceId, "command", command));
    }

    // ==========================
    // 📬 AGENT REPORTS COMMAND RESULT
    // ==========================
    @PostMapping("/result")
    public ResponseEntity<?> receiveCommandResult(@RequestBody CommandResultRequest result) {
        if (result == null || result.getDeviceId() == null || result.getDeviceId().isBlank() ||
                result.getCommand() == null || result.getCommand().isBlank() ||
                result.getStatus() == null || result.getStatus().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "deviceId, command, and status are required"));
        }

        String message = String.format("RESULT from %s: %s = %s at %s",
                result.getDeviceId().trim(),
                result.getCommand().trim(),
                result.getStatus().trim(),
                result.getTimestamp() == null ? Instant.now().toString() : result.getTimestamp().trim());

        HISTORY.add(message);
        System.out.println("✅ " + message);

        // Emit WebSocket event for command status
        try {
            Map<String, Object> wsEvent = Map.of(
                "type", "COMMAND_STATUS",
                "deviceId", result.getDeviceId().trim(),
                "command", result.getCommand().trim(),
                "status", result.getStatus().trim(),
                "timestamp", result.getTimestamp() != null ? result.getTimestamp().trim() : Instant.now().toString()
            );
            messagingTemplate.convertAndSend("/topic/commands", wsEvent);
            System.out.println("✅ WebSocket command status sent to /topic/commands");
        } catch (Exception e) {
            System.out.println("⚠️ WebSocket command status send failed: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @Autowired(required = false)
    private com.iotmonitor.service.SupabaseStorageService supabaseStorageService;

    // ==========================
    // 📜 HISTORY FOR DASHBOARD
    // ==========================
    @GetMapping("/history")
    public List<String> history(@RequestParam(required = false) String deviceId) {
        List<String> combinedHistory = new ArrayList<>(HISTORY);
        
        try {
            if (supabaseStorageService != null) {
                // Fetch process logs from Supabase
                List<Map<String, Object>> logs = supabaseStorageService.fetchProcessLogs(deviceId, 20);
                if (logs != null) {
                    for (Map<String, Object> log : logs) {
                        combinedHistory.add(String.format("📂 PROCESS: %s | CPU: %s%% | RAM: %sMB at %s",
                            log.get("process_name"), log.get("cpu_usage"), log.get("memory_usage"), log.get("timestamp")));
                    }
                }

                // Fetch detections from Supabase
                List<Map<String, Object>> detections = supabaseStorageService.fetchAppDetections(deviceId, 20);
                if (detections != null) {
                    for (Map<String, Object> det : detections) {
                        combinedHistory.add(String.format("🎯 ALERT: %s - %s at %s",
                            det.get("app_name"), det.get("message"), det.get("timestamp")));
                    }
                }
            } else {
                System.out.println("ℹ️ Supabase not configured, skipping cloud history fetch");
            }
        } catch (Exception e) {
            System.err.println("Error fetching Supabase history: " + e.getMessage());
        }

        // Return sorted by reverse chronological order (simple string sort for now, better than nothing)
        combinedHistory.sort((a, b) -> b.compareTo(a));
        return combinedHistory;
    }
}
