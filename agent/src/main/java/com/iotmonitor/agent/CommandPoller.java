package com.iotmonitor.agent;

import com.iotmonitor.monitor.ScreenMonitor;
import com.iotmonitor.network.ApiClient;

public class CommandPoller implements Runnable {

    private static final long DEFAULT_POLL_INTERVAL_MS = 5000;

    private final ApiClient apiClient;
    private final FileSyncService fileSyncService;
    private final ScreenMonitor screenMonitor;
    private final String deviceId;
    private final long intervalMs;

    public CommandPoller(ApiClient apiClient,
                         FileSyncService fileSyncService,
                         ScreenMonitor screenMonitor,
                         String deviceId,
                         long intervalMs) {
        this.apiClient = apiClient;
        this.fileSyncService = fileSyncService;
        this.screenMonitor = screenMonitor;
        this.deviceId = deviceId;
        this.intervalMs = intervalMs > 0 ? intervalMs : DEFAULT_POLL_INTERVAL_MS;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (deviceId == null || deviceId.isBlank()) {
                    System.err.println("⚠️ CommandPoller skipped because deviceId is missing");
                } else {
                    String command = apiClient.fetchPendingCommand(deviceId);
                    if (command != null) {
                        command = stripQuotes(command);
                        if (!command.isBlank() && !command.equalsIgnoreCase("NONE")) {
                            executeCommand(command.trim().toUpperCase());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Command poll error: " + e.getMessage());
            }

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void executeCommand(String command) {
        System.out.println("📨 Command received: " + command);
        if (command.startsWith("KILL_PROCESS ")) {
            executeKillProcess(command);
        } else {
            switch (command) {
                case "TAKE_SCREENSHOT" -> executeScreenshot(command);
                case "SYNC_FILES" -> executeSyncFiles(command);
                case "DELETE_FILE" -> executeDeleteFile(command);
                case "SHUTDOWN" -> executeShutdown(command);
                case "SLEEP" -> executeSleep(command);
                case "RESTART" -> executeRestart(command);
                case "RESTART_AGENT" -> executeRestartAgent(command);
                default -> {
                    System.err.println("⚠️ Unknown command: " + command);
                    apiClient.postCommandResult(deviceId, command, "FAILED");
                }
            }
        }
    }

    private void executeScreenshot(String command) {
        if (screenMonitor == null) {
            System.err.println("⚠️ TAKE_SCREENSHOT unavailable: screen monitor not initialized");
            apiClient.postCommandResult(deviceId, command, "FAILED");
            return;
        }
        screenMonitor.captureAndSend();
        apiClient.postCommandResult(deviceId, command, "SUCCESS");
    }

    private void executeSyncFiles(String command) {
        try {
            fileSyncService.syncNow();
            apiClient.postCommandResult(deviceId, command, "SUCCESS");
        } catch (Exception e) {
            System.err.println("⚠️ SYNC_FILES failed: " + e.getMessage());
            apiClient.postCommandResult(deviceId, command, "FAILED");
        }
    }

    private void executeDeleteFile(String command) {
        System.err.println("⚠️ DELETE_FILE command received but no path information was provided");
        apiClient.postCommandResult(deviceId, command, "FAILED");
    }

    private void executeShutdown(String command) {
        apiClient.postCommandResult(deviceId, command, "SUCCESS");
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("shutdown /s /t 0");
            } else {
                Runtime.getRuntime().exec("shutdown -h now");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Shutdown command failed: " + e.getMessage());
        }
        System.exit(0);
    }

    private void executeSleep(String command) {
        apiClient.postCommandResult(deviceId, command, "SUCCESS");
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("rundll32.exe powrprof.dll,SetSuspendState 0,1,0");
            } else {
                Runtime.getRuntime().exec("systemctl suspend");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Sleep command failed: " + e.getMessage());
        }
    }

    private void executeRestart(String command) {
        apiClient.postCommandResult(deviceId, command, "SUCCESS");
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("shutdown -r -t 0");
            } else {
                Runtime.getRuntime().exec("reboot");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Restart command failed: " + e.getMessage());
        }
    }

    private void executeRestartAgent(String command) {
        apiClient.postCommandResult(deviceId, command, "SUCCESS");
        System.exit(2);
    }

    private void executeKillProcess(String command) {
        String processName = command.substring("KILL_PROCESS ".length()).trim();
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec("taskkill /F /IM " + processName + ".exe");
            } else {
                process = Runtime.getRuntime().exec("pkill -f " + processName);
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                apiClient.postCommandResult(deviceId, command, "SUCCESS");
            } else {
                apiClient.postCommandResult(deviceId, command, "FAILED");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Kill process failed: " + e.getMessage());
            apiClient.postCommandResult(deviceId, command, "FAILED");
        }
    }

    private String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
