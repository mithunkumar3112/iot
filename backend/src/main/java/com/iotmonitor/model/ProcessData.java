package com.iotmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processes")
public class ProcessData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private String processName;
    private double cpuUsage;
    private double memoryUsage; // in MB
    private int instanceCount; // Number of instances for grouped processes
    private LocalDateTime timestamp;

    // Constructors
    public ProcessData() {}

    public ProcessData(String deviceId, String processName, double cpuUsage, double memoryUsage) {
        this.deviceId = deviceId;
        this.processName = processName;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.instanceCount = 1;
        this.timestamp = LocalDateTime.now();
    }

    public ProcessData(String deviceId, String processName, double cpuUsage, double memoryUsage, int instanceCount) {
        this.deviceId = deviceId;
        this.processName = processName;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.instanceCount = instanceCount;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }

    public double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }

    public int getInstanceCount() { return instanceCount; }
    public void setInstanceCount(int instanceCount) { this.instanceCount = instanceCount; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}