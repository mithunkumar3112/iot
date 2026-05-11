package com.iotmonitor.agent;

import com.iotmonitor.monitor.ActiveWindowMonitor;
import com.iotmonitor.monitor.AppMonitor;
import com.iotmonitor.monitor.BatteryMonitor;
import com.iotmonitor.monitor.ClipboardMonitor;
import com.iotmonitor.monitor.LoginSessionMonitor;
import com.iotmonitor.monitor.ProcessMonitor;
import com.iotmonitor.monitor.ScreenshotActivityMonitor;
import com.iotmonitor.monitor.ScreenshotService;
import com.iotmonitor.monitor.SecurityEventReporter;
import com.iotmonitor.monitor.SystemMonitor;
import com.iotmonitor.monitor.UsbMonitor;
import com.iotmonitor.network.ApiClient;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class App {

    public static void main(String[] args) {
        Properties prop = new Properties();
        String configFile = "agent.properties";

        try (FileInputStream fis = new FileInputStream(configFile)) {
            prop.load(fis);
        } catch (Exception e) {
            System.err.println("⚠️ Could not load config file: " + configFile + " - falling back to defaults");
        }

        String renderBackendUrl = firstNonBlank(
                System.getenv("RENDER_BACKEND_URL"),
                prop.getProperty("RENDER_BACKEND_URL"),
                "http://localhost:5000"
        );

        String fileSyncDir = firstNonBlank(
                System.getenv("FILE_SYNC_DIR"),
                prop.getProperty("FILE_SYNC_DIR"),
                ""
        );

        // Initialize API client
        ApiClient apiClient = new ApiClient(renderBackendUrl);

        System.out.println("🚀 Laptop Monitoring Agent starting...");
        System.out.println("🖥 Device ID: " + apiClient.getDeviceId());
        System.out.println("🔗 Backend: " + renderBackendUrl);

        // 📂 Start File Sync Service when configured
        FileSyncService syncService = null;
        if (!fileSyncDir.isBlank()) {
            Path syncPath = Paths.get(fileSyncDir);
            if (Files.exists(syncPath) && Files.isDirectory(syncPath)) {
                syncService = new FileSyncService(apiClient, fileSyncDir);
                syncService.startSync();
                System.out.println("🟢 Sync service is active. Monitoring " + fileSyncDir);
            } else {
                System.err.println("⚠️ FILE_SYNC_DIR configured but does not exist or is not a directory: " + fileSyncDir);
            }
        } else {
            System.out.println("ℹ️ File sync is disabled because FILE_SYNC_DIR is not configured.");
        }

        // 📈 Start Performance + Screen + Process + App monitoring
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

        SystemMonitor systemMonitor = new SystemMonitor(apiClient);
        scheduler.scheduleAtFixedRate(systemMonitor::collectAndSendMetrics, 0, 5, TimeUnit.SECONDS);
        System.out.println("✅ SystemMonitor initialized and scheduled");

        ProcessMonitor processMonitor = new ProcessMonitor(apiClient);
        scheduler.scheduleAtFixedRate(processMonitor::collectAndSendProcesses, 0, 10, TimeUnit.SECONDS);
        System.out.println("✅ ProcessMonitor initialized and scheduled");

        // 📱 Start App Activity Tracking
        AppMonitor appMonitor = new AppMonitor(apiClient);
        scheduler.scheduleAtFixedRate(appMonitor::collectAndTrackApps, 0, 5, TimeUnit.SECONDS);
        System.out.println("✅ AppMonitor initialized and scheduled");

        // NEW code: foreground-window tracking for activity timeline
        ActiveWindowMonitor activeWindowMonitor = new ActiveWindowMonitor(apiClient);
        scheduler.scheduleAtFixedRate(activeWindowMonitor::collectAndSendActivity, 0, 2, TimeUnit.SECONDS);
        System.out.println("✅ ActiveWindowMonitor initialized and scheduled");

        // 🔋 Start Battery Monitoring
        BatteryMonitor batteryMonitor = new BatteryMonitor(apiClient);
        scheduler.scheduleAtFixedRate(batteryMonitor::collectAndSendBatteryData, 0, 5, TimeUnit.SECONDS);
        System.out.println("✅ BatteryMonitor initialized and scheduled");

        ClipboardMonitor clipboardMonitor = new ClipboardMonitor(apiClient);
        scheduler.scheduleAtFixedRate(clipboardMonitor::collectAndSend, 0, 1, TimeUnit.SECONDS);
        System.out.println("✅ ClipboardMonitor initialized and scheduled");

        SecurityEventReporter securityReporter = new SecurityEventReporter(apiClient);
        LoginSessionMonitor loginSessionMonitor = new LoginSessionMonitor(securityReporter);
        loginSessionMonitor.reportStartupSession();
        scheduler.scheduleAtFixedRate(loginSessionMonitor::scanFailedLogins, 15, 30, TimeUnit.SECONDS);
        System.out.println("✅ LoginSessionMonitor initialized and scheduled");

        UsbMonitor usbMonitor = new UsbMonitor(securityReporter);
        scheduler.scheduleAtFixedRate(usbMonitor::scanAndReportChanges, 5, 5, TimeUnit.SECONDS);
        System.out.println("✅ UsbMonitor initialized and scheduled");

        ScreenshotActivityMonitor screenshotActivityMonitor = new ScreenshotActivityMonitor(securityReporter);
        scheduler.scheduleAtFixedRate(screenshotActivityMonitor::scanAndReport, 3, 2, TimeUnit.SECONDS);
        System.out.println("✅ ScreenshotActivityMonitor initialized and scheduled");

        // Add ScreenshotService for periodic screenshot capture
        ScreenshotService screenshotService = new ScreenshotService(apiClient);
        scheduler.scheduleAtFixedRate(screenshotService::captureAndSend, 0, 30, TimeUnit.SECONDS);
        System.out.println("✅ ScreenshotService initialized and scheduled");

        Thread commandThread = new Thread(new CommandPoller(apiClient, syncService, apiClient.getDeviceId(), 5000), "command-poller");
        commandThread.setDaemon(true);
        commandThread.start();
        System.out.println("✅ CommandPoller thread started");

        // Keep the main thread alive
        while (true) {
            try {
                Thread.sleep(60000); // 1 minute heartbeat
            } catch (InterruptedException e) {
                if (syncService != null) {
                    syncService.stopSync();
                }
                scheduler.shutdownNow();
                break;
            }
        }

    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
