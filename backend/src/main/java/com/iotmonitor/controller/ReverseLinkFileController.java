package com.iotmonitor.controller;

import com.iotmonitor.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reverselink/fs")
public class ReverseLinkFileController {

    private final List<Path> allowedDirectories;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    public ReverseLinkFileController() {
        String userHome = System.getProperty("user.home");
        allowedDirectories = List.of(
                Paths.get(userHome, "Documents").normalize().toAbsolutePath(),
                Paths.get(userHome, "Downloads").normalize().toAbsolutePath(),
                Paths.get(userHome, "Pictures").normalize().toAbsolutePath()
        );
    }

    /**
     * Accept either:
     *  1. Email-based JWT auth (Authorization: Bearer <token>)
     *  2. Legacy QR/ECDH pairing (derivedAesKey not null)
     */
    private void verifyAuthentication(String authHeader) throws AccessDeniedException {
        // Try JWT first
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                return; // valid JWT — allow access
            }
        }
        // Fall back to legacy AES key pairing
        if (ReverseLinkAuthController.derivedAesKey != null) {
            return;
        }
        throw new AccessDeniedException("Unauthorized - Pair via email or QR code first.");
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
    public ResponseEntity<?> listDirectory(
            @RequestParam(value = "path", required = false) String directoryPath,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        try {
            verifyAuthentication(authHeader);
            Path dir;
            if (directoryPath == null || directoryPath.trim().isEmpty() || directoryPath.equals("/")) {
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

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(
            @RequestParam("path") String filePath,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        try {
            verifyAuthentication(authHeader);
            Path file = validateAndGetPath(filePath);

            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) return ResponseEntity.badRequest().build();

            // Resize maintaining aspect ratio
            int targetWidth = 256;
            int targetHeight = (img.getHeight() * targetWidth) / img.getWidth();
            Image scaledImage = img.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            BufferedImage outImg = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = outImg.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(outImg, "jpg", baos);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, "image/jpeg");
            return ResponseEntity.ok().headers(headers).body(baos.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Standard Download and Upload endpoints utilizing similar logic as `AndroidFileTransferController`
    // can be mapped below using `StreamingResponseBody`.
    
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @RequestParam("path") String filePath,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        try {
            verifyAuthentication(authHeader);
            Path file = validateAndGetPath(filePath);
            if (!Files.isRegularFile(file)) return ResponseEntity.badRequest().build();
            long fileLength = Files.size(file);
            long start = 0;
            long end = fileLength - 1;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.substring(6).split("-");
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) end = Long.parseLong(ranges[1]);
            }
            
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
            headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName().toString() + "\"");
            headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");

            if (rangeHeader != null) {
                headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).contentLength(contentLength).body(responseBody);
            }
            return ResponseEntity.ok().headers(headers).contentLength(fileLength).body(responseBody);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
