package com.iotmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_activity")
public class AppActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private String appName;
    private String status; // OPENED or CLOSED
    private LocalDateTime timestamp;
    private String appPath; // Optional: full path to app executable

    // Constructors
    public AppActivity() {}

    public AppActivity(String deviceId, String appName, String status) {
        this.deviceId = deviceId;
        this.appName = appName;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public AppActivity(String deviceId, String appName, String status, String appPath) {
        this.deviceId = deviceId;
        this.appName = appName;
        this.status = status;
        this.appPath = appPath;
        this.timestamp = LocalDateTime.now();
    }

    public AppActivity(String deviceId, String appName, String status, String appPath, LocalDateTime timestamp) {
        this.deviceId = deviceId;
        this.appName = appName;
        this.status = status;
        this.appPath = appPath;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getAppPath() { return appPath; }
    public void setAppPath(String appPath) { this.appPath = appPath; }

    @Override
    public String toString() {
        return "AppActivity{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", appName='" + appName + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                ", appPath='" + appPath + '\'' +
                '}';
    }
}
