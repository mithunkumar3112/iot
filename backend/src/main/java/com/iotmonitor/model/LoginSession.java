package com.iotmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_sessions", indexes = {
        @Index(name = "idx_session_device_time", columnList = "device_id,login_time"),
        @Index(name = "idx_session_status", columnList = "status")
})
public class LoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;
    private String username;
    @Column(name = "login_time")
    private LocalDateTime loginTime;
    @Column(name = "logout_time")
    private LocalDateTime logoutTime;
    private String status;

    public LoginSession() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getLoginTime() { return loginTime; }
    public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }

    public LocalDateTime getLogoutTime() { return logoutTime; }
    public void setLogoutTime(LocalDateTime logoutTime) { this.logoutTime = logoutTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
