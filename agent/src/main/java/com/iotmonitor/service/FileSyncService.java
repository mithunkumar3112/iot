package com.iotmonitor.service;

import com.iotmonitor.config.AgentConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * File synchronisation service for the IoT Monitor laptop agent.
 *
 * Responsibilities
 * ----------------
 * 1. On startup, registers a {@link WatchService} on each configured watched
 *    directory and begins emitting CREATE / MODIFY / MOVE events in a
 *    background thread.
 * 2. Uploads new or changed files to {@code /upload} using
 *    multipart/form-data.
 * 3. Skips files that have already been uploaded (tracked by SHA-256 hash of
 *    the first 64 KB + file size).
 * 4. Runs a full-directory scan on a configurable interval as a safety net to
 *    catch any files that might have been missed by the WatchService (common
 *    during the first startup).
 *
 * All upload tasks are executed on a small fixed thread pool to avoid blocking
 * the WatchService loop and to support parallel uploads.
 */
@Service
public class FileSyncService {

    private static final Logger log = LoggerFactory.getLogger(FileSyncService.class);

    private final AgentConfig config;
    private final AuthService authService;
    private final RestTemplate rest = new RestTemplate();

    /** SHA-256 hashes of already-uploaded file versions */
    private final Set<String> uploadedHashes = ConcurrentHashMap.newKeySet();

    /** Upload executor – 3 threads for parallel uploads */
    private final ExecutorService uploadPool = Executors.newFixedThreadPool(3);

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = true;

    public FileSyncService(AgentConfig config, AuthService authService) {
        this.config      = config;
        this.authService = authService;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @PostConstruct
    public void startWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            List<String> dirs = config.getWatchedDirs();
            int registered = 0;

            for (String dir : dirs) {
                Path path = Paths.get(dir);
                if (Files.isDirectory(path)) {
                    path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                    log.info("Watching: {}", path);
                    registered++;
                } else {
                    log.warn("Watched directory not found (skipping): {}", dir);
                }
            }

            if (registered == 0) {
                log.warn("No valid watched directories found – real-time sync disabled");
                return;
            }

            watchThread = new Thread(this::watchLoop, "file-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
            log.info("File watcher started on {} director{}", registered, registered == 1 ? "y" : "ies");

        } catch (IOException ex) {
            log.error("Failed to start WatchService: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void stopWatcher() {
        running = false;
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) {}
        }
        uploadPool.shutdownNow();
        log.info("File watcher stopped");
    }

    // -----------------------------------------------------------------------
    // WatchService loop
    // -----------------------------------------------------------------------

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();   // blocks until an event arrives
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            Path watchedDir = (Path) key.watchable();

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                Path filename = ((WatchEvent<Path>) event).context();
                Path fullPath = watchedDir.resolve(filename);

