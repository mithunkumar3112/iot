package com.iotmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_screenshots", indexes = {
        @Index(name = "idx_security_screenshot_device_time", columnList = "device_id,timestamp")
})
public class SecurityScreenshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;
    @Column(name = "alert_id")
    private Long alertId;
    @Column(name = "usb_activity_id")
    private Long usbActivityId;
    @Column(name = "event_type")
    private String eventType;
    @Column(name = "file_path")
    private String filePath;
    private String url;
    @Column(name = "file_size")
    private long fileSize;
    private LocalDateTime timestamp;

    public SecurityScreenshot() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public Long getUsbActivityId() { return usbActivityId; }
    public void setUsbActivityId(Long usbActivityId) { this.usbActivityId = usbActivityId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
