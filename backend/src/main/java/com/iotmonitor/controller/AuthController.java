package com.iotmonitor.controller;

import com.iotmonitor.dto.*;
import com.iotmonitor.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        // Accept any non-empty device ID and empty password for dashboard login.
        if (username != null && !username.isBlank() && password != null && password.isEmpty()) {
            String token = jwtTokenProvider.generateToken(username.trim());
            return ResponseEntity.ok(new AuthResponse(token));
        }

        return ResponseEntity.status(401).body("Invalid credentials");
    }
}
