package com.iotmonitor.controller;

import com.iotmonitor.model.AppActivity;
import com.iotmonitor.repository.AppActivityRepository;
import com.iotmonitor.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/apps")
public class AppActivityController {

    @Autowired
    private AppActivityRepository appActivityRepository;
    
    @Autowired(required = false)
    private com.iotmonitor.service.SupabaseStorageService supabaseStorageService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AlertService alertService;

    /**
     * Receive app activity event from agent
     * POST /apps/activity
     * {
     *   "deviceId": "Laptop-1",
     *   "appName": "Spotify",
     *   "status": "OPENED",
     *   "appPath": "/Applications/Spotify.app" (optional)
     * }
     */
    @PostMapping("/activity")
    public Map<String, Object> receiveAppActivity(@RequestBody Map<String, Object> payload) {
        System.out.println("\n=== 📱 APP ACTIVITY RECEIVED ===");
        System.out.println("📱 Timestamp: " + LocalDateTime.now());
        System.out.println("📱 Payload: " + payload);

        String deviceId = (String) payload.get("deviceId");
        String appName = (String) payload.get("appName");
        String status = (String) payload.get("status");
        String appPath = (String) payload.get("appPath");

        Map<String, Object> response = new HashMap<>();

        // Validate required fields
        if (deviceId == null || deviceId.isBlank() || appName == null || appName.isBlank() || status == null || status.isBlank()) {
            System.out.println("❌ VALIDATION ERROR: Missing required fields");
            System.out.println("   - deviceId: " + deviceId);
            System.out.println("   - appName: " + appName);
            System.out.println("   - status: " + status);
            response.put("success", false);
            response.put("message", "Missing required fields: deviceId, appName, status");
            return response;
        }

        deviceId = deviceId.trim();
        appName = appName.trim();
        status = status.trim().toUpperCase();

        // Validate status
        if (!status.equals("OPENED") && !status.equals("CLOSED")) {
            System.out.println("❌ VALIDATION ERROR: Invalid status: " + status);
            response.put("success", false);
            response.put("message", "Invalid status. Must be OPENED or CLOSED");
            return response;
        }

        // Parse timestamp if provided
        LocalDateTime eventTime = LocalDateTime.now();
        String rawTimestamp = (String) payload.get("timestamp");
        if (rawTimestamp != null) {
            try {
                eventTime = java.time.OffsetDateTime.parse(rawTimestamp).toLocalDateTime();
                System.out.println("✅ Parsed timestamp: " + eventTime);
            } catch (Exception e) {
                System.out.println("⚠️ Failed to parse timestamp: " + rawTimestamp + " | Using server time instead");
                System.out.println("   Error: " + e.getMessage());
            }
        } else {
            System.out.println("ℹ️ No timestamp provided, using server time");
        }

        // Create and save app activity
        AppActivity activity = new AppActivity(deviceId, appName, status, appPath, eventTime);
        AppActivity saved = appActivityRepository.save(activity);
        System.out.println("✅ SAVED TO DB: ID=" + saved.getId() + " | Device=" + deviceId + " | App=" + appName + " | Status=" + status + " | Time=" + saved.getTimestamp());

        // Sync to Supabase if configured
        if (supabaseStorageService != null) {
            try {
                supabaseStorageService.logAppActivityToSupabase(deviceId, appName, status, rawTimestamp);
                System.out.println("✅ Synced to Supabase");
            } catch (Exception e) {
                System.out.println("⚠️ Supabase sync failed: " + e.getMessage());
            }
        } else {
            System.out.println("ℹ️ Supabase not configured, skipping sync");
        }

        // Emit WebSocket event for real-time updates
        try {
            Map<String, Object> wsEvent = new HashMap<>();
            wsEvent.put("type", "APP_EVENT");
            wsEvent.put("event", "app_activity");
            wsEvent.put("data", saved);
            messagingTemplate.convertAndSend("/topic/app-activity", wsEvent);
            System.out.println("✅ WebSocket event sent to /topic/app-activity");
        } catch (Exception e) {
            System.out.println("⚠️ WebSocket send failed: " + e.getMessage());
        }

        // Check if alert should be triggered (example: certain apps)
        if (status.equals("OPENED") && shouldTriggerAlert(appName)) {
            try {
                String alertMessage = appName + " opened on " + deviceId;
                alertService.recordAlert(deviceId, "APP_ALERT", alertMessage, "HIGH");
                Map<String, Object> alertEvent = new HashMap<>();
                alertEvent.put("type", "APP_ALERT");
                alertEvent.put("event", "alert");
                alertEvent.put("message", alertMessage);
                alertEvent.put("appName", appName);
                alertEvent.put("deviceId", deviceId);
                alertEvent.put("timestamp", LocalDateTime.now());
                messagingTemplate.convertAndSend("/topic/alerts", alertEvent);
                System.out.println("✅ Alert event sent for: " + appName);
            } catch (Exception e) {
                System.out.println("⚠️ Alert send failed: " + e.getMessage());
            }
        }

        response.put("success", true);
        response.put("message", "App activity recorded successfully");
        response.put("activity", saved);
        System.out.println("📤 Returning response: success=true, id=" + saved.getId());
        System.out.println("=== END APP ACTIVITY ===\n");
        return response;
    }

