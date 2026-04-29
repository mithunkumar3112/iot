package com.iotmonitor.controller;

import com.iotmonitor.service.SupabaseStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@RestController
@RequestMapping("/config")
public class ConfigController {

    private final SupabaseStorageService supabaseStorageService;

    @Value("${supabase.enabled:true}")
    private boolean supabaseEnabled;

    public ConfigController(SupabaseStorageService supabaseStorageService) {
        this.supabaseStorageService = supabaseStorageService;
    }

    @PostConstruct
    public void init() {
        supabaseStorageService.setSupabaseEnabled(supabaseEnabled);
    }

    @PostMapping("/cloud-toggle")
    public ResponseEntity<?> toggleCloud(@RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled field is required"));
        }
        supabaseStorageService.setSupabaseEnabled(enabled);
        return ResponseEntity.ok(Map.of("message", "Cloud " + (enabled ? "enabled" : "disabled"), "enabled", enabled));
    }

    @GetMapping("/cloud-status")
    public ResponseEntity<?> getCloudStatus() {
        return ResponseEntity.ok(Map.of("enabled", supabaseStorageService.isSupabaseEnabled()));
    }
}