package com.iotmonitor.controller;

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

    private static final Path FILE = Paths.get("screenshots/latest.png");

    // Agent uploads screenshot here
    @PostMapping
    public void upload(@RequestBody byte[] image) throws IOException {

        Files.createDirectories(FILE.getParent());
        Files.write(FILE, image);

        System.out.println("Screenshot updated");
    }

    // Dashboard loads screenshot here
    @GetMapping
    public ResponseEntity<FileSystemResource> getScreenshot() {

        File file = FILE.toFile();

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
    }
}