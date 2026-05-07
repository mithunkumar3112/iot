package com.iotmonitor.controller;

import com.iotmonitor.model.LoginSession;
import com.iotmonitor.repository.LoginSessionRepository;
import com.iotmonitor.service.SecurityMonitoringService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SessionController {

    private final LoginSessionRepository loginSessionRepository;
    private final SecurityMonitoringService securityService;

    public SessionController(LoginSessionRepository loginSessionRepository, SecurityMonitoringService securityService) {
        this.loginSessionRepository = loginSessionRepository;
        this.securityService = securityService;
    }

    @PostMapping("/sessions")
    public LoginSession createSession(@RequestBody LoginSession session) {
        return securityService.recordSession(session);
    }

    @GetMapping("/sessions")
    public List<LoginSession> getSessions() {
        return loginSessionRepository.findTop200ByOrderByLoginTimeDesc();
    }

    @GetMapping("/sessions/{deviceId}")
    public List<LoginSession> getSessionsByDevice(@PathVariable String deviceId) {
        return loginSessionRepository.findTop200ByDeviceIdOrderByLoginTimeDesc(deviceId);
    }
}
