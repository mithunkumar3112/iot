package com.iotmonitor.controller;

import com.iotmonitor.model.FileMetadata;
import com.iotmonitor.repository.FileMetadataRepository;
import com.iotmonitor.service.SupabaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
public class FileMetadataController {

    private static final Logger logger = LoggerFactory.getLogger(FileMetadataController.class);

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired(required = false)
    private SupabaseStorageService supabaseStorageService;

    /**
     * Get files for a specific device.
     * Prefers Supabase as source of truth, falls back to local database.
     * 
     * @param deviceId The device ID to filter by
     * @return List of files for the device
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<?> getFilesByDevice(@PathVariable String deviceId) {
        try {
            logger.info("API call: /files/device/{} - Fetching files for device", deviceId);
            
            // Prefer Supabase metadata as source of truth
            if (supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled()) {
                logger.debug("Supabase enabled, fetching file metadata from cloud");
                List<Map<String,Object>> supabaseRows = supabaseStorageService.fetchFileMetadata(deviceId);
                logger.info("Supabase returned {} files for device {}", 
                    supabaseRows != null ? supabaseRows.size() : 0, deviceId);
                return ResponseEntity.ok(supabaseRows != null ? supabaseRows : List.of());
            } else {
                logger.debug("Supabase disabled, fetching from local database");
                List<FileMetadata> files = fileMetadataRepository.findByDeviceId(deviceId);
                logger.info("Local database returned {} files for device {}", files.size(), deviceId);
                return ResponseEntity.ok(files);
            }
        } catch (Exception e) {
            logger.error("Error fetching files for device {}: {}", deviceId, e.getMessage(), e);
            // Fallback to local database if Supabase side is unavailable
            try {
                List<FileMetadata> files = fileMetadataRepository.findByDeviceId(deviceId);
                logger.info("Fallback to local database succeeded, returning {} files", files.size());
                return ResponseEntity.ok(files);
            } catch (Exception fallbackError) {
                logger.error("Fallback also failed: {}", fallbackError.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch files", "message", e.getMessage()));
            }
        }
    }

    /**
     * Get all files from all devices.
     * Used as fallback when device ID is not specified.
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllFiles() {
        try {
            logger.info("API call: /files/all - Fetching all files from all devices");
            
            if (supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled()) {
                logger.debug("Supabase enabled, fetching all file metadata");
                List<Map<String,Object>> supabaseRows = supabaseStorageService.fetchFileMetadata(null);
                logger.info("Supabase returned {} total files", 
                    supabaseRows != null ? supabaseRows.size() : 0);
                return ResponseEntity.ok(supabaseRows != null ? supabaseRows : List.of());
            } else {
                logger.debug("Supabase disabled, fetching from local database");
                List<FileMetadata> files = fileMetadataRepository.findAll();
                logger.info("Local database returned {} total files", files.size());
                return ResponseEntity.ok(files);
            }
        } catch (Exception e) {
            logger.error("Error fetching all files: {}", e.getMessage(), e);
            // Fallback to local database
            try {
                List<FileMetadata> files = fileMetadataRepository.findAll();
                logger.info("Fallback succeeded, returning {} files", files.size());
                return ResponseEntity.ok(files);
            } catch (Exception fallbackError) {
                logger.error("Fallback failed: {}", fallbackError.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch files", "message", e.getMessage()));
            }
        }
    }

    /**
     * Get recent files, ordered by upload time descending.
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentFiles(@RequestParam(defaultValue = "10") int limit) {
        try {
            logger.info("API call: /files/recent - Fetching {} most recent files", limit);
            
            if (supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled()) {
                logger.debug("Supabase enabled, fetching recent files");
                List<Map<String,Object>> supabaseRows = supabaseStorageService.fetchRecentFiles(limit);
                logger.info("Supabase returned {} recent files", 
                    supabaseRows != null ? supabaseRows.size() : 0);
                return ResponseEntity.ok(supabaseRows != null ? supabaseRows : List.of());
            } else {
                logger.debug("Supabase disabled, fetching from local database");
                List<FileMetadata> files = fileMetadataRepository.findTop10ByOrderByUploadTimeDesc();
                int resultSize = Math.min(limit, files.size());
                logger.info("Local database returned {} recent files", resultSize);
                return ResponseEntity.ok(files.subList(0, resultSize));
            }
        } catch (Exception e) {
            logger.error("Error fetching recent files: {}", e.getMessage(), e);
            try {
                List<FileMetadata> files = fileMetadataRepository.findTop10ByOrderByUploadTimeDesc();
                int resultSize = Math.min(limit, files.size());
                logger.info("Fallback succeeded, returning {} files", resultSize);
                return ResponseEntity.ok(files.subList(0, resultSize));
            } catch (Exception fallbackError) {
                logger.error("Fallback failed: {}", fallbackError.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch recent files"));
            }
        }
    }

    /**
     * Get files directly from Supabase (diagnostic endpoint).
     */
    @GetMapping("/supabase")
    public ResponseEntity<?> getSupabaseFiles(@RequestParam(value = "deviceId", required = false) String deviceId) {
        try {
            logger.info("API call: /files/supabase - Diagnostic endpoint for device {}", deviceId);
            
            if (supabaseStorageService == null || !supabaseStorageService.isSupabaseEnabled()) {
                logger.warn("Supabase service is not enabled or not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Supabase storage service is not configured"));
            }
            
            List<Map<String, Object>> supabaseRows = supabaseStorageService.fetchFileMetadata(deviceId);
            logger.info("Supabase diagnostic returned {} files", supabaseRows != null ? supabaseRows.size() : 0);
            return ResponseEntity.ok(supabaseRows != null ? supabaseRows : List.of());
        } catch (Exception e) {
            logger.error("Supabase diagnostic endpoint error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Supabase query failed", "message", e.getMessage()));
        }
    }

    /**
     * Alias route for legacy frontend code and direct dashboard use.
     */
    @GetMapping("/files/{deviceId}")
    public ResponseEntity<?> getFilesLegacy(@PathVariable String deviceId) {
        logger.debug("Legacy endpoint called: /files/files/{}", deviceId);
        return getFilesByDevice(deviceId);
    }

    /**
     * Alias route for legacy frontend code.
     */
    @GetMapping("/files")
    public ResponseEntity<?> getFilesLegacyAll() {
        logger.debug("Legacy endpoint called: /files/files");
        return getAllFiles();
    }

    /**
     * DEBUG: Get Supabase service status
     */
    @GetMapping("/debug/status")
    public ResponseEntity<?> getStatus() {
        try {
            boolean supabaseEnabled = supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled();
            int localFileCount = fileMetadataRepository.findAll().size();
            
            return ResponseEntity.ok(Map.of(
                "supabaseEnabled", supabaseEnabled,
                "localDatabaseFileCount", localFileCount,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            logger.error("Debug status endpoint error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get status"));
        }
    }

    /**
     * DEBUG: Get local database files
     */
    @GetMapping("/debug/local")
    public ResponseEntity<?> getLocalFiles() {
        try {
            List<FileMetadata> files = fileMetadataRepository.findAll();
            logger.info("DEBUG: Returning {} files from local database", files.size());
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            logger.error("Debug local files endpoint error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch local files"));
        }
    }
}
