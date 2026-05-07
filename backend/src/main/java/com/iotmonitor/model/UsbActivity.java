package com.iotmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usb_activity", indexes = {
        @Index(name = "idx_usb_device_time", columnList = "device_id,timestamp"),
        @Index(name = "idx_usb_time", columnList = "timestamp")
})
public class UsbActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;
    @Column(name = "device_name")
    private String deviceName;
    @Column(name = "connection_type")
    private String connectionType;
    private String status;
    private LocalDateTime timestamp;
    @Column(name = "screenshot_url")
    private String screenshotUrl;

    public UsbActivity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }
}
