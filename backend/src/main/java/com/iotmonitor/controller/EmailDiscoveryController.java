package com.iotmonitor.controller;

import com.iotmonitor.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.Map;

/**
 * POST /reverselink/discover
 *
 * The Android app sends the user's email address.
 * If it matches the PC's configured owner email (app.owner.email),
 * we issue a JWT token so the phone can authenticate all subsequent
 * /reverselink/fs/* requests without any QR code.
 */
@RestController
@RequestMapping("/reverselink")
public class EmailDiscoveryController {

    @Value("${app.owner.email}")
    private String ownerEmail;

    @Value("${server.port:8080}")
    private int httpPort;

    private final JwtTokenProvider jwtTokenProvider;

    public EmailDiscoveryController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/discover")
    public ResponseEntity<?> discover(@RequestBody Map<String, String> body) {
        String incomingEmail = body.get("email");

        if (incomingEmail == null || incomingEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }

        if (!incomingEmail.trim().equalsIgnoreCase(ownerEmail != null ? ownerEmail.trim() : "")) {
            return ResponseEntity.status(401).body(Map.of("error", "Email does not match PC owner. Connection denied."));
        }

        // Generate a 24-hour JWT for this email
        String token = jwtTokenProvider.generateToken(incomingEmail.trim().toLowerCase());

        // Store the authorised email so ReverseLinkFileController can verify it
        ReverseLinkAuthController.authorizedEmails.add(incomingEmail.trim().toLowerCase());

        String pcName;
        try {
            pcName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            pcName = "PC";
        }

        return ResponseEntity.ok(Map.of(
                "token", token,
                "pcName", pcName,
                "port", httpPort,
                "message", "Connected to " + pcName + " successfully"
        ));
    }
}
