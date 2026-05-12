package com.iotmonitor.agent;

import com.iotmonitor.network.ApiClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileSyncService {

    private static final long MAX_FILE_SIZE_BYTES = Long.parseLong(
            System.getenv().getOrDefault("MAX_SYNC_FILE_SIZE_BYTES", "104857600"));

    private final ApiClient apiClient;
    private final String watchDirectory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FileSyncService(ApiClient apiClient, String watchDirectory) {
        this.apiClient = apiClient;
        this.watchDirectory = watchDirectory;

        File dir = new File(watchDirectory);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("[FileSyncService] Failed to create watch directory: " + watchDirectory);
        }
        System.out.println("[FileSyncService] Initialized watching directory: " + watchDirectory);
    }

    public void startSync() {
        executor.submit(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Path path = Paths.get(watchDirectory);
                registerAllDirs(path, watchService);

                System.out.println("[FileSyncService] Watching for changes: " + watchDirectory);
                uploadExistingFiles(path);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            System.err.println("[FileSyncService] WatchService overflow detected; running full scan");
                            uploadExistingFiles(path);
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path watchedDir = (Path) key.watchable();
                        Path child = watchedDir.resolve(ev.context());
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                            registerAllDirs(child, watchService);
                        }
                        if (Files.isRegularFile(child)) {
                            uploadDetectedFile(child, "watch-" + kind.name());
                        } else {
                            System.out.println("[FileSyncService] Skipped non-file watch event: " + child.toAbsolutePath());
                        }
                    }

                    if (!key.reset()) {
                        System.err.println("[FileSyncService] WatchKey invalidated for: " + path.toAbsolutePath());
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ClosedWatchServiceException e) {
                System.out.println("[FileSyncService] WatchService closed");
            } catch (Exception e) {
                System.err.println("[FileSyncService] WatchService error: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        });
    }

    public void syncNow() {
        Path path = Paths.get(watchDirectory);
        try {
            uploadExistingFiles(path);
            System.out.println("[FileSyncService] Manual sync triggered");
        } catch (IOException e) {
            System.err.println("[FileSyncService] Manual sync error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private void uploadExistingFiles(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                uploadDetectedFile(file, "scan");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("[FileSyncService] Skipped unreadable file during scan: " + file.toAbsolutePath()
                        + " - " + exc.getMessage());
                exc.printStackTrace(System.err);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerAllDirs(Path root, WatchService watchService) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                System.out.println("[FileSyncService] Registered watch directory: " + dir.toAbsolutePath());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void uploadDetectedFile(Path file, String source) {
        System.out.println("[FileSyncService] Detected file (" + source + "): " + file.toAbsolutePath());
        try {
            if (!Files.isRegularFile(file)) {
                System.out.println("[FileSyncService] Skipped non-regular file: " + file.toAbsolutePath());
                return;
            }

            waitForStableFile(file);
            long size = Files.size(file);
            if (size > MAX_FILE_SIZE_BYTES) {
                System.err.println("[FileSyncService] Skipped oversized file: " + file.toAbsolutePath()
                        + " bytes=" + size + " maxBytes=" + MAX_FILE_SIZE_BYTES);
                return;
            }

            System.out.println("[FileSyncService] Upload start: " + file.toAbsolutePath() + " bytes=" + size);
            boolean uploaded = apiClient.uploadFileToBackend(file.toFile());
            if (uploaded) {
                System.out.println("[FileSyncService] Upload success: " + file.toAbsolutePath());
            } else {
                System.err.println("[FileSyncService] Upload failure: " + file.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("[FileSyncService] Upload exception for " + file.toAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private void waitForStableFile(Path file) throws IOException, InterruptedException {
        long previousSize = -1L;
        long previousModified = -1L;
        for (int attempt = 0; attempt < 10; attempt++) {
            long currentSize = Files.size(file);
            long currentModified = Files.getLastModifiedTime(file).toMillis();
            if (currentSize == previousSize && currentModified == previousModified) {
                return;
            }
            previousSize = currentSize;
            previousModified = currentModified;
            Thread.sleep(500);
        }
    }

    public void stopSync() {
        executor.shutdownNow();
    }
}
