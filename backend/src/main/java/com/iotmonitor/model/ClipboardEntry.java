package com.iotmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "clipboard_entry")
public class ClipboardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_type", nullable = false)
    private String contentType = "text"; // "text" or "image"

    @Column(name = "content_size", nullable = false)
    private Long contentSize = 0L;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    public ClipboardEntry() {}

    public ClipboardEntry(String deviceId, String content, LocalDateTime timestamp) {
        this.deviceId = deviceId;
        this.content = content != null ? content : "";
        this.contentType = "text";
        this.contentSize = (long) this.content.length();
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public ClipboardEntry(String deviceId, String content, String contentType, Long contentSize, LocalDateTime timestamp) {
        this.deviceId = deviceId;
        this.content = content != null ? content : "";
        this.contentType = contentType != null ? contentType : "text";
        this.contentSize = contentSize != null ? contentSize : 0L;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getContentSize() {
        return contentSize;
    }

    public void setContentSize(Long contentSize) {
        this.contentSize = contentSize;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
