package com.iotmonitor.controller;

import com.iotmonitor.service.SupabaseStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/screenshots")
public class ScreenshotController {

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    private static final Map<String, String> latestScreenshotUrls = new ConcurrentHashMap<>();

    // Agent uploads screenshot here
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadScreenshot(@RequestParam("file") MultipartFile file, @RequestParam(value = "deviceId", defaultValue = "default") String deviceId) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            String objectPath = "screenshots/" + deviceId + "/latest.png";
            String imageUrl = supabaseStorageService.uploadObject(deviceId, objectPath, file.getBytes());

            latestScreenshotUrls.put(deviceId, imageUrl);

            System.out.println("Screenshot uploaded for device: " + deviceId + " at " + imageUrl);

            return ResponseEntity.ok(Map.of("message", "Screenshot uploaded", "imageUrl", imageUrl));
        } catch (Exception e) {
            System.err.println("Screenshot upload failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed"));
        }
    }

    // Dashboard loads latest screenshot URL here
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestScreenshot(@RequestParam(value = "deviceId", defaultValue = "default") String deviceId) {
        String imageUrl = latestScreenshotUrls.get(deviceId);
        if (imageUrl == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }
}