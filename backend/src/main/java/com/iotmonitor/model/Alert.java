package com.iotmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    @Column(name = "device_name")
    private String deviceName;
    private String type; // CPU, RAM, PROCESS
    private String message;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private LocalDateTime timestamp;
    private boolean acknowledged = false;
    @Column(name = "process_name")
    private String processName;
    @Column(name = "screenshot_url")
    private String screenshotUrl;
    private String source;

    // Constructors
    public Alert() {}

    public Alert(String deviceId, String type, String message, String severity) {
        this.deviceId = deviceId;
        this.type = type;
        this.message = message;
        this.severity = severity;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
