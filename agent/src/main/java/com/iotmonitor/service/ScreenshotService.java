package com.iotmonitor.service;

import com.iotmonitor.config.AgentConfig;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Captures a full-screen screenshot using AWT {@link Robot} and uploads the
 * PNG bytes to {@code /api/screenshots/upload} at the configured interval.
 *
 * Works on any platform that has a display (Windows, Linux with X11/Wayland
 * via java.awt.headless=false, macOS).
 *
 * On headless servers set {@code agent.screenshot-enabled=false} to disable.
 */
@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    private final AgentConfig config;
    private final AuthService authService;
    private final RestTemplate rest = new RestTemplate();

    private Robot robot;

    public ScreenshotService(AgentConfig config, AuthService authService) {
        this.config      = config;
        this.authService = authService;
        try {
            this.robot = new Robot();
            log.info("Screenshot capture initialised (AWT Robot)");
        } catch (AWTException | UnsupportedOperationException e) {
            log.warn("AWT Robot not available – screenshots disabled: {}", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Scheduled task
    // -----------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${agent.screenshot-interval-ms:10000}")
    public void captureAndUpload() {
        if (robot == null) {
            log.warn("Screenshot capture skipped: AWT Robot unavailable");
            return;
        }

        try {
            log.info("Starting scheduled screenshot capture");
            byte[] png = captureScreen();
            if (png == null || png.length == 0) {
                log.warn("Screenshot capture returned empty data");
                return;
            }

            log.info("Screenshot captured, size: {} bytes", png.length);
            uploadScreenshot(png);
            log.info("Screenshot uploaded successfully");

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Screenshot upload unauthorized, refreshing token");
            authService.invalidateToken();
        } catch (Exception ex) {
            log.warn("Screenshot upload failed: {}", ex.getMessage());
        }
    }

    public boolean captureAndUploadNow() {
        if (robot == null) {
            log.warn("Cannot capture screenshot: AWT Robot unavailable");
            return false;
        }

        try {
            byte[] png = captureScreen();
            if (png == null || png.length == 0) {
                log.warn("No screenshot data captured");
                return false;
            }

            uploadScreenshot(png);
            log.info("Immediate screenshot uploaded ({} bytes)", png.length);
            return true;

        } catch (HttpClientErrorException.Unauthorized e) {
            authService.invalidateToken();
            return false;
        } catch (Exception ex) {
            log.warn("Immediate screenshot failed: {}", ex.getMessage());
            return false;
        }
    }

    private void uploadScreenshot(byte[] png) {
        log.info("Uploading screenshot to backend");
        HttpHeaders headers = authService.authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "screenshot.png";
            }
        });
        body.add("deviceId", config.getDeviceId());

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        String uploadUrl = config.getBackendUrl() + "/api/screenshots/upload";
        log.info("Upload URL: {}", uploadUrl);

        try {
            ResponseEntity<Void> response = rest.postForEntity(uploadUrl, request, Void.class);
            log.info("Screenshot upload response: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Screenshot upload failed with exception: {}", e.getMessage());
            throw e;
        }
    }

    // -----------------------------------------------------------------------
    // Screen capture
    // -----------------------------------------------------------------------

    private byte[] captureScreen() {
        try {
            // Capture the default screen device
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenshot = robot.createScreenCapture(screenRect);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenshot, "png", baos);
            return baos.toByteArray();

        } catch (Exception ex) {
            log.warn("Screen capture failed: {}", ex.getMessage());
            return null;
        }
    }
}