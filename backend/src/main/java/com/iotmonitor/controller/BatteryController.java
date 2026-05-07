package com.iotmonitor.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/system")
public class BatteryController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Store latest battery data per device
    private static final Map<String, BatteryData> latestBatteryData = new ConcurrentHashMap<>();


    @PostMapping("/battery")
    public Map<String, Object> receiveBatteryData(@RequestBody Map<String, Object> payload) {
        System.out.println("\n=== 🔋 BATTERY DATA RECEIVED ===");
        System.out.println("🔋 Timestamp: " + LocalDateTime.now());
        System.out.println("🔋 Payload: " + payload);

        String deviceId = (String) payload.get("deviceId");
        Number batteryNum = (Number) payload.get("battery");
        Boolean charging = (Boolean) payload.get("charging");

        Map<String, Object> response = new HashMap<>();

        // Validate required fields
        if (deviceId == null || batteryNum == null) {
            System.out.println("❌ VALIDATION ERROR: Missing required fields");
            response.put("success", false);
            response.put("message", "Missing required fields: deviceId, battery");
            return response;
        }

        double battery = batteryNum.doubleValue();

        // Validate battery range
        if (battery < 0 || battery > 100) {
            System.out.println("❌ VALIDATION ERROR: Invalid battery value: " + battery);
            response.put("success", false);
            response.put("message", "Battery must be between 0 and 100");
            return response;
        }

        // Store battery data
        BatteryData data = new BatteryData(deviceId, battery, charging != null ? charging : false, LocalDateTime.now());
        latestBatteryData.put(deviceId, data);

        System.out.println("✅ BATTERY STORED: Device=" + deviceId + " | Battery=" + battery + "% | Charging=" + (charging != null ? charging : false));
        System.out.println("=== END BATTERY ===\n");

        // Emit WebSocket event for real-time updates
        try {
            Map<String, Object> wsEvent = new HashMap<>();
            wsEvent.put("type", "BATTERY_UPDATE");
            wsEvent.put("event", "battery_update");
            wsEvent.put("data", Map.of(
                "deviceId", deviceId,
                "battery", battery,
                "charging", data.charging,
                "timestamp", data.timestamp
            ));
            messagingTemplate.convertAndSend("/topic/battery", wsEvent);
            System.out.println("✅ WebSocket battery update sent to /topic/battery");
        } catch (Exception e) {
            System.out.println("⚠️ WebSocket battery send failed: " + e.getMessage());
        }

        response.put("success", true);
        response.put("message", "Battery data recorded successfully");
        return response;
    }

    /**
     * Get devices that have reported battery data
     * GET /system/battery/devices
     */
    @GetMapping("/battery/devices")
    public List<String> getBatteryDevices() {
        return new ArrayList<>(latestBatteryData.keySet());
    }

    /**
     * Get latest battery data for a device
     * GET /system/battery/{deviceId}
     */
    @GetMapping("/battery/{deviceId}")
    public Map<String, Object> getBatteryData(@PathVariable String deviceId) {
        BatteryData data = latestBatteryData.get(deviceId);

        Map<String, Object> response = new HashMap<>();
        if (data != null) {
            response.put("deviceId", data.deviceId);
            response.put("battery", data.battery);
            response.put("charging", data.charging);
            response.put("timestamp", data.timestamp);
            response.put("found", true);
        } else {
            response.put("found", false);
            response.put("message", "No battery data found for device: " + deviceId);
        }

        return response;
    }

    /**
     * Inner class to store battery data
     */
    private static class BatteryData {
        String deviceId;
        double battery;
        boolean charging;
        LocalDateTime timestamp;

        BatteryData(String deviceId, double battery, boolean charging, LocalDateTime timestamp) {
            this.deviceId = deviceId;
            this.battery = battery;
            this.charging = charging;
            this.timestamp = timestamp;
        }
    }
}
