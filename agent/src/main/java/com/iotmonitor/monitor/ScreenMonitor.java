package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class ScreenMonitor {

    private final ApiClient apiClient;
    private final Robot robot;

    public ScreenMonitor(ApiClient apiClient) throws AWTException {
        this.apiClient = apiClient;
        this.robot = new Robot();
    }

    public void captureAndSend() {
        try {
            apiClient.sendScreenshot(capturePng());
        } catch (Exception e) {
            System.err.println("Screen capture error: " + e.getMessage());
        }
    }

    public byte[] capturePng() throws Exception {
        Rectangle screenBounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage image = robot.createScreenCapture(screenBounds);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }
}
