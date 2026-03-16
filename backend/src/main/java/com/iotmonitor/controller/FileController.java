package com.iotmonitor.controller;

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
import java.util.List;
import java.util.stream.Collectors;

/**
 * File service used by the UI.  All methods are public and the security layer
 * already permits access to "/files/**", so the controller is responsible
 * for validating paths and handling I/O problems gracefully.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final Path root = Paths.get("C:/shared-files").toAbsolutePath().normalize();

    private Path resolveSafe(String name) throws IOException {
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("Attempt to escape root directory");
        }
        return file;
    }

    // list files
    @GetMapping("/list")
    public ResponseEntity<?> listFiles() {
        try {
            List<String> names = Files.list(root)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
            return ResponseEntity.ok(names);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to list files: " + e.getMessage());
        }
    }

    // open file inline
    @GetMapping("/view/{name}")
    public ResponseEntity<?> viewFile(@PathVariable String name) {
        try {
            Path file = resolveSafe(name);
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

    // download file
    @GetMapping("/download/{name}")
    public ResponseEntity<?> downloadFile(@PathVariable String name) {
        try {
            Path file = resolveSafe(name);
            Resource resource = new UrlResource(file.toUri());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found or unreadable");
        }
    }

    // view file as simple HTML table
    @GetMapping("/read/{name}")
    public ResponseEntity<?> readFile(@PathVariable String name) {
        try {
            Path file = resolveSafe(name);

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
                    String html = "<html><body><img src=\"/files/view/" + name + "\" style=\"max-width:100%;height:auto;\"></body></html>";
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
            logger.warn("error reading file {}: {}", name, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found or unreadable: " + e.getMessage());
        }
    }
}


