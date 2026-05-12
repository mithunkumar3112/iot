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

    private static final Path BASE_UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "iot-monitor-uploads");

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
            
            logger.info("FILE UPLOAD START: deviceId={}, filename={}, size={} bytes", 
                effectiveDeviceId, originalFilename, file.getSize());

            if (originalFilename == null || originalFilename.isBlank()) {
                logger.warn("FILE UPLOAD REJECTED: Invalid file name");
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file name"));
            }

            String safeFilename = Paths.get(originalFilename).getFileName().toString();
            if (safeFilename.contains("..")) {
                logger.warn("FILE UPLOAD REJECTED: Path traversal attempt in filename: {}", safeFilename);
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file name"));
            }

            byte[] bytes = file.getBytes();
            String sha256 = computeSha256(bytes);
            String hashWithSize = sha256 + ":" + bytes.length;
            
            logger.debug("FILE HASH COMPUTED: sha256={}, size={}", sha256, bytes.length);

            Path deviceUploadDir = BASE_UPLOAD_DIR.resolve(sanitizePathSegment(effectiveDeviceId));
            Files.createDirectories(deviceUploadDir);
            Path localFilePath = deviceUploadDir.resolve(safeFilename).normalize();
            
            if (!localFilePath.startsWith(deviceUploadDir)) {
                logger.warn("FILE UPLOAD REJECTED: Path traversal attempt");
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file name"));
            }
            
            // Overwrite any existing file with the same name
            Files.deleteIfExists(localFilePath);
            Files.write(localFilePath, bytes);
            
            // Build a cloud‑style path (deviceId/fileName) for metadata and UI consistency
            String fileUrl = "files/" + effectiveDeviceId + "/" + safeFilename;
            logger.debug("Generated cloud path for metadata: {}", fileUrl);
            logger.info("FILE SAVED LOCALLY (overwritten if existed): path={}", localFilePath);

            Map<String, Object> supabaseRow = null;

            if (supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled()) {
                try {
                try {
                    // Supabase upload returns the public URL; we already have the correct cloud path in fileUrl
                    fileUrl = supabaseStorageService.uploadObject(effectiveDeviceId, safeFilename, bytes);
                    logger.info("Supabase upload succeeded: {}", fileUrl);
                } catch (Exception supabaseErr) {
                    logger.warn("SUPABASE UPLOAD FAILED: Falling back to local storage only: {}", supabaseErr.getMessage(), supabaseErr);
                }
                    
                    try {
                        logger.debug("SUPABASE METADATA PERSIST STARTING: saving metadata to database");
                        supabaseRow = supabaseStorageService.persistFileMetadata(
                                safeFilename,
                                fileUrl,
                                effectiveDeviceId,
                                bytes.length,
                                sha256,
                                hashWithSize
                        );
                        logger.info("SUPABASE METADATA SUCCESS: metadataId={}", 
                            supabaseRow != null ? supabaseRow.get("id") : "null");
                    } catch (Exception metadataErr) {
                        logger.warn("SUPABASE METADATA FAILED: File in storage but metadata not saved: {}", 
                            metadataErr.getMessage());
                    }
                } catch (Exception supabaseErr) {
                    logger.warn("SUPABASE UPLOAD FAILED: Falling back to local storage only: {}", 
                        supabaseErr.getMessage(), supabaseErr);
                }
            } else {
                logger.info("SUPABASE DISABLED: Using local storage only");
            }

            FileMetadata metadata = new FileMetadata(
                    safeFilename,
                    fileUrl,
                    effectiveDeviceId,
                    LocalDateTime.now(),
                    bytes.length
            );
            fileMetadataRepository.save(metadata);
            logger.info("LOCAL DATABASE SAVED: metadataId={}, path={}", metadata.getId(), fileUrl);

            // Emit WebSocket event for real-time update
            Map<String, Object> fileEvent = Map.of(
                "event", "file_uploaded",
                "fileName", safeFilename,
                "deviceId", effectiveDeviceId,
                "fileSize", bytes.length,
                "uploadTime", metadata.getUploadTime().toString()
            );
            messagingTemplate.convertAndSend("/topic/files", fileEvent);
            logger.debug("WEBSOCKET EVENT SENT: /topic/files");

            Map<String, Object> response = new HashMap<>();
            if (supabaseRow == null) {
                response.put("message", "File uploaded locally (Supabase unavailable)");
            } else {
                response.put("message", "File uploaded to Supabase successfully");
                response.put("supabaseRow", supabaseRow);
            }
            response.put("fileUrl", fileUrl);
            response.put("localPath", localFilePath.toString());
            response.put("deviceId", effectiveDeviceId);
            response.put("metadataId", metadata.getId());
            response.put("hash", sha256);
            
            logger.info("FILE UPLOAD SUCCESS: Complete flow done for {} in device {}", safeFilename, effectiveDeviceId);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("FILE UPLOAD FAILED: IO error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save file: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("FILE UPLOAD FAILED: Unexpected error: {}", e.getMessage(), e);
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

    private String sanitizePathSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown-device";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
