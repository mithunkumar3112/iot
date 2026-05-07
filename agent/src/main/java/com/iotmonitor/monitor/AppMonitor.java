package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.*;

/**
 * AppMonitor tracks running applications and detects when apps are opened/closed
 * Uses OSHI to get process list and maintains state to detect changes
 */
public class AppMonitor {

    private final ApiClient apiClient;
    private final OperatingSystem os;
    private final Map<Integer, AppProcessInfo> lastProcessMap = new HashMap<>();
    private final Set<String> trackedApps = new HashSet<>();
    private final boolean isWindows;

    public AppMonitor(ApiClient apiClient) {
        this.apiClient = apiClient;
        SystemInfo systemInfo = new SystemInfo();
        this.os = systemInfo.getOperatingSystem();
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        initializeTrackedApps();
    }

    /**
     * Initialize list of apps to track (popular apps)
     */
    private void initializeTrackedApps() {
        // Windows apps
        trackedApps.addAll(Arrays.asList(
            "chrome.exe", "firefox.exe", "msedge.exe", "opera.exe",
            "spotify.exe", "vlc.exe", "teams.exe", "slack.exe",
            "discord.exe", "code.exe", "vscode.exe", "idea64.exe", "sublime_text.exe",
            "notepad.exe", "calc.exe", "explorer.exe", "powershell.exe",
            "cmd.exe", "putty.exe", "filezilla.exe", "7zfm.exe",
            "winrar.exe", "gimp.exe", "photoshop.exe", "premiere.exe",
            "audacity.exe", "blender.exe", "mongodb.exe", "mysql.exe"
        ));

        // Linux/macOS apps
        trackedApps.addAll(Arrays.asList(
            "chrome", "firefox", "safari", "opera",
            "spotify", "vlc", "teams", "slack",
            "discord", "code", "vscode", "idea", "sublime",
            "terminal", "bash", "zsh", "python",
            "java", "node", "npm", "git"
        ));
    }

    /**
     * Collect running apps and detect new/closed apps
     */
    public void collectAndTrackApps() {
        try {
            List<OSProcess> processes = os.getProcesses();
            Map<Integer, AppProcessInfo> currentProcessMap = new HashMap<>();
            Set<String> currentApps = new HashSet<>();

            // Build current process map and app list
            for (OSProcess process : processes) {
                int pid = process.getProcessID();
                if (pid == 0) continue; // Skip idle process

                String processName = process.getName();
                String displayName = extractDisplayName(processName);

                // Track specific apps
                if (isAppOfInterest(processName)) {
                    currentApps.add(displayName);
                    currentProcessMap.put(pid, new AppProcessInfo(pid, processName, displayName));
                }
            }

            // Detect opened apps (new apps)
            Set<String> previousApps = getAppsFromLastState();
            Set<String> openedApps = new HashSet<>(currentApps);
            openedApps.removeAll(previousApps);

            // Detect closed apps (removed apps)
            Set<String> closedApps = new HashSet<>(previousApps);
            closedApps.removeAll(currentApps);

            // Send app events for opened apps
            for (String appName : openedApps) {
                sendAppActivityEvent(appName, "OPENED");
            }

            // Send app events for closed apps
            for (String appName : closedApps) {
                sendAppActivityEvent(appName, "CLOSED");
            }

            // Update last state
            lastProcessMap.clear();
            lastProcessMap.putAll(currentProcessMap);

        } catch (Exception e) {
            System.err.println("⚠️ Error in app monitoring: " + e.getMessage());
        }
    }

    /**
     * Send app activity event to backend
     */
    private void sendAppActivityEvent(String appName, String status) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("deviceId", apiClient.getDeviceId());
            payload.put("appName", appName);
            payload.put("status", status);
            payload.put("timestamp", Instant.now().toString());

            System.out.println("\n=== 📱 SENDING APP EVENT ===");
            System.out.println("📱 Timestamp: " + java.time.LocalDateTime.now());
            System.out.println("📱 Device ID: " + apiClient.getDeviceId());
            System.out.println("📱 App Name: " + appName);
            System.out.println("📱 Status: " + status);
            System.out.println("📱 Event Time: " + payload.get("timestamp"));
            
            apiClient.sendAppActivity(payload);
            System.out.println("✅ App event sent successfully to backend");
        } catch (Exception e) {
            System.err.println("❌ ERROR sending app activity for " + appName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extract display name from process name
     * E.g., "chrome.exe" -> "Chrome", "spotify.exe" -> "Spotify"
     */
    private String extractDisplayName(String processName) {
        String lower = processName.toLowerCase();

        if (lower.equals("chrome.exe") || lower.equals("chrome")) return "Chrome";
        if (lower.equals("spotify.exe") || lower.equals("spotify")) return "Spotify";
        if (lower.equals("code.exe") || lower.equals("code") || lower.equals("vscode.exe") || lower.equals("vscode")) {
            return "VS Code";
        }

        // Remove extension
        if (lower.endsWith(".exe")) {
            processName = processName.substring(0, processName.length() - 4);
        }

        // Capitalize first letter
        if (processName.length() > 0) {
            processName = processName.substring(0, 1).toUpperCase() + processName.substring(1);
        }

        return processName;
    }

    /**
     * Check if process is of interest (known app)
     */
    private boolean isAppOfInterest(String processName) {
        String lower = processName.toLowerCase();

        // Check tracked apps
        for (String tracked : trackedApps) {
            if (lower.contains(tracked.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get set of apps from last state
     */
    private Set<String> getAppsFromLastState() {
        Set<String> apps = new HashSet<>();
        for (AppProcessInfo info : lastProcessMap.values()) {
            apps.add(info.displayName);
        }
        return apps;
    }

    /**
     * Inner class to store app process info
     */
    private static class AppProcessInfo {
        int pid;
        String processName;
        String displayName;

        AppProcessInfo(int pid, String processName, String displayName) {
            this.pid = pid;
            this.processName = processName;
            this.displayName = displayName;
        }
    }

    /**
     * Add custom app to tracking list
     */
    public void addTrackedApp(String appName) {
        trackedApps.add(appName);
    }

    /**
     * Remove app from tracking list
     */
    public void removeTrackedApp(String appName) {
        trackedApps.remove(appName);
    }

    /**
     * Get list of tracked apps
     */
    public Set<String> getTrackedApps() {
        return new HashSet<>(trackedApps);
    }
}
