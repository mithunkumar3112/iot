package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class ScreenshotService {

    private final ApiClient apiClient;

    public ScreenshotService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public static byte[] capture() {
        try {
            Robot robot = new Robot();
            BufferedImage img = robot.createScreenCapture(
                    new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            System.err.println("❌ Screenshot capture failed: " + e.getMessage());
            return null;
        }
    }

    public void captureAndSend() {
        try {
            System.out.println("📸 Capturing screenshot...");
            byte[] imageBytes = capture();
            if (imageBytes != null && imageBytes.length > 0) {
                System.out.println("📸 Screenshot captured, size: " + imageBytes.length + " bytes");
                apiClient.sendScreenshot(imageBytes);
                System.out.println("✅ Screenshot sent to backend");
            } else {
                System.err.println("❌ Screenshot capture returned null or empty");
            }
        } catch (Exception e) {
            System.err.println("❌ Error in captureAndSend: " + e.getMessage());
        }
    }
}
