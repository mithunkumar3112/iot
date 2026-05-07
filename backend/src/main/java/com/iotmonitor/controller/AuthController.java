package com.iotmonitor.controller;

import com.iotmonitor.dto.*;
import com.iotmonitor.model.Alert;
import com.iotmonitor.model.LoginSession;
import com.iotmonitor.security.JwtTokenProvider;
import com.iotmonitor.service.SecurityMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityMonitoringService securityMonitoringService;

    public AuthController(JwtTokenProvider jwtTokenProvider, SecurityMonitoringService securityMonitoringService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityMonitoringService = securityMonitoringService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        // Accept any non-empty device ID and empty password for dashboard login.
        if (username != null && !username.isBlank() && password != null && password.isEmpty()) {
            String token = jwtTokenProvider.generateToken(username.trim());
            LoginSession session = new LoginSession();
            session.setDeviceId(username.trim());
            session.setUsername(username.trim());
            session.setLoginTime(LocalDateTime.now());
            session.setStatus("ACTIVE");
            securityMonitoringService.recordSession(session);
            return ResponseEntity.ok(new AuthResponse(token));
        }

        Alert alert = new Alert(username == null || username.isBlank() ? "dashboard" : username.trim(),
                "FAILED_LOGIN", "Wrong password/login attempt", "HIGH");
        alert.setDeviceName(username);
        alert.setSource("dashboard");
        securityMonitoringService.recordSecurityAlert(alert);

        LoginSession session = new LoginSession();
        session.setDeviceId(username == null || username.isBlank() ? "dashboard" : username.trim());
        session.setUsername(username == null || username.isBlank() ? "unknown" : username.trim());
        session.setLoginTime(LocalDateTime.now());
        session.setStatus("FAILED");
        securityMonitoringService.recordSession(session);

        return ResponseEntity.status(401).body("Invalid credentials");
    }
}
