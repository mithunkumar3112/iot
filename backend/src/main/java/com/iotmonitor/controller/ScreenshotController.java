package com.iotmonitor.controller;

import com.iotmonitor.service.SupabaseStorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Value("${app.file.storage-dir:${java.io.tmpdir}/iot-monitor/screenshots}")
    private String storageDir;

    public ScreenshotController(SupabaseStorageService supabaseStorageService,
                                SimpMessagingTemplate messagingTemplate) {
        this.supabaseStorageService = supabaseStorageService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get(storageDir).toAbsolutePath().normalize());
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadScreenshot(@RequestParam("file") MultipartFile file,
                                                                @RequestParam(value = "deviceId", defaultValue = "default") String deviceId) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String safeDeviceId = deviceId == null || deviceId.isBlank() ? "default" : deviceId.trim();
        try {
            byte[] imageBytes = file.getBytes();
            Path localFile = localScreenshotPath(safeDeviceId);
            Files.createDirectories(localFile.getParent());
            Files.write(localFile, imageBytes);

            String imageUrl = "/api/screenshots/image?deviceId=" + java.net.URLEncoder.encode(safeDeviceId, java.nio.charset.StandardCharsets.UTF_8);
            String cloudImageUrl = "";
            if (supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled()) {
                try {
                    cloudImageUrl = supabaseStorageService.uploadObject(safeDeviceId, "screenshots/latest.png", imageBytes);
                } catch (Exception e) {
                    logger.warn("Supabase screenshot upload failed; using local screenshot {}", localFile, e);
                }
            }
            latestScreenshotUrls.put(safeDeviceId, imageUrl);

            logger.info("Screenshot uploaded: deviceId={} size={} imageUrl={}", safeDeviceId, file.getSize(), imageUrl);

            Map<String, Object> wsEvent = new HashMap<>();
            wsEvent.put("type", "SCREENSHOT_UPDATED");
            wsEvent.put("event", "screenshot_updated");
            wsEvent.put("deviceId", safeDeviceId);
            wsEvent.put("imageUrl", imageUrl);
            wsEvent.put("cloudImageUrl", cloudImageUrl);
            wsEvent.put("timestamp", java.time.Instant.now().toString());
            messagingTemplate.convertAndSend("/topic/screenshots", wsEvent);
            logger.info("Screenshot WebSocket sent: topic=/topic/screenshots deviceId={}", safeDeviceId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Screenshot uploaded");
            response.put("imageUrl", imageUrl);
            response.put("cloudImageUrl", cloudImageUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Screenshot upload failed for deviceId={}: {}", safeDeviceId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed", "message", e.getMessage()));
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestScreenshot(@RequestParam(value = "deviceId", defaultValue = "default") String deviceId) {
        String safeDeviceId = deviceId == null || deviceId.isBlank() ? "default" : deviceId.trim();
        String imageUrl = latestScreenshotUrls.get(safeDeviceId);
        if ((imageUrl == null || imageUrl.isBlank()) && Files.exists(localScreenshotPath(safeDeviceId))) {
            imageUrl = "/api/screenshots/image?deviceId=" + java.net.URLEncoder.encode(safeDeviceId, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (imageUrl == null && supabaseStorageService != null && supabaseStorageService.objectExists(safeDeviceId, "screenshots/latest.png")) {
            imageUrl = supabaseStorageService.publicObjectUrl(safeDeviceId, "screenshots/latest.png");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.ok(Map.of("imageUrl", "", "message", "No screenshot uploaded yet"));
        }
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    @GetMapping("/image")
    public ResponseEntity<?> getScreenshotImage(@RequestParam(value = "deviceId", defaultValue = "default") String deviceId) {
        String safeDeviceId = deviceId == null || deviceId.isBlank() ? "default" : deviceId.trim();
        try {
            Path file = localScreenshotPath(safeDeviceId);
            if (!Files.exists(file) || !Files.isReadable(file)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(file.toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(resource);
        } catch (Exception e) {
            logger.warn("Unable to read local screenshot for deviceId={}: {}", safeDeviceId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private Path localScreenshotPath(String deviceId) {
        String safeDeviceId = (deviceId == null || deviceId.isBlank() ? "default" : deviceId.trim())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get(storageDir).toAbsolutePath().normalize().resolve(safeDeviceId).resolve("latest.png").normalize();
    }
}
