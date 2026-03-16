package com.iotmonitor.monitor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class ScreenshotService {

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
            return null;
        }
    }
}