                if (Files.isRegularFile(fullPath)) {
                    // Short delay so the OS finishes writing the file
                    uploadPool.submit(() -> {
                        sleep(500);
                        uploadFile(fullPath);
                    });
                }
            }

            if (!key.reset()) {
                log.warn("WatchKey invalidated for: {}", watchedDir);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Periodic full-scan (safety net)
    // -----------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${agent.sync-interval-ms:30000}")
    public void fullScanSync() {
        log.debug("Full-scan sync starting…");
        for (String dir : config.getWatchedDirs()) {
            Path root = Paths.get(dir);
            if (!Files.isDirectory(root)) continue;
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        uploadPool.submit(() -> uploadFile(file));
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ex) {
                log.warn("Full-scan error for {}: {}", dir, ex.getMessage());
            }
        }
        log.debug("Full-scan sync queued");
    }

    // -----------------------------------------------------------------------
    // Upload a single file
    // -----------------------------------------------------------------------

    void uploadFile(Path file) {
        if (!Files.isRegularFile(file)) return;

        // Size check
        long size;
        try { size = Files.size(file); }
        catch (IOException e) { return; }

        if (size > config.getMaxFileSizeBytes()) {
            log.warn("SYNC FILE SKIPPED oversized: localPath={} bytes={} maxBytes={}",
                    file.toAbsolutePath(), size, config.getMaxFileSizeBytes());
            return;
        }

        // Hash check – skip if we already uploaded this version
        String hash = quickHash(file, size);
        if (uploadedHashes.contains(hash)) {
            log.debug("SYNC FILE SKIPPED already uploaded: localPath={} bytes={}", file.toAbsolutePath(), size);
            return;
        }

        String storagePath = deriveStoragePath(file);
        String filename = file.getFileName().toString();
        MediaType mimeType = detectMimeType(file);
        log.info("SYNC FILE DETECTED: file={} filename={} storagePath={} bytes={} mimeType={}",
                file.toAbsolutePath(), filename, storagePath, size, mimeType);

        try {
            byte[] bytes = Files.readAllBytes(file);

            // Build multipart body
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentDisposition(ContentDisposition.formData()
                    .name("file")
                    .filename(filename)
                    .build());
            fileHeaders.setContentType(mimeType);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(new NamedByteArrayResource(bytes, filename), fileHeaders));
            body.add("storagePath", storagePath);
            body.add("deviceId", config.getDeviceId());
            body.add("localPath", file.toAbsolutePath().toString());

            HttpHeaders headers = authService.authHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            log.info("SYNC UPLOAD START: localPath={} storagePath={} bytes={} mimeType={}",
                    file.toAbsolutePath(), storagePath, size, mimeType);
            ResponseEntity<String> response = rest.postForEntity(
                config.getBackendUrl() + "/upload",
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                uploadedHashes.add(hash);
                log.info("Detected file for sync: localPath={} storagePath={} deviceId={} bytes={} response={}",
                        file.toAbsolutePath(), storagePath, config.getDeviceId(), size, response.getBody());
            } else {
                log.warn("Sync upload rejected ({}): localPath={} storagePath={}", response.getStatusCode(), file.toAbsolutePath(), storagePath);
            }

        } catch (HttpClientErrorException.Unauthorized e) {
            authService.invalidateToken();
            log.warn("Token expired – will re-authenticate on next attempt");
        } catch (IOException ex) {
            log.error("SYNC UPLOAD FAILURE read error: localPath={}", file.toAbsolutePath(), ex);
        } catch (Exception ex) {
            log.error("SYNC UPLOAD FAILURE: localPath={} storagePath={}", file.toAbsolutePath(), storagePath, ex);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Derives the storage path for an uploaded file relative to the watched
     * directory root. Files directly inside a watched directory are uploaded
     * into files/{deviceId}/{filename}.
     */
    private String deriveStoragePath(Path file) {
        Path absoluteFile = file.toAbsolutePath().normalize();
        for (String dir : config.getWatchedDirs()) {
            try {
                Path root = Paths.get(dir).toAbsolutePath().normalize();
                if (absoluteFile.startsWith(root)) {
                    Path relative = root.relativize(absoluteFile);
                    String normalized = relative.toString().replace("\\", "/");
                    normalized = normalized.replaceAll("^/+", "");
                    normalized = normalized.replaceAll("\\.\\.", "_");

                    return normalizePathSegment(config.getDeviceId()) + "/" + normalized;
                }
            } catch (Exception ignored) {
                // ignore invalid watched directory entries
            }
        }
        // Fallback to device root if no watched directory matches
        return normalizePathSegment(config.getDeviceId()) + "/" + normalizePathSegment(file.getFileName().toString());
    }

    private String normalizePathSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown-device";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._/-]", "_");
    }

    private MediaType detectMimeType(Path file) {
        try {
            String probed = Files.probeContentType(file);
            if (probed != null && !probed.isBlank()) {
                return MediaType.parseMediaType(probed);
            }
        } catch (Exception ex) {
            log.warn("MIME probe failed for {}: {}", file.toAbsolutePath(), ex.getMessage());
        }

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        if (name.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (name.endsWith(".ppt")) return MediaType.parseMediaType("application/vnd.ms-powerpoint");
        if (name.endsWith(".pptx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        if (name.endsWith(".doc")) return MediaType.parseMediaType("application/msword");
        if (name.endsWith(".docx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Quick SHA-256: hashes the first 64 KB of the file concatenated with the
     * file size. Fast enough even for large files yet unique for all practical
     * purposes.
     */
    private String quickHash(Path file, long size) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Long.toString(size).getBytes());
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buf = new byte[65536];
                int read = is.read(buf);
                if (read > 0) md.update(buf, 0, read);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return file.toString() + "@" + size;
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // -----------------------------------------------------------------------
    // Inner class: named ByteArrayResource for multipart upload
    // -----------------------------------------------------------------------

    /**
     * Spring's {@link ByteArrayResource} does not carry a filename, which
     * causes the server to receive "blob" instead of the real name.
     * This subclass overrides {@link #getFilename()} to fix that.
     */
    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
