package com.iotmonitor.agent;

import com.iotmonitor.network.ApiClient;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileSyncService {

    private final ApiClient apiClient;
    private final String watchDirectory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FileSyncService(ApiClient apiClient, String watchDirectory) {
        this.apiClient = apiClient;
        this.watchDirectory = watchDirectory;
        
        // Ensure directory exists
        File dir = new File(watchDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public void startSync() {
        executor.submit(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path path = Paths.get(watchDirectory);
                path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

                System.out.println("📂 Laptop Agent watching for changes: " + watchDirectory);

                // Initial sync of existing files
                uploadExistingFiles(path);

                WatchKey key;
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path fileName = ev.context();
                        Path child = path.resolve(fileName);

                        if (!Files.isDirectory(child)) {
                            System.out.println("🔄 Change detected: " + fileName);
                            apiClient.uploadFileToBackend(child.toFile());
                        }
                    }
                    boolean valid = key.reset();
                    if (!valid) break;
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("❌ FileSyncService error: " + e.getMessage());
            }
        });
    }

    public void syncNow() {
        Path path = Paths.get(watchDirectory);
        try {
            uploadExistingFiles(path);
            System.out.println("🔁 Manual sync triggered");
        } catch (IOException e) {
            System.err.println("❌ Manual sync error: " + e.getMessage());
        }
    }

    private void uploadExistingFiles(Path path) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    System.out.println("📤 Initial sync: " + entry.getFileName());
                    apiClient.uploadFileToBackend(entry.toFile());
                }
            }
        }
    }

    public void stopSync() {
        executor.shutdownNow();
    }
}
