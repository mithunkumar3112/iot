package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * NEW code: tracks the currently focused app and active screen time.
 */
public class ActiveWindowMonitor {

    private final ApiClient apiClient;
    private final ZoneId zoneId = ZoneId.systemDefault();
    private final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private String currentApp;

    private static final Set<String> IGNORED_APPS = Set.of(
            "system", "system idle process", "dwm", "lockapp", "searchhost",
            "shellexperiencehost", "startmenuexperiencehost", "textinputhost",
            "applicationframehost", "runtimebroker", "backgroundtaskhost"
    );

    public ActiveWindowMonitor(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void collectAndSendActivity() {
        try {
            Instant now = Instant.now();
            String activeApp = detectActiveApp();

            if (activeApp == null) {
                currentApp = null;
                return;
            }

            if (!activeApp.equals(currentApp)) {
                currentApp = activeApp;
                sendActiveWindowEvent(activeApp, now);
            }
        } catch (Exception e) {
            System.err.println("Active window tracking failed: " + e.getMessage());
        }
    }


    private void sendActiveWindowEvent(String appName, Instant timestamp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", apiClient.getDeviceId());
        payload.put("activeWindow", appName);
        payload.put("timestamp", timestamp.toString());
        apiClient.sendActiveWindow(payload);
    }

    private String detectActiveApp() {
        String appName;
        if (osName.contains("win")) {
            appName = detectWindowsActiveApp();
        } else if (osName.contains("mac")) {
            appName = detectMacActiveApp();
        } else {
            appName = detectLinuxActiveApp();
        }
        appName = normalizeAppName(appName);
        return shouldIgnore(appName) ? null : appName;
    }

    private String detectWindowsActiveApp() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) return null;

        char[] titleBuffer = new char[512];
        User32.INSTANCE.GetWindowText(hwnd, titleBuffer, titleBuffer.length);
        String title = Native.toString(titleBuffer).trim();

        IntByReference pidRef = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
        String processName = processNameFromPid(pidRef.getValue());
        if (processName == null || processName.isBlank()) {
            processName = title;
        }
        return processName;
    }

    private String processNameFromPid(int pid) {
        if (pid <= 0) return null;
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) return null;
        Optional<String> command = handle.get().info().command();
        if (command.isEmpty()) return null;
        try {
            Path fileName = Path.of(command.get()).getFileName();
            return fileName == null ? command.get() : fileName.toString();
        } catch (Exception e) {
            return command.get();
        }
    }

    private String detectMacActiveApp() {
        return runCommand(List.of(
                "osascript",
                "-e",
                "tell application \"System Events\" to get name of first application process whose frontmost is true"
        ));
    }

    private String detectLinuxActiveApp() {
        String pid = runCommand(List.of("xdotool", "getactivewindow", "getwindowpid"));
        if (pid == null || pid.isBlank()) return null;
        return runCommand(List.of("ps", "-p", pid.trim(), "-o", "comm="));
    }

    private String runCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(800, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            return new String(process.getInputStream().readAllBytes()).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeAppName(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".exe")) lower = lower.substring(0, lower.length() - 4);

        if (lower.contains("chrome")) return "Chrome";
        if (lower.contains("msedge")) return "Microsoft Edge";
        if (lower.contains("firefox")) return "Firefox";
        if (lower.contains("brave")) return "Brave";
        if (lower.equals("code") || lower.contains("vscode")) return "VS Code";
        if (lower.contains("idea64") || lower.contains("idea")) return "IntelliJ IDEA";
        if (lower.contains("spotify")) return "Spotify";
        if (lower.contains("teams")) return "Teams";
        if (lower.contains("slack")) return "Slack";
        if (lower.contains("discord")) return "Discord";
        if (lower.contains("explorer")) return "File Explorer";
        if (lower.contains("notepad")) return "Notepad";
        if (lower.contains("powershell")) return "PowerShell";
        if (lower.equals("cmd")) return "Command Prompt";

        String cleaned = lower.replace('-', ' ').replace('_', ' ').trim();
        if (cleaned.isEmpty()) return null;
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private boolean shouldIgnore(String appName) {
        if (appName == null || appName.isBlank()) return true;
        String lower = appName.toLowerCase(Locale.ROOT).replace(" ", "");
        return IGNORED_APPS.stream().anyMatch(lower::contains);
    }
}
