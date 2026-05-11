package com.iotmonitor.controller;

import com.iotmonitor.service.SupabaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/screenshots")
public class ScreenshotController {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotController.class);
    private static final Map<String, String> latestScreenshotUrls = new ConcurrentHashMap<>();

    private final SupabaseStorageService supabaseStorageService;
    private final SimpMessagingTemplate messagingTemplate;

    public ScreenshotController(SupabaseStorageService supabaseStorageService,
                                SimpMessagingTemplate messagingTemplate) {
        this.supabaseStorageService = supabaseStorageService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadScreenshot(@RequestParam("file") MultipartFile file,
                                                                @RequestParam(value = "deviceId", defaultValue = "default") String deviceId) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String safeDeviceId = deviceId == null || deviceId.isBlank() ? "default" : deviceId.trim();
        try {
            String imageUrl = supabaseStorageService.uploadObject(safeDeviceId, "screenshots/latest.png", file.getBytes());
            latestScreenshotUrls.put(safeDeviceId, imageUrl);

            logger.info("Screenshot uploaded: deviceId={} size={} imageUrl={}", safeDeviceId, file.getSize(), imageUrl);

            Map<String, Object> wsEvent = new HashMap<>();
            wsEvent.put("type", "SCREENSHOT_UPDATED");
            wsEvent.put("event", "screenshot_updated");
            wsEvent.put("deviceId", safeDeviceId);
            wsEvent.put("imageUrl", imageUrl);
            wsEvent.put("timestamp", java.time.Instant.now().toString());
            messagingTemplate.convertAndSend("/topic/screenshots", wsEvent);
            logger.info("Screenshot WebSocket sent: topic=/topic/screenshots deviceId={}", safeDeviceId);

            return ResponseEntity.ok(Map.of("message", "Screenshot uploaded", "imageUrl", imageUrl));
        } catch (Exception e) {
            logger.error("Screenshot upload failed for deviceId={}: {}", safeDeviceId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed", "message", e.getMessage()));
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestScreenshot(@RequestParam(value = "deviceId", defaultValue = "default") String deviceId) {
        String safeDeviceId = deviceId == null || deviceId.isBlank() ? "default" : deviceId.trim();
        String imageUrl = latestScreenshotUrls.get(safeDeviceId);
        if (imageUrl == null && supabaseStorageService.objectExists(safeDeviceId, "screenshots/latest.png")) {
            imageUrl = supabaseStorageService.publicObjectUrl(safeDeviceId, "screenshots/latest.png");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.ok(Map.of("imageUrl", "", "message", "No screenshot uploaded yet"));
        }
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }
}
