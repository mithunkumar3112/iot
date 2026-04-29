package com.iotmonitor.controller;

import com.iotmonitor.model.ProcessData;
import com.iotmonitor.repository.ProcessDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.iotmonitor.service.SupabaseStorageService;
import org.springframework.beans.factory.annotation.Autowired;

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

        if (deviceId != null && processes != null) {
            // Save locally so devices can be discovered and history can be queried
            List<ProcessData> processEntities = processes.stream()
                    .map(process -> {
                        String processName = String.valueOf(process.getOrDefault("name", "unknown"));
                        double cpuUsage = parseDouble(process.get("cpu"));
                        double memoryUsage = parseDouble(process.get("memory"));
                        return new ProcessData(deviceId, processName, cpuUsage, memoryUsage);
                    })
                    .toList();
            processDataRepository.saveAll(processEntities);

            // Log to Supabase Cloud if configured
            if (supabaseStorageService != null) {
                supabaseStorageService.logProcessesToSupabase(deviceId, processes);
            }
            
            // Emit WebSocket event for real-time update
            messagingTemplate.convertAndSend("/topic/processes", payload);
        }
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

    @GetMapping("/{deviceId}")
    public List<ProcessData> getProcesses(@PathVariable String deviceId) {
        return processDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId);
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
}