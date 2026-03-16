package com.iotmonitor.agent;

import com.iotmonitor.monitor.ScreenshotService;
import com.iotmonitor.monitor.SystemMonitor;
import com.iotmonitor.network.ApiClient;

public class App {

    public static void main(String[] args) {

        ApiClient apiClient = new ApiClient("http://localhost:8080");
        SystemMonitor monitor = new SystemMonitor(apiClient);

        System.out.println("🚀 Agent started");

        while (true) {
            try {
                // 🔍 Check monitoring ON / OFF
                boolean monitoringEnabled = apiClient.isMonitoringEnabled();

                if (monitoringEnabled) {
                    // 📊 Collect & send CPU / RAM
                    monitor.collectAndSendMetrics();

                    // 🖼 Capture & send screenshot
                    byte[] screenshot = ScreenshotService.capture();
                    if (screenshot != null) {
                        apiClient.uploadScreenshot(screenshot);
                    }

                    System.out.println("✅ Monitoring running");
                } else {
                    System.out.println("⏸ Monitoring OFF (paused by server)");
                }

                // 🔍 Check server command
                String command = apiClient.getLatestCommand();

                if ("SHUTDOWN".equalsIgnoreCase(command)) {
                    System.out.println("⏹ Shutdown command received");

                    // ⚠ Windows shutdown
                    Runtime.getRuntime().exec("shutdown -s -t 0");
                    break;
                }

                if ("RESTART_AGENT".equalsIgnoreCase(command)) {
                    System.out.println("🔁 Restart agent command received");
                    System.exit(0);
                }

                Thread.sleep(5000);

            } catch (Exception e) {
                System.out.println("⚠ Agent error: " + e.getMessage());
            }
        }
    }
}
