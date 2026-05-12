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
import java.io.InputStream;
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
            System.out.println("[agent] Loaded config file: " + configFile);
        } catch (Exception e) {
            System.err.println("[agent] Could not load config file: " + configFile + " - falling back to environment variables");
        }

        Properties appProp = new Properties();
        try (InputStream input = App.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                appProp.load(input);
            }
        } catch (Exception e) {
            System.err.println("[agent] Could not load bundled application.properties: " + e.getMessage());
        }

        String backendUrl = firstNonBlank(
                System.getenv("AGENT_BACKEND_URL"),
                prop.getProperty("AGENT_BACKEND_URL"),
                System.getenv("BACKEND_URL"),
                prop.getProperty("BACKEND_URL"),
                System.getenv("RENDER_BACKEND_URL"),
                prop.getProperty("RENDER_BACKEND_URL"),
                appProp.getProperty("agent.backend-url")
        );

        if (backendUrl.isBlank()) {
            System.err.println("[agent] Backend URL is not configured. Set AGENT_BACKEND_URL or RENDER_BACKEND_URL to your Render backend URL.");
            return;
        }
        if (backendUrl.contains("localhost") || backendUrl.contains("127.0.0.1")) {
            System.err.println("[agent] Warning: backend URL points to localhost. Cloud dashboards need the deployed Render URL.");
        }

        String fileSyncDir = firstNonBlank(
                System.getenv("FILE_SYNC_DIR"),
                prop.getProperty("FILE_SYNC_DIR"),
                appProp.getProperty("file.sync.dir"),
                prop.getProperty("file.sync.dir"),
                ""
        );

        ApiClient apiClient = new ApiClient(backendUrl);

        System.out.println("[agent] Laptop Monitoring Agent starting");
        System.out.println("[agent] Device ID: " + apiClient.getDeviceId());
        System.out.println("[agent] Backend: " + apiClient.getBackendUrl());

        FileSyncService syncService = null;
        if (!fileSyncDir.isBlank()) {
            Path syncPath = Paths.get(fileSyncDir);
            if (Files.exists(syncPath) && Files.isDirectory(syncPath)) {
                syncService = new FileSyncService(apiClient, fileSyncDir);
                syncService.startSync();
                System.out.println("[agent] FileSyncService started. Monitoring " + fileSyncDir);
            } else {
                System.err.println("[agent] FILE_SYNC_DIR configured but does not exist or is not a directory: " + fileSyncDir);
            }
        } else {
            System.out.println("[agent] FileSyncService disabled because FILE_SYNC_DIR is not configured.");
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

        SystemMonitor systemMonitor = new SystemMonitor(apiClient);
        scheduler.scheduleAtFixedRate(safeTask("SystemMonitor", systemMonitor::collectAndSendMetrics), 0, 5, TimeUnit.SECONDS);
        System.out.println("[agent] SystemMonitor initialized and scheduled");

        ProcessMonitor processMonitor = new ProcessMonitor(apiClient);
        scheduler.scheduleAtFixedRate(safeTask("ProcessMonitor", processMonitor::collectAndSendProcesses), 0, 10, TimeUnit.SECONDS);
        System.out.println("[agent] ProcessMonitor initialized and scheduled");

        AppMonitor appMonitor = new AppMonitor(apiClient);
        scheduler.scheduleAtFixedRate(safeTask("AppMonitor", appMonitor::collectAndTrackApps), 0, 5, TimeUnit.SECONDS);
        System.out.println("[agent] AppMonitor initialized and scheduled");

        ActiveWindowMonitor activeWindowMonitor = new ActiveWindowMonitor(apiClient);
        scheduler.scheduleAtFixedRate(safeTask("ActiveWindowMonitor", activeWindowMonitor::collectAndSendActivity), 0, 2, TimeUnit.SECONDS);
        System.out.println("[agent] ActiveWindowMonitor initialized and scheduled");

        BatteryMonitor batteryMonitor = new BatteryMonitor(apiClient);
        scheduler.scheduleAtFixedRate(safeTask("BatteryMonitor", batteryMonitor::collectAndSendBatteryData), 0, 5, TimeUnit.SECONDS);
        System.out.println("[agent] BatteryMonitor initialized and scheduled");

        ClipboardMonitor clipboardMonitor = new ClipboardMonitor(apiClient);
        scheduler.scheduleAtFixedRate(safeTask("ClipboardMonitor", clipboardMonitor::collectAndSend), 0, 1, TimeUnit.SECONDS);
        System.out.println("[agent] ClipboardMonitor initialized and scheduled");

        SecurityEventReporter securityReporter = new SecurityEventReporter(apiClient);
        LoginSessionMonitor loginSessionMonitor = new LoginSessionMonitor(securityReporter);
        loginSessionMonitor.reportStartupSession();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[agent] Shutdown detected; reporting closed session");
            securityReporter.reportSession(System.getProperty("user.name", "unknown"), "CLOSED");
        }, "session-shutdown-reporter"));
        scheduler.scheduleAtFixedRate(safeTask("LoginSessionMonitor", loginSessionMonitor::scanFailedLogins), 15, 30, TimeUnit.SECONDS);
        System.out.println("[agent] LoginSessionMonitor initialized and scheduled");

        UsbMonitor usbMonitor = new UsbMonitor(securityReporter);
        scheduler.scheduleAtFixedRate(safeTask("UsbMonitor", usbMonitor::scanAndReportChanges), 5, 5, TimeUnit.SECONDS);
        System.out.println("[agent] UsbMonitor initialized and scheduled");

        ScreenshotActivityMonitor screenshotActivityMonitor = new ScreenshotActivityMonitor(securityReporter);
        scheduler.scheduleAtFixedRate(safeTask("ScreenshotActivityMonitor", screenshotActivityMonitor::scanAndReport), 3, 2, TimeUnit.SECONDS);
        System.out.println("[agent] ScreenshotActivityMonitor initialized and scheduled");

        ScreenshotService screenshotService = new ScreenshotService(apiClient);
        scheduler.scheduleAtFixedRate(safeTask("ScreenshotService", screenshotService::captureAndSend), 0, 30, TimeUnit.SECONDS);
        System.out.println("[agent] ScreenshotService initialized and scheduled");

        Thread commandThread = new Thread(new CommandPoller(apiClient, syncService, apiClient.getDeviceId(), 5000), "command-poller");
        commandThread.setDaemon(true);
        commandThread.start();
        System.out.println("[agent] CommandPoller thread started");

        while (true) {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                if (syncService != null) {
                    syncService.stopSync();
                }
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static Runnable safeTask(String serviceName, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println("[agent] " + serviceName + " failed: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        };
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
