package com.iotmonitor.controller;

import com.iotmonitor.model.FileMetadata;
import com.iotmonitor.repository.FileMetadataRepository;
import com.iotmonitor.service.SupabaseStorageService;
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

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired(required = false)
    private SupabaseStorageService supabaseStorageService;

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<?> getFilesByDevice(@PathVariable String deviceId) {
        try {
            // Prefer Supabase metadata as source of truth
            if (supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled()) {
                List<Map<String,Object>> supabaseRows = supabaseStorageService.fetchFileMetadata(deviceId);
                return ResponseEntity.ok(supabaseRows);
            } else {
                // Supabase not configured, use local database
                List<FileMetadata> files = fileMetadataRepository.findByDeviceId(deviceId);
                return ResponseEntity.ok(files);
            }
        } catch (Exception e) {
            // Fallback to local database if Supabase side is unavailable
            List<FileMetadata> files = fileMetadataRepository.findByDeviceId(deviceId);
            return ResponseEntity.ok(files);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllFiles() {
        try {
            if (supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled()) {
                List<Map<String,Object>> supabaseRows = supabaseStorageService.fetchFileMetadata(null);
                return ResponseEntity.ok(supabaseRows);
            } else {
                // Supabase not configured, use local database
                List<FileMetadata> files = fileMetadataRepository.findAll();
                return ResponseEntity.ok(files);
            }
        } catch (Exception e) {
            List<FileMetadata> files = fileMetadataRepository.findAll();
            return ResponseEntity.ok(files);
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentFiles(@RequestParam(defaultValue = "10") int limit) {
        try {
            if (supabaseStorageService != null && supabaseStorageService.isSupabaseEnabled()) {
                List<Map<String,Object>> supabaseRows = supabaseStorageService.fetchRecentFiles(limit);
                return ResponseEntity.ok(supabaseRows);
            } else {
                // Supabase not configured, use local database
                List<FileMetadata> files = fileMetadataRepository.findTop10ByOrderByUploadTimeDesc();
                return ResponseEntity.ok(files.subList(0, Math.min(limit, files.size())));
            }
        } catch (Exception e) {
            List<FileMetadata> files = fileMetadataRepository.findTop10ByOrderByUploadTimeDesc();
            return ResponseEntity.ok(files.subList(0, Math.min(limit, files.size())));
        }
    }

    @GetMapping("/supabase")
    public ResponseEntity<?> getSupabaseFiles(@RequestParam(value = "deviceId", required = false) String deviceId) {
        if (supabaseStorageService == null || !supabaseStorageService.isSupabaseEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Supabase storage service is not configured"));
        }
        List<Map<String, Object>> supabaseRows = supabaseStorageService.fetchFileMetadata(deviceId);
        return ResponseEntity.ok(supabaseRows);
    }

    // Alias route for legacy frontend code and direct dashboard use.
    @GetMapping("/files/{deviceId}")
    public ResponseEntity<?> getFilesLegacy(@PathVariable String deviceId) {
        return getFilesByDevice(deviceId);
    }

    @GetMapping("/files")
    public ResponseEntity<?> getFilesLegacyAll() {
        return getAllFiles();
    }
}
