package com.iotmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "active_window_activity")
public class ActiveWindowActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "active_window", nullable = false)
    private String activeWindow;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    public ActiveWindowActivity() {}

    public ActiveWindowActivity(String deviceId, String activeWindow, LocalDateTime timestamp) {
        this.deviceId = deviceId;
        this.activeWindow = activeWindow;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getActiveWindow() { return activeWindow; }
    public void setActiveWindow(String activeWindow) { this.activeWindow = activeWindow; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
