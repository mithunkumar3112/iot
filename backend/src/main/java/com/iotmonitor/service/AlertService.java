package com.iotmonitor.service;

import com.iotmonitor.model.Alert;
import com.iotmonitor.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void createAlert(String deviceId, String type, String message, String severity) {
        Alert alert = new Alert(deviceId, type, message, severity);
        alertRepository.save(alert);

        // Emit WebSocket event
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }

    public void checkAndCreateAlerts(String deviceId, double cpu, double ram, double processCpu) {
        if (cpu > 80) {
            createAlert(deviceId, "CPU", "High CPU usage: " + cpu + "%", "HIGH");
        }
        if (ram > 80) {
            createAlert(deviceId, "RAM", "High RAM usage: " + ram + "%", "HIGH");
        }
        if (processCpu > 80) {
            createAlert(deviceId, "PROCESS", "High process CPU: " + processCpu + "%", "MEDIUM");
        }
    }
}