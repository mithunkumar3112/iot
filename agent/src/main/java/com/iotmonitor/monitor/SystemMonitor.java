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

        // Battery
        List<PowerSource> powerSources = hw.getPowerSources();
        double battery = -1;

        if (powerSources != null && !powerSources.isEmpty()) {
            battery = powerSources.get(0).getRemainingCapacityPercent();
        }

        // JSON
        String json = String.format(
                "{ \"cpu\": %.2f, \"ram\": %.2f, \"uptime\": %d, \"battery\": %.2f }",
                cpuLoad, ramUsage, uptime, battery
        );

        apiClient.sendMetrics(json);
    }
}