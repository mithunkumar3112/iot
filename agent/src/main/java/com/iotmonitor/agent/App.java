package com.iotmonitor.agent;

import com.iotmonitor.monitor.ActiveWindowMonitor;
import com.iotmonitor.monitor.AppMonitor;
import com.iotmonitor.monitor.BatteryMonitor;
import com.iotmonitor.monitor.ClipboardMonitor;
import com.iotmonitor.monitor.LoginSessionMonitor;
import com.iotmonitor.monitor.ProcessMonitor;
import com.iotmonitor.monitor.ScreenshotActivityMonitor;
import com.iotmonitor.monitor.SecurityEventReporter;
import com.iotmonitor.monitor.SystemMonitor;
import com.iotmonitor.monitor.UsbMonitor;
import com.iotmonitor.network.ApiClient;

import java.awt.AWTException;
import java.io.FileInputStream;
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
            System.err.println("❌ Could not load config file: " + configFile);
            return;
        }

        String renderBackendUrl = prop.getProperty("RENDER_BACKEND_URL", "http://localhost:5000");

        // Initialize API client
        ApiClient apiClient = new ApiClient(renderBackendUrl);

        System.out.println("🚀 Laptop Monitoring Agent starting...");
        System.out.println("🖥 Device ID: " + apiClient.getDeviceId());
        System.out.println("🔗 Backend: " + renderBackendUrl);

        // 📂 Start File Sync Service
        String syncDir = "C:\\shared-files";
        FileSyncService syncService = new FileSyncService(apiClient, syncDir);
        syncService.startSync();

        System.out.println("🟢 Sync service is active. Monitoring " + syncDir);

        // 📈 Start Performance + Screen + Process + App monitoring
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

        SystemMonitor systemMonitor = new SystemMonitor(apiClient);
        scheduler.scheduleAtFixedRate(systemMonitor::collectAndSendMetrics, 0, 5, TimeUnit.SECONDS);

        ProcessMonitor processMonitor = new ProcessMonitor(apiClient);
        scheduler.scheduleAtFixedRate(processMonitor::collectAndSendProcesses, 0, 10, TimeUnit.SECONDS);

        // 📱 Start App Activity Tracking
        AppMonitor appMonitor = new AppMonitor(apiClient);
        scheduler.scheduleAtFixedRate(appMonitor::collectAndTrackApps, 0, 5, TimeUnit.SECONDS);

        // NEW code: foreground-window tracking for activity timeline
        ActiveWindowMonitor activeWindowMonitor = new ActiveWindowMonitor(apiClient);
        scheduler.scheduleAtFixedRate(activeWindowMonitor::collectAndSendActivity, 0, 2, TimeUnit.SECONDS);

        // 🔋 Start Battery Monitoring
        BatteryMonitor batteryMonitor = new BatteryMonitor(apiClient);
        scheduler.scheduleAtFixedRate(batteryMonitor::collectAndSendBatteryData, 0, 5, TimeUnit.SECONDS);

        ClipboardMonitor clipboardMonitor = new ClipboardMonitor(apiClient);
        scheduler.scheduleAtFixedRate(clipboardMonitor::collectAndSend, 0, 1, TimeUnit.SECONDS);

        SecurityEventReporter securityReporter = new SecurityEventReporter(apiClient);
        LoginSessionMonitor loginSessionMonitor = new LoginSessionMonitor(securityReporter);
        loginSessionMonitor.reportStartupSession();
        scheduler.scheduleAtFixedRate(loginSessionMonitor::scanFailedLogins, 15, 30, TimeUnit.SECONDS);

        UsbMonitor usbMonitor = new UsbMonitor(securityReporter);
        scheduler.scheduleAtFixedRate(usbMonitor::scanAndReportChanges, 5, 5, TimeUnit.SECONDS);

        ScreenshotActivityMonitor screenshotActivityMonitor = new ScreenshotActivityMonitor(securityReporter);
        scheduler.scheduleAtFixedRate(screenshotActivityMonitor::scanAndReport, 3, 2, TimeUnit.SECONDS);

        Thread commandThread = new Thread(new CommandPoller(apiClient, syncService, apiClient.getDeviceId(), 5000), "command-poller");
        commandThread.setDaemon(true);
        commandThread.start();

        // Keep the main thread alive
        while (true) {
            try {
                Thread.sleep(60000); // 1 minute heartbeat
            } catch (InterruptedException e) {
                syncService.stopSync();
                scheduler.shutdownNow();
                break;
            }
        }
    }
}
