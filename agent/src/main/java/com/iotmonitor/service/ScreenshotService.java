package com.iotmonitor.service;

import com.iotmonitor.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Captures a full-screen screenshot using AWT {@link Robot} and POSTs the
 * raw PNG bytes to {@code /screenshot} at the configured interval.
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
        if (robot == null) return;

        try {
            byte[] png = captureScreen();
            if (png == null || png.length == 0) return;

            HttpHeaders headers = authService.authHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            HttpEntity<byte[]> request = new HttpEntity<>(png, headers);
            rest.postForEntity(config.getBackendUrl() + "/screenshot", request, Void.class);
            log.debug("Screenshot uploaded ({} bytes)", png.length);

        } catch (HttpClientErrorException.Unauthorized e) {
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

            HttpHeaders headers = authService.authHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            HttpEntity<byte[]> request = new HttpEntity<>(png, headers);
            ResponseEntity<Void> response = rest.postForEntity(config.getBackendUrl() + "/screenshot", request, Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Immediate screenshot uploaded ({} bytes)", png.length);
                return true;
            }

            log.warn("Immediate screenshot failed: HTTP {}", response.getStatusCode().value());
        } catch (HttpClientErrorException.Unauthorized e) {
            authService.invalidateToken();
        } catch (Exception ex) {
            log.warn("Immediate screenshot upload failed: {}", ex.getMessage());
        }
        return false;
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