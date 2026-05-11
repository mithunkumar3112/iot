package com.iotmonitor.controller;

import com.iotmonitor.model.ProcessData;
import com.iotmonitor.repository.ProcessDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.iotmonitor.service.SupabaseStorageService;

@RestController
@RequestMapping("/processes")
public class ProcessController {

    @Autowired
    private ProcessDataRepository processDataRepository;

    @Autowired(required = false)
    private SupabaseStorageService supabaseStorageService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public void receiveProcesses(@RequestBody Map<String, Object> payload) {
        String deviceId = (String) payload.get("deviceId");
        List<Map<String, Object>> processes = (List<Map<String, Object>>) payload.get("processes");

        System.out.println("\n=== 📊 PROCESSES RECEIVED ===");
        System.out.println("📊 Timestamp: " + java.time.LocalDateTime.now());
        System.out.println("📊 Device ID: " + deviceId);
        System.out.println("📊 Process count: " + (processes != null ? processes.size() : "null"));

        if (processes != null && processes.size() > 0) {
            System.out.println("📊 Sample process: " + processes.get(0));
        }

        if (deviceId != null && !deviceId.isBlank() && processes != null) {
            deviceId = deviceId.trim();
            // Save locally so devices can be discovered and history can be queried
            String finalDeviceId = deviceId;
            List<ProcessData> processEntities = processes.stream()
                    .map(process -> {
                        String processName = String.valueOf(process.getOrDefault("name", "unknown"));
                        double cpuUsage = parseDouble(process.get("cpu"));
                        double memoryUsage = parseDouble(process.get("memory"));
                        int instanceCount = parseInt(process.get("instanceCount"));
                        return new ProcessData(finalDeviceId, processName, cpuUsage, memoryUsage, instanceCount);
                    })
                    .toList();
            processDataRepository.saveAll(processEntities);

            System.out.println("✅ PROCESSES SAVED: " + processEntities.size() + " entities for device " + deviceId);
            
            // Log to Supabase Cloud if configured
            if (supabaseStorageService != null) {
                supabaseStorageService.logProcessesToSupabase(deviceId, processes);
            }
            
            // Emit WebSocket event for real-time update
            messagingTemplate.convertAndSend("/topic/processes", payload);
            System.out.println("✅ PROCESSES WEBSOCKET SENT to /topic/processes");
        } else {
            System.out.println("❌ PROCESSES VALIDATION FAILED: deviceId=" + deviceId + ", processes=" + (processes != null));
        }
        System.out.println("=== END PROCESSES ===\n");
    }

    private double parseDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseInt(Object value) {
        if (value == null) {
            return 1;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @GetMapping("/{deviceId}")
    public List<ProcessData> getProcesses(@PathVariable String deviceId) {
        return processDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId);
    }

    @GetMapping("/latest")
    public List<ProcessData> getLatestProcesses(@RequestParam(required = false) String deviceId,
                                                @RequestParam(required = false) String device,
                                                @RequestParam(defaultValue = "20") int limit) {
        String resolvedDeviceId = resolveDeviceId(deviceId != null ? deviceId : device);
        if (resolvedDeviceId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 300));
        return processDataRepository.findByDeviceIdOrderByTimestampDesc(resolvedDeviceId, PageRequest.of(0, safeLimit));
    }

    @GetMapping("/{deviceId}/high-cpu")
    public List<ProcessData> getHighCpuProcesses(@PathVariable String deviceId) {
        return processDataRepository.findHighCpuProcesses(deviceId, 80.0);
    }

    /**
     * Get list of all unique devices
     * GET /processes/devices
     */
    @GetMapping("/devices")
    public List<String> getAllDevices() {
        Set<String> deviceIds = new HashSet<>(processDataRepository.findAllUniqueDeviceIds());
        return deviceIds.stream().sorted().toList();
    }

    private String resolveDeviceId(String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            return deviceId.trim();
        }
        return processDataRepository.findAllUniqueDeviceIds().stream().findFirst().orElse(null);
    }
}
