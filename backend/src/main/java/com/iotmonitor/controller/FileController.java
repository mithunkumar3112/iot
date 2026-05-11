package com.iotmonitor.controller;

import com.iotmonitor.dto.FileInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * File service used by the UI. All methods are public and the security layer
 * already permits access to "/files/**", so the controller is responsible
 * for validating paths and handling I/O problems gracefully.
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Value("${app.file.paths:${java.io.tmpdir}/iot-monitor-uploads}")
    private String filePaths;

    private List<Path> getRootPaths() {
        return Arrays.stream(filePaths.split(","))
                .map(String::trim)
                .map(p -> Paths.get(p).toAbsolutePath().normalize())
                .collect(Collectors.toList());
    }

    private Path resolveSafe(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IOException("Invalid file path");
        }

        filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
        filePath = filePath.replace("\\", "/");

        for (Path root : getRootPaths()) {
            try {
                Path candidate;
                if (Paths.get(filePath).isAbsolute()) {
                    candidate = Paths.get(filePath).normalize();
                } else {
                    candidate = root.resolve(filePath).normalize();
                }

                if (candidate.startsWith(root) && Files.exists(candidate)) {
                    return candidate;
                }
            } catch (Exception e) {
                continue;
            }
        }

        throw new IOException("File not found in any configured path: " + filePath);
    }

    // List files from all configured paths with metadata
    @GetMapping("/list")
    public ResponseEntity<?> listFiles() {
        try {
            List<FileInfo> fileList = new ArrayList<>();
            
            for (Path root : getRootPaths()) {
                if (!Files.exists(root)) {
                    logger.warn("Configured path does not exist: {}", root);
                    continue;
                }
                
                try (var stream = Files.walk(root, 3)) {
                    stream.filter(p -> !p.equals(root)) // exclude root itself
                          .filter(p -> {
                              try {
                                  return Files.isReadable(p);
                              } catch (Exception e) {
                                  return false;
                              }
                          })
                          .forEach(p -> {
                              try {
                                  String relativePath = root.relativize(p).toString();
                                  long size = Files.isDirectory(p) ? 0 : Files.size(p);
                                  long lastModified = Files.getLastModifiedTime(p).toMillis();
                                  String fileName = p.getFileName().toString();
                                  
                                  fileList.add(new FileInfo(
                                      fileName,
                                      relativePath,
                                      size,
                                      Files.isDirectory(p),
                                      lastModified
                                  ));
                              } catch (IOException e) {
                                  logger.warn("Error getting file info for {}: {}", p, e.getMessage());
                              }
                          });
                } catch (IOException e) {
                    logger.warn("Error listing files from {}: {}", root, e.getMessage());
                }
            }
            
            logger.info("Found {} files", fileList.size());
            return ResponseEntity.ok(fileList);
        } catch (Exception e) {
            logger.error("Error listing files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to list files: " + e.getMessage());
        }
    }

    // Download file
    @GetMapping("/download/{filePath:.+}")
    public ResponseEntity<?> downloadFile(@PathVariable String filePath,
                                         @RequestParam(required = false) String token) {
        try {
            Path file = resolveSafe(java.net.URLDecoder.decode(filePath, "UTF-8"));
            
            if (!Files.isReadable(file)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("File is not readable");
            }
            
            Resource resource = new UrlResource(file.toUri());
            String fileName = file.getFileName().toString();
            
            logger.info("Downloading file: {}", file);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (IOException e) {
            logger.error("Download error", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found: " + e.getMessage());
        }
    }
    
    // View file inline (images, PDFs, etc.)
    @GetMapping("/view/{filePath:.+}")
    public ResponseEntity<?> viewFile(@PathVariable String filePath) {
        try {
            Path file = resolveSafe(java.net.URLDecoder.decode(filePath, "UTF-8"));
            Resource resource = new UrlResource(file.toUri());

            String contentType = Files.probeContentType(file);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found or unreadable");
        }
    }

    
    // Read file as HTML table (for text files)
    @GetMapping("/read/{filePath:.+}")
    public ResponseEntity<?> readFile(@PathVariable String filePath) {
        try {
            Path file = resolveSafe(java.net.URLDecoder.decode(filePath, "UTF-8"));

            String contentType = Files.probeContentType(file);
            if (contentType != null && !contentType.startsWith("text")) {
                // handle a few non-text types specially
                if ("application/pdf".equals(contentType)) {
                    // stream PDF directly so the browser can display without embedding
                    Resource resource = new UrlResource(file.toUri());
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "inline; filename=\"" + resource.getFilename() + "\"")
                            .body(resource);
                }
                if (contentType.startsWith("image/")) {
                    String html = "<html><body><img src=\"/files/view/" + filePath + "\" style=\"max-width:100%;height:auto;\"></body></html>";
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(html);
                }

                if (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                    contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation") ||
                    contentType.equals("application/msword") ||
                    contentType.equals("application/vnd.ms-powerpoint")) {
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                            .body("Cannot render Office file. Please download.");
                }

                logger.warn("attempt to read non-text file {} (type={})", file, contentType);
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body("Cannot render non-text file (type=" + contentType + "); please download instead.");
            }

            List<String> lines = Files.readAllLines(file);

            StringBuilder html = new StringBuilder("<html><body><table border='1'>");
            for (String line : lines) {
                html.append("<tr><td>").append(line).append("</td></tr>");
            }
            html.append("</table></body></html>");

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html.toString());
        } catch (IOException e) {
            logger.warn("error reading file {}: {}", filePath, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found or unreadable: " + e.getMessage());
        }
    }
}


