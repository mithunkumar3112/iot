package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;

import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class ScreenshotService {

    private final ApiClient apiClient;

    public ScreenshotService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public static byte[] capture() {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("[screenshot] Capture skipped: Java is running in headless mode");
            return null;
        }

        try {
            Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            Robot robot = new Robot();
            BufferedImage img = robot.createScreenCapture(screen);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            System.err.println("[screenshot] Capture failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    public void captureAndSend() {
        try {
            System.out.println("[screenshot] Capturing desktop screenshot");
            byte[] imageBytes = capture();
            if (imageBytes == null || imageBytes.length == 0) {
                System.err.println("[screenshot] Capture returned no bytes");
                return;
            }

            System.out.println("[screenshot] Captured " + imageBytes.length + " bytes; uploading to backend");
            apiClient.sendScreenshot(imageBytes);
        } catch (Exception e) {
            System.err.println("[screenshot] captureAndSend failed: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
