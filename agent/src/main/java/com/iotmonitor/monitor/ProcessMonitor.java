package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessMonitor {

    private final ApiClient apiClient;
    private final OperatingSystem os;
    private final Map<Integer, OSProcess> lastProcessMap = new HashMap<>();

    public ProcessMonitor(ApiClient apiClient) {
        this.apiClient = apiClient;
        SystemInfo systemInfo = new SystemInfo();
        this.os = systemInfo.getOperatingSystem();
    }

    public void collectAndSendProcesses() {
        List<OSProcess> processes = os.getProcesses();
        List<Map<String, Object>> processList = new ArrayList<>();
        String timestamp = Instant.now().toString();

        for (OSProcess process : processes) {
            int pid = process.getProcessID();
            if (pid == 0) continue; // Skip idle process

            double cpuPercent = 0.0;
            if (lastProcessMap.containsKey(pid)) {
                cpuPercent = process.getProcessCpuLoadBetweenTicks(lastProcessMap.get(pid)) * 100;
            }
            lastProcessMap.put(pid, process);

            Map<String, Object> data = Map.of(
                "name", process.getName(),
                "cpu", Math.round(cpuPercent * 100.0) / 100.0,
                "memory", Math.round((process.getResidentSetSize() / (1024.0 * 1024.0)) * 100.0) / 100.0,
                "timestamp", timestamp
            );
            processList.add(data);
        }

        // Cleanup dead processes from map
        lastProcessMap.keySet().removeIf(pid -> processes.stream().noneMatch(p -> p.getProcessID() == pid));

        // Send to backend in new format
        apiClient.sendProcesses(Map.of(
            "deviceId", apiClient.getDeviceId(),
            "processes", processList
        ));
    }
}