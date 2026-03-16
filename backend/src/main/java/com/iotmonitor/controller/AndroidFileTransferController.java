package com.iotmonitor.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/android/files")
public class AndroidFileTransferController {

    private final List<Path> allowedDirectories;

    public AndroidFileTransferController() {
        String userHome = System.getProperty("user.home");
        allowedDirectories = List.of(
                Paths.get(userHome, "Documents").normalize().toAbsolutePath(),
                Paths.get(userHome, "Downloads").normalize().toAbsolutePath(),
                Paths.get(userHome, "Pictures").normalize().toAbsolutePath()
        );
    }

    private Path validateAndGetPath(String requestedPath) throws IOException {
        Path path = Paths.get(requestedPath).normalize().toAbsolutePath();
        boolean isAllowed = false;
        for (Path allowed : allowedDirectories) {
            if (path.startsWith(allowed)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            throw new AccessDeniedException("Access to this path is restricted.");
        }
        return path;
    }

    @GetMapping("/list")
    public ResponseEntity<?> listDirectory(@RequestParam(value = "path", required = false) String directoryPath) {
        try {
            Path dir;
            if (directoryPath == null || directoryPath.trim().isEmpty() || directoryPath.equals("/")) {
                // If root requested, return allowed directories
                List<Map<String, Object>> roots = new ArrayList<>();
                for (Path allowed : allowedDirectories) {
                    File file = allowed.toFile();
                    roots.add(Map.of(
                            "name", file.getName(),
                            "path", file.getAbsolutePath(),
                            "isDirectory", true,
                            "size", 0,
                            "lastModified", file.lastModified()
                    ));
                }
                return ResponseEntity.ok(roots);
            } else {
                dir = validateAndGetPath(directoryPath);
            }

            if (!Files.isDirectory(dir)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Path is not a directory"));
            }

            List<Map<String, Object>> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    File file = entry.toFile();
                    files.add(Map.of(
                            "name", file.getName(),
                            "path", file.getAbsolutePath(),
                            "isDirectory", file.isDirectory(),
                            "size", file.length(),
                            "lastModified", file.lastModified()
                    ));
                }
            }
            return ResponseEntity.ok(files);

        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @RequestParam("path") String filePath,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        try {
            Path file = validateAndGetPath(filePath);
            if (!Files.isRegularFile(file)) {
                return ResponseEntity.badRequest().build();
            }

            long fileLength = Files.size(file);
            long start = 0;
            long end = fileLength - 1;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.substring(6).split("-");
                try {
                    start = Long.parseLong(ranges[0]);
                    if (ranges.length > 1 && !ranges[1].isEmpty()) {
                        end = Long.parseLong(ranges[1]);
                    }
                } catch (NumberFormatException ignored) {}
            }
            
            if (start > end || start >= fileLength) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                        .build();
            }
            
            end = Math.min(end, fileLength - 1);
            long contentLength = end - start + 1;

            final long finalStart = start;

            StreamingResponseBody responseBody = outputStream -> {
                try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                    raf.seek(finalStart);
                    byte[] buffer = new byte[8192];
                    long bytesToRead = contentLength;
                    int read;
                    while (bytesToRead > 0 && (read = raf.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead))) != -1) {
                        outputStream.write(buffer, 0, read);
                        bytesToRead -= read;
                    }
                }
            };

            HttpHeaders headers = new HttpHeaders();
            String contentType = Files.probeContentType(file);
            headers.add(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream");
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName().toString() + "\"");
            headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");

            if (rangeHeader != null) {
                headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(headers)
                        .contentLength(contentLength)
                        .body(responseBody);
            } else {
                return ResponseEntity.ok()
                        .headers(headers)
                        .contentLength(fileLength)
                        .body(responseBody);
            }

        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("path") String directoryPath,
            @RequestParam("filename") String filename,
            @RequestHeader(value = HttpHeaders.CONTENT_RANGE, required = false) String contentRange,
            InputStream dataStream) {
        try {
            Path dir = validateAndGetPath(directoryPath);
            if (!Files.isDirectory(dir)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Destination path is not a directory"));
            }

            Path file = dir.resolve(filename).normalize().toAbsolutePath();
            if (!file.startsWith(dir)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid filename"));
            }

            boolean append = false;
            
            // "bytes start-end/total"
            if (contentRange != null && contentRange.startsWith("bytes ")) {
                String range = contentRange.substring(6).split("/")[0];
                String[] parts = range.split("-");
                if (parts.length > 0) {
                    long start = Long.parseLong(parts[0]);
                    if (start > 0) {
                        append = true;
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file.toFile(), append)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = dataStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            return ResponseEntity.ok(Map.of("message", "Chunk uploaded successfully", "file", file.toString()));

        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
