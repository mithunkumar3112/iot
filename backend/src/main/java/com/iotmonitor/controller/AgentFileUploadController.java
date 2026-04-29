package com.iotmonitor.controller;

import com.iotmonitor.model.FileMetadata;
import com.iotmonitor.repository.FileMetadataRepository;
import com.iotmonitor.service.SupabaseStorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class AgentFileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(AgentFileUploadController.class);

    // Optional local mirror (retain this location for UI legacy behavior / offline access, but main storage is Supabase)
    private static final Path BASE_UPLOAD_DIR = Paths.get("C:/shared-files");

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired(required = false)
    private SupabaseStorageService supabaseStorageService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void init() throws IOException {
        if (!Files.exists(BASE_UPLOAD_DIR)) {
            Files.createDirectories(BASE_UPLOAD_DIR);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "deviceId", required = false) String deviceId) {
        try {
            String effectiveDeviceId = (deviceId != null && !deviceId.isBlank()) ? deviceId : "unknown-device";

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file name"));
            }

            String safeFilename = Paths.get(originalFilename).getFileName().toString();
            if (safeFilename.contains("..")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file name"));
            }

            byte[] bytes = file.getBytes();
            String sha256 = computeSha256(bytes);
            String hashWithSize = sha256 + ":" + bytes.length;

            String supabaseObjectUrl;
            Map<String, Object> supabaseRow;

            if (supabaseStorageService == null) {
                // Supabase not configured - save locally
                try {
                    Path localDir = Paths.get("uploads", effectiveDeviceId);
                    Files.createDirectories(localDir);
                    Path localFile = localDir.resolve(safeFilename);
                    Files.write(localFile, bytes);
                    supabaseObjectUrl = "local://" + localFile.toString();

                    FileMetadata metadata = new FileMetadata(
                            safeFilename,
                            supabaseObjectUrl,
                            effectiveDeviceId,
                            LocalDateTime.now(),
                            bytes.length
                    );
                    fileMetadataRepository.save(metadata);

                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Supabase not configured, file stored locally");
                    response.put("fileUrl", supabaseObjectUrl);
                    response.put("deviceId", effectiveDeviceId);
                    response.put("fileSize", bytes.length);
                    response.put("sha256", sha256);
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Failed to save file locally: " + e.getMessage()));
                }
            }

            try {
                supabaseObjectUrl = supabaseStorageService.uploadObject(effectiveDeviceId, safeFilename, bytes);
                
                if (supabaseObjectUrl.startsWith("local://")) {
                    // Cloud disabled - save metadata locally
                    FileMetadata metadata = new FileMetadata(
                            safeFilename,
                            supabaseObjectUrl,
                            effectiveDeviceId,
                            LocalDateTime.now(),
                            bytes.length
                    );
                    fileMetadataRepository.save(metadata);

                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Cloud disabled, file stored locally");
                    response.put("fileUrl", supabaseObjectUrl);
                    response.put("deviceId", effectiveDeviceId);
                    response.put("fileSize", bytes.length);
                    response.put("sha256", sha256);
                    return ResponseEntity.ok(response);
                }
                
                supabaseRow = supabaseStorageService.persistFileMetadata(
                        safeFilename,
                        supabaseObjectUrl,
                        effectiveDeviceId,
                        bytes.length,
                        sha256,
                        hashWithSize
                );
            } catch (Exception e) {
                logger.warn("Supabase upload or metadata persist failed; falling back to local storage", e);
                String supabaseError = e.getMessage();

                Path deviceDir = BASE_UPLOAD_DIR.resolve(effectiveDeviceId);
                if (!Files.exists(deviceDir)) {
                    Files.createDirectories(deviceDir);
                }
                Path dest = deviceDir.resolve(safeFilename).normalize().toAbsolutePath();
                Files.write(dest, bytes);

                FileMetadata metadata = new FileMetadata(
                        safeFilename,
                        dest.toUri().toString(),
                        effectiveDeviceId,
                        LocalDateTime.now(),
                        bytes.length
                );
                fileMetadataRepository.save(metadata);

                Map<String, Object> response = new HashMap<>();
                response.put("message", "File saved locally; Supabase unavailable");
                response.put("fileUrl", dest.toUri().toString());
                response.put("deviceId", effectiveDeviceId);
                response.put("hash", sha256);
                response.put("error", supabaseError);
                return ResponseEntity.status(503).body(response);
            }

            String fileUrl = supabaseObjectUrl;
            FileMetadata metadata = new FileMetadata(
                    safeFilename,
                    fileUrl,
                    effectiveDeviceId,
                    LocalDateTime.now(),
                    bytes.length
            );
            fileMetadataRepository.save(metadata);

            // Emit WebSocket event for real-time update
            Map<String, Object> fileEvent = Map.of(
                "event", "file_uploaded",
                "fileName", safeFilename,
                "deviceId", effectiveDeviceId,
                "fileSize", bytes.length,
                "uploadTime", metadata.getUploadTime().toString()
            );
            messagingTemplate.convertAndSend("/topic/files", fileEvent);

            Map<String, Object> response = new HashMap<>();
            if (supabaseRow == null) {
                response.put("message", "File uploaded to Supabase successfully, but Supabase metadata table is unavailable");
            } else {
                response.put("message", "File uploaded to Supabase successfully");
                response.put("supabaseRow", supabaseRow);
            }
            response.put("fileUrl", fileUrl);
            response.put("deviceId", effectiveDeviceId);
            response.put("metadataId", metadata.getId());
            response.put("hash", sha256);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save file: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload error: " + e.getMessage()));
        }
    }

    private String computeSha256(byte[] content) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
