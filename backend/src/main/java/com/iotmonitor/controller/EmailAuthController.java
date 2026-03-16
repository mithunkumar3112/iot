package com.iotmonitor.controller;

import com.iotmonitor.dto.AuthResponse;
import com.iotmonitor.dto.EmailLoginRequest;
import com.iotmonitor.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class EmailAuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public EmailAuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/email-login")
    public ResponseEntity<?> login(@RequestBody EmailLoginRequest request) {
        if (request.getEmail() != null && request.getEmail().contains("@") && "admin123".equals(request.getPassword())) {
            String token = jwtTokenProvider.generateToken(request.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));
        }
        return ResponseEntity.status(401).body("Invalid email or password");
    }
}
