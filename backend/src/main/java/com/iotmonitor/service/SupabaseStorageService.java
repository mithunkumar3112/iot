package com.iotmonitor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupabaseStorageService {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseStorageService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    @Value("${supabase.service-role-key:}")
    private String supabaseServiceRoleKey;

    @Value("${supabase.bucket.name:}")
    private String bucket;

    @Value("${supabase.bucket:}")
    private String bucketFallback;

    @Value("${supabase.storage.url:}")
    private String supabaseStorageUrl;

    @Value("${supabase.region:}")
    private String supabaseRegion;

    @Value("${supabase.public-url-base:}")
    private String publicUrlBase;

    @Value("${supabase.enabled:true}")
    private boolean supabaseEnabled;

    public void setSupabaseEnabled(boolean enabled) {
        this.supabaseEnabled = enabled;
    }

    public boolean isSupabaseEnabled() {
        return supabaseEnabled;
    }

    public SupabaseStorageService() {
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        String authKey = getSupabaseAuthKey();
        String effectiveBucket = getEffectiveBucket();
        if (!supabaseEnabled || supabaseUrl == null || supabaseUrl.isBlank() || authKey == null || authKey.isBlank() || effectiveBucket == null || effectiveBucket.isBlank()) {
            supabaseEnabled = false;
            logger.warn("SupabaseStorageService disabled. Configuration invalid or missing: supabaseUrlPresent={}, bucket={}, authKeyPresent={}",
                    supabaseUrl != null && !supabaseUrl.isBlank(), effectiveBucket, authKey != null && !authKey.isBlank());
            return;
        }

        String baseUrl = getStorageBaseUrl();
        logger.info("SupabaseStorageService initialized: supabaseUrl={} bucket={} storageBaseUrl={}",
                supabaseUrl, effectiveBucket, baseUrl);
        verifyBucket(effectiveBucket);
    }

    private String getSupabaseAuthKey() {
        if (supabaseServiceRoleKey != null && !supabaseServiceRoleKey.isBlank()) {
            return supabaseServiceRoleKey;
        }
        return supabaseKey;
    }

    private HttpHeaders authHeaders() {
        String keyToUse = getSupabaseAuthKey();
        if (keyToUse == null || keyToUse.isBlank()) {
            throw new IllegalStateException("Supabase API key is not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", keyToUse);
        headers.set("Authorization", "Bearer " + keyToUse);
        return headers;
    }

    private HttpHeaders storageAuthHeaders() {
        return authHeaders();
    }

    private String getEffectiveBucket() {
        if (bucket != null && !bucket.isBlank()) {
            return bucket;
        }
        if (bucketFallback != null && !bucketFallback.isBlank()) {
            return bucketFallback;
        }
        return "files";
    }

    private String getStorageBaseUrl() {
        if (supabaseStorageUrl != null && !supabaseStorageUrl.isBlank()) {
            return supabaseStorageUrl.replaceAll("/+$", "");
        }
        if (supabaseUrl != null && !supabaseUrl.isBlank()) {
            return supabaseUrl.replaceAll("/+$", "") + "/storage/v1";
        }
        return ""; // fallback for when supabaseUrl is not configured
    }

    private String buildStorageUrl(String bucketName, String objectPath) {
        String base = getStorageBaseUrl();
        // Supabase Storage API: /storage/v1/object/{bucketName}/{objectPath}
        return String.format("%s/object/%s/%s", base, bucketName, objectPath);
    }

    private void verifyBucket(String bucketName) {
        try {
            String url = getStorageBaseUrl() + "/bucket/" + bucketName;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
            logger.info("Supabase bucket check completed: bucket={} status={}", bucketName, response.getStatusCode());
        } catch (HttpStatusCodeException ex) {
            logger.error("Supabase bucket check failed: bucket={} status={} body={}", bucketName, ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("Supabase bucket check failed: bucket={} error={}", bucketName, e.getMessage());
        }
    }

    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String uploadObject(String deviceId, String safeFilename, byte[] bytes) {
        if (!supabaseEnabled) {
            throw new IllegalStateException("Supabase storage is disabled or misconfigured. Check SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY/SUPABASE_KEY, and SUPABASE_BUCKET_NAME on Render.");
        }

        String effectiveBucket = getEffectiveBucket();
        if (effectiveBucket == null || effectiveBucket.isBlank()) {
            throw new IllegalStateException("Supabase bucket is not configured");
        }

        String objectPath = safeObjectPath(deviceId, safeFilename);
        // Append upsert query param so Supabase overwrites existing files
        String url = buildStorageUrl(effectiveBucket, objectPath) + "?upsert=true";

        HttpHeaders headers = storageAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        // Retain header for backward compatibility; Supabase also accepts ?upsert=true
        headers.set("x-upsert", "true");

        HttpEntity<byte[]> request = new HttpEntity<>(bytes, headers);

        logger.info("Supabase upload request: method=POST bucket={} path={} bytes={} authHeader={}",
                effectiveBucket, objectPath, bytes == null ? 0 : bytes.length, headers.getFirst("Authorization") != null ? "present" : "missing");

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            logger.info("Supabase upload successful: status={} path={}", response.getStatusCode(), objectPath);
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            logger.error("Supabase upload failed: url={} method=POST status={} statusText={} body={} headers={}",
                url, ex.getStatusCode(), ex.getStatusText(), body, ex.getResponseHeaders());
            
            // Treat 409 Conflict (duplicate file) as success - file already exists in cloud storage
            if (ex.getStatusCode().value() == 409) {
                logger.warn("Supabase file already exists (409 Conflict), treating as success: {}", objectPath);
                if (publicUrlBase != null && !publicUrlBase.isBlank()) {
                    return publicUrlBase.replaceAll("/+$", "") + "/" + objectPath;
                }
                return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, effectiveBucket, objectPath);
            }
            throw new IllegalStateException("Supabase upload failed: " + ex.getStatusCode() + " - " + body, ex);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            logger.error("Supabase upload returned non-2xx status: {} body={}", response.getStatusCode(), response.getBody());
            throw new IllegalStateException("Supabase upload failed with status " + response.getStatusCode() + " body=" + response.getBody());
        }

        if (publicUrlBase != null && !publicUrlBase.isBlank()) {
            return publicUrlBase.replaceAll("/+$", "") + "/" + objectPath;
            
            // Treat 409 Conflict (duplicate file) as success - file already exists in cloud storage
            if (ex.getStatusCode().value() == 409) {
                logger.warn("Supabase file already exists (409 Conflict), treating as success: {}", objectPath);
                if (publicUrlBase != null && !publicUrlBase.isBlank()) {
                    return publicUrlBase.replaceAll("/+$", "") + "/" + objectPath;
                }
                return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, effectiveBucket, objectPath);
            }
            throw new IllegalStateException("Supabase upload failed: " + ex.getStatusCode() + " - " + body, ex);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            logger.error("Supabase upload returned non-2xx status: {} body={}", response.getStatusCode(), response.getBody());
            throw new IllegalStateException("Supabase upload failed with status " + response.getStatusCode() + " body=" + response.getBody());
        }

        if (publicUrlBase != null && !publicUrlBase.isBlank()) {
            return publicUrlBase.replaceAll("/+$", "") + "/" + objectPath;
        }

        return String.format("%s/storage/v1/object/public/%s/files/%s", supabaseUrl, effectiveBucket, objectPath);
    }

    private String safeObjectPath(String deviceId, String fileName) {
        String safeDeviceId = (deviceId == null || deviceId.isBlank() ? "unknown-device" : deviceId.trim())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String normalizedFileName = (fileName == null || fileName.isBlank() ? "upload.bin" : fileName.trim())
                .replace("\\", "/")
                .replaceAll("^/+", "");
        while (normalizedFileName.contains("..")) {
            normalizedFileName = normalizedFileName.replace("..", "_");
        }
        return safeDeviceId + "/" + normalizedFileName;
    }

    public String createSignedUrl(String deviceId, String safeFilename, int expiresInSeconds) {
        String effectiveBucket = getEffectiveBucket();
        String objectPath = safeObjectPath(deviceId, safeFilename);
        String url = String.format("%s/storage/v1/object/sign/%s/%s", supabaseUrl, effectiveBucket, objectPath);

        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("expiresIn", expiresInSeconds);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<?, ?> result = restTemplate.postForObject(url, request, Map.class);

        if (result == null || !result.containsKey("signedURL")) {
            throw new IllegalStateException("Signed URL generation failed");
        }

        return result.get("signedURL").toString();
    }

    public String publicObjectUrl(String deviceId, String safeFilename) {
        String objectPath = safeObjectPath(deviceId, safeFilename);
        if (publicUrlBase != null && !publicUrlBase.isBlank()) {
            return publicUrlBase.replaceAll("/+$", "") + "/" + objectPath;
        }
        return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, getEffectiveBucket(), objectPath);
    }

    public boolean objectExists(String deviceId, String safeFilename) {
        if (!supabaseEnabled) {
            return false;
        }
        String objectPath = safeObjectPath(deviceId, safeFilename);
        String url = buildStorageUrl(getEffectiveBucket(), objectPath);
        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.HEAD, new HttpEntity<>(authHeaders()), Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            logger.warn("Supabase object existence check failed: path={} status={} body={}", objectPath, ex.getStatusCode(), ex.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            logger.warn("Supabase object existence check failed: path={} error={}", objectPath, e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> fetchFileMetadata(String deviceId) {
        if (!supabaseEnabled) {
            logger.debug("Supabase disabled, returning empty list for file metadata");
            return new ArrayList<>(); // Return empty list when Supabase is disabled
        }

        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(supabaseUrl + "/rest/v1/file_metadata")
                .queryParam("select", "*");

        if (deviceId != null && !deviceId.isBlank()) {
            uri.queryParam("device_id", "eq." + deviceId);
        }

        HttpHeaders headers = authHeaders();
        headers.set("Accept", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<List> response = restTemplate.exchange(uri.toUriString(), HttpMethod.GET, request, List.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Supabase metadata fetch failed: " + response.getStatusCode());
        }

        return response.getBody();
    }

    public List<Map<String, Object>> fetchRecentFiles(int limit) {
        if (!supabaseEnabled) {
            logger.debug("Supabase disabled, returning empty list for recent files");
            return new ArrayList<>(); // Return empty list when Supabase is disabled
        }

        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(supabaseUrl + "/rest/v1/file_metadata")
                .queryParam("select", "*")
                .queryParam("order", "upload_time.desc")
                .queryParam("limit", limit);

        HttpHeaders headers = authHeaders();
        headers.set("Accept", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<List> response = restTemplate.exchange(uri.toUriString(), HttpMethod.GET, request, List.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Supabase recent files fetch failed: " + response.getStatusCode());
        }

        return response.getBody();
    }

    public Map<String, Object> persistFileMetadata(String fileName, String fileUrl, String deviceId, long fileSize, String sha256, String hashWithSize) {
        if (!supabaseEnabled) {
            logger.debug("Supabase disabled, skipping metadata persistence for local file: {}", fileName);
            return null; // Return null to indicate metadata not persisted to cloud
        }

        String url = supabaseUrl + "/rest/v1/file_metadata";

        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Prefer", "return=representation");

        Map<String, Object> payload = new HashMap<>();
        payload.put("file_name", fileName);
        payload.put("file_url", fileUrl);
        payload.put("device_id", deviceId);
        payload.put("upload_time", Instant.now().toString());
        payload.put("file_size", fileSize);
        payload.put("hash", sha256);
        payload.put("hash_size", hashWithSize);

        HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(List.of(payload), headers);
        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.POST, request, List.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isEmpty()) {
                throw new IllegalStateException("Supabase metadata write failed: " + response.getStatusCode());
            }
            return (Map<String, Object>) response.getBody().get(0);
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            logger.warn("Supabase metadata write failed url={} status={} body={}", url, ex.getStatusCode(), body);
            // Handle missing table or schema issues gracefully - file upload succeeded, just metadata is unavailable
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND && body != null && body.contains("file_metadata")) {
                return null;
            }
            // Also handle schema/permission errors gracefully (PGRST204 = column not found, PGRST109 = permission denied, etc.)
            if (body != null && (body.contains("PGRST") || body.contains("column"))) {
                logger.warn("Supabase metadata table has schema or permission issues, file upload to storage succeeded though");
                return null;
            }
            throw new IllegalStateException("Supabase metadata write failed: " + ex.getStatusCode() + " - " + body, ex);
        }
    }

    public List<Map<String, Object>> fetchProcessLogs(String deviceId, int limit) {
        if (!supabaseEnabled) {
            logger.debug("Supabase disabled, returning empty list for process logs");
            return new ArrayList<>(); // Return empty list when Supabase is disabled
        }

        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(supabaseUrl + "/rest/v1/process_logs")
                .queryParam("select", "*")
                .queryParam("order", "timestamp.desc")
                .queryParam("limit", limit);

        if (deviceId != null && !deviceId.isBlank()) {
            uri.queryParam("device_id", "eq." + deviceId);
        }

        HttpHeaders headers = authHeaders();
        headers.set("Accept", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<List> response = restTemplate.exchange(uri.toUriString(), HttpMethod.GET, request, List.class);
            return (List<Map<String, Object>>) response.getBody();
        } catch (Exception e) {
            logger.error("Failed to fetch process logs from Supabase: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> fetchAppDetections(String deviceId, int limit) {
        if (!supabaseEnabled) {
            logger.debug("Supabase disabled, returning empty list for app detections");
            return new ArrayList<>(); // Return empty list when Supabase is disabled
        }

        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(supabaseUrl + "/rest/v1/app_detections")
                .queryParam("select", "*")
                .queryParam("order", "timestamp.desc")
                .queryParam("limit", limit);

        if (deviceId != null && !deviceId.isBlank()) {
            uri.queryParam("device_id", "eq." + deviceId);
        }

        HttpHeaders headers = authHeaders();
        headers.set("Accept", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<List> response = restTemplate.exchange(uri.toUriString(), HttpMethod.GET, request, List.class);
            return (List<Map<String, Object>>) response.getBody();
        } catch (Exception e) {
            logger.error("Failed to fetch app detections from Supabase: {}", e.getMessage());
            return List.of();
        }
    }

    public void logProcessesToSupabase(String deviceId, List<Map<String, Object>> processes) {
        if (!supabaseEnabled) {
            logger.debug("Supabase disabled, skipping process logging");
            return; // Skip logging when Supabase is disabled
        }

        String url = supabaseUrl + "/rest/v1/process_logs";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        List<Map<String, Object>> payload = processes.stream().map(p -> {
            Map<String, Object> log = new HashMap<>();
            log.put("device_id", deviceId);
            log.put("process_name", p.get("name"));
            log.put("cpu_usage", p.get("cpu"));
            log.put("memory_usage", p.get("memory"));
            log.put("timestamp", p.get("timestamp"));
            return log;
        }).toList();

        HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(payload, headers);
        try {
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        } catch (Exception e) {
            logger.warn("Failed to log processes to Supabase: {}", e.getMessage());
        }
    }

    public void logAppActivityToSupabase(String deviceId, String appName, String status, String timestamp) {
        if (!supabaseEnabled) return;

        String url = supabaseUrl + "/rest/v1/app_detections";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> log = new HashMap<>();
        log.put("device_id", deviceId);
        log.put("app_name", appName);
        log.put("status", status);
        log.put("timestamp", timestamp != null ? timestamp : Instant.now().toString());

        HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(List.of(log), headers);
        try {
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            logger.info("✅ App activity synced to Supabase: {} - {}", appName, status);
        } catch (Exception e) {
            logger.warn("⚠️ Failed to log app activity to Supabase: {}", e.getMessage());
        }
    }
}
