package com.iotmonitor.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/screenshot")
public class ScreenshotController {

    @Value("${app.file.storage-dir:C:/shared-files/screenshots}")
    private String screenshotDir;

    private Path getScreenshotFile() {
        return Paths.get(screenshotDir).resolve("latest.png");
    }

    // Agent uploads screenshot here
    @PostMapping
    public void upload(@RequestBody byte[] image) throws IOException {

        Path file = getScreenshotFile();
        Files.createDirectories(file.getParent());
        Files.write(file, image);

        System.out.println("Screenshot updated");
    }

    // Dashboard loads screenshot here
    @GetMapping
    public ResponseEntity<FileSystemResource> getScreenshot() {

        File file = getScreenshotFile().toFile();

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
    }
}