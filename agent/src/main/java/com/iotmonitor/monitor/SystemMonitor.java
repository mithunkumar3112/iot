package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;
import oshi.software.os.OperatingSystem;

import java.util.List;

public class SystemMonitor {

    private final ApiClient apiClient;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final HardwareAbstractionLayer hw;
    private final OperatingSystem os;

    // Required for CPU calculation
    private long[] previousCpuTicks;
    private boolean firstCall = true;

    public SystemMonitor(ApiClient apiClient) {

        this.apiClient = apiClient;

        SystemInfo systemInfo = new SystemInfo();
        this.hw = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();

        this.processor = hw.getProcessor();
        this.memory = hw.getMemory();

        this.previousCpuTicks = processor.getSystemCpuLoadTicks();
    }

    public void collectAndSendMetrics() {

        // CPU
        double cpuLoad = 0.0;
        if (!firstCall) {
            cpuLoad = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100;
        }
        previousCpuTicks = processor.getSystemCpuLoadTicks();
        firstCall = false;

        // RAM
        long totalMem = memory.getTotal();
        long usedMem = totalMem - memory.getAvailable();
        double ramUsage = (usedMem * 100.0) / totalMem;

        // Uptime
        long uptime = os.getSystemUptime();

        // Battery with charging status
        List<PowerSource> powerSources = hw.getPowerSources();
        double battery = -1.0;
        boolean isCharging = false;

        if (powerSources != null && !powerSources.isEmpty()) {
            PowerSource ps = powerSources.get(0);
            battery = ps.getRemainingCapacityPercent();
            // Ensure battery is in valid range (0-100)
            if (battery < 0) battery = -1.0;
            if (battery > 100) battery = 100.0;
            isCharging = ps.isCharging();
        }

        // JSON with battery charging status
        String json = String.format(
                "{ \"deviceId\": \"%s\", \"cpu\": %.2f, \"ram\": %.2f, \"uptime\": %d, \"battery\": %.2f, \"charging\": %s }",
                apiClient.getDeviceId().replace("\"", "\\\""), cpuLoad, ramUsage, uptime, battery, isCharging
        );

        apiClient.sendMetrics(json);
    }
}