    /**
     * Get app activity history for a device
     * GET /apps/history?deviceId=Laptop-1
     */
    @GetMapping("/history")
    public List<AppActivity> getAppHistory(@RequestParam String deviceId) {
        System.out.println("\n=== 📱 GET APP HISTORY ===");
        System.out.println("📱 Requested for device: " + deviceId);
        System.out.println("📱 Timestamp: " + LocalDateTime.now());
        
        List<AppActivity> activities = appActivityRepository.findByDeviceIdOrderByTimestampDesc(deviceId);
        System.out.println("✅ FOUND " + activities.size() + " activities for device " + deviceId);
        
        if (activities.size() > 0) {
            System.out.println("📋 Sample records:");
            activities.stream().limit(5).forEach(a -> 
                System.out.println("   ✓ " + a.getAppName() + " (" + a.getStatus() + ") at " + a.getTimestamp())
            );
            if (activities.size() > 5) {
                System.out.println("   ... and " + (activities.size() - 5) + " more");
            }
        } else {
            System.out.println("⚠️ No activities found for device: " + deviceId);
        }
        
        return activities;
    }

    /**
     * Get app activity for a specific time range
     * GET /apps/history/range?deviceId=Laptop-1&startTime=2024-01-01T00:00:00&endTime=2024-01-02T00:00:00
     */
    @GetMapping("/history/range")
    public List<AppActivity> getAppHistoryByTimeRange(
            @RequestParam String deviceId,
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime) {
        return appActivityRepository.findByDeviceIdAndTimestampRange(deviceId, startTime, endTime);
    }

    /**
     * Get activities for specific app
     * GET /apps/activity-by-name?deviceId=Laptop-1&appName=Spotify&status=OPENED
     */
    @GetMapping("/activity-by-name")
    public List<AppActivity> getActivityByAppName(
            @RequestParam String deviceId,
            @RequestParam String appName,
            @RequestParam(required = false) String status) {
        if (status == null) {
            return appActivityRepository.searchByAppName(deviceId, appName);
        }
        return appActivityRepository.findByDeviceIdAndAppNameAndStatus(deviceId, appName, status);
    }

    /**
     * Get opened/closed apps only
     * GET /apps/opened?deviceId=Laptop-1
     * GET /apps/closed?deviceId=Laptop-1
     */
    @GetMapping("/opened")
    public List<AppActivity> getOpenedApps(@RequestParam String deviceId) {
        return appActivityRepository.findByDeviceIdAndStatusOrderByTimestampDesc(deviceId, "OPENED");
    }

    @GetMapping("/closed")
    public List<AppActivity> getClosedApps(@RequestParam String deviceId) {
        return appActivityRepository.findByDeviceIdAndStatusOrderByTimestampDesc(deviceId, "CLOSED");
    }

    /**
     * Search for app activity
     * GET /apps/search?deviceId=Laptop-1&searchTerm=Chrome
     */
    @GetMapping("/search")
    public List<AppActivity> searchAppActivity(
            @RequestParam String deviceId,
            @RequestParam String searchTerm) {
        return appActivityRepository.searchByAppName(deviceId, searchTerm);
    }

    /**
     * Clear old app activity records (optional cleanup)
     * DELETE /apps/cleanup?deviceId=Laptop-1&daysOld=30
     */
    @DeleteMapping("/cleanup")
    public Map<String, Object> cleanupOldActivity(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "30") int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        List<AppActivity> oldActivities = appActivityRepository.findByDeviceIdAndTimestampRange(
                deviceId,
                LocalDateTime.now().minusYears(100), // Very old start date
                cutoffDate
        );
        
        appActivityRepository.deleteAll(oldActivities);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Deleted " + oldActivities.size() + " old records");
        return response;
    }

    /**
     * Determine if an alert should be triggered
     * This is where you can configure which apps trigger alerts
     */
    @GetMapping("/devices")
    public List<String> getAppDevices() {
        return appActivityRepository.findDistinctDeviceIds();
    }

    private boolean shouldTriggerAlert(String appName) {
        return appName != null && appName.equalsIgnoreCase("Spotify");
    }
}
