package com.iotmonitor.monitor;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.CRC32;

public class ScreenshotActivityMonitor {

    private static final Duration ALERT_COOLDOWN = Duration.ofSeconds(20);
    private static final Duration RECENT_FILE_WINDOW = Duration.ofSeconds(12);
    private static final Set<String> SCREENSHOT_PROCESS_MARKERS = Set.of(
            "snippingtool", "screenclippinghost", "snipsketch", "snipandsketch",
            "snipping tool", "screen sketch", "sharex", "greenshot", "lightshot",
            "prntscr", "picpick", "snagit", "snagit32", "gyazo", "flameshot",
            "ksnip", "spectacle", "gnome-screenshot", "xfce4-screenshooter",
            "screencapture", "grab", "preview"
    );
    private static final Set<String> SCREENSHOT_FILE_MARKERS = Set.of(
            "screenshot", "screen shot", "screenclip", "snip", "capture"
    );

    private final SecurityEventReporter reporter;
    private final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<Path, Long> knownScreenshotFiles = new ConcurrentHashMap<>();
    private final Set<String> runningScreenshotProcesses = ConcurrentHashMap.newKeySet();
    private String lastClipboardSignature;
    private String lastActiveScreenshotTool;
    private boolean initializedFiles;

    public ScreenshotActivityMonitor(SecurityEventReporter reporter) {
        this.reporter = reporter;
    }

    public void scanAndReport() {
        try {
            detectActiveScreenshotTool();
            detectRunningScreenshotTools();
            detectClipboardScreenshot();
            detectNewScreenshotFiles();
        } catch (Exception e) {
            System.err.println("Screenshot activity monitor error: " + e.getMessage());
        }
    }

    private void detectActiveScreenshotTool() {
        String processName = detectActiveProcessName();
        if (isScreenshotTool(processName)) {
            String normalized = normalize(processName);
            if (!normalized.equals(lastActiveScreenshotTool)) {
                lastActiveScreenshotTool = normalized;
                report(processName, "active screenshot tool");
            }
        } else {
            lastActiveScreenshotTool = "";
        }
    }

    private void detectRunningScreenshotTools() {
        Set<String> currentScreenshotProcesses = new HashSet<>();
        ProcessHandle.allProcesses().forEach(handle -> {
            String command = handle.info().command().orElse("");
            String processName = fileName(command);
            if (isScreenshotTool(processName)) {
                String key = handle.pid() + ":" + normalize(processName);
                currentScreenshotProcesses.add(key);
                if (runningScreenshotProcesses.add(key)) {
                    report(processName, "screenshot process");
                }
            }
        });
        runningScreenshotProcesses.retainAll(currentScreenshotProcesses);
    }

    private void detectClipboardScreenshot() {
        if (GraphicsEnvironment.isHeadless()) return;
        try {
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.imageFlavor)) return;
            Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
            String signature = clipboardImageSignature(image);
            if (signature != null && !signature.equals(lastClipboardSignature)) {
                lastClipboardSignature = signature;
                report("Print Screen / Clipboard", "clipboard image");
            }
        } catch (Exception ignored) {
            // Clipboard can be temporarily locked by Windows or another app.
        }
    }

    private void detectNewScreenshotFiles() {
        List<Path> directories = screenshotDirectories();
        Instant now = Instant.now();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) continue;
            try (Stream<Path> stream = Files.list(directory)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!isScreenshotFileName(name)) return;
                        long modified = Files.getLastModifiedTime(file).toMillis();
                        Long previous = knownScreenshotFiles.put(file, modified);
                        if (initializedFiles && (previous == null || previous.longValue() != modified)
                                && Duration.between(Instant.ofEpochMilli(modified), now).abs().compareTo(RECENT_FILE_WINDOW) <= 0) {
                            report(file.getFileName().toString(), "screenshot file saved");
                        }
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
        initializedFiles = true;
    }

    private String detectActiveProcessName() {
        if (!osName.contains("win")) return "";
        try {
            HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) return "";
            IntByReference pidRef = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            Optional<ProcessHandle> handle = ProcessHandle.of(pidRef.getValue());
            return handle.flatMap(h -> h.info().command()).map(this::fileName).orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    private String clipboardImageSignature(Image image) {
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width <= 0 || height <= 0) return null;
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        CRC32 crc = new CRC32();
        int stepX = Math.max(1, width / 32);
        int stepY = Math.max(1, height / 32);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int rgb = buffered.getRGB(x, y);
                crc.update(rgb);
                crc.update(rgb >>> 8);
                crc.update(rgb >>> 16);
                crc.update(rgb >>> 24);
            }
        }
        return width + "x" + height + ":" + crc.getValue();
    }

    private List<Path> screenshotDirectories() {
        String home = System.getProperty("user.home", "");
        if (home.isBlank()) return List.of();
        Path userHome = Paths.get(home);
        return List.of(
                userHome.resolve("Pictures").resolve("Screenshots"),
                userHome.resolve("Pictures"),
                userHome.resolve("Desktop"),
                userHome.resolve("Downloads")
        );
    }

    private boolean isScreenshotTool(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return false;
        return SCREENSHOT_PROCESS_MARKERS.stream().anyMatch(normalized::contains);
    }

    private boolean isScreenshotFileName(String name) {
        return (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp"))
                && SCREENSHOT_FILE_MARKERS.stream().anyMatch(name::contains);
    }

    private void report(String processName, String trigger) {
        String key = normalize(processName) + ":" + normalize(trigger);
        long now = System.currentTimeMillis();
        Long previous = cooldowns.put(key, now);
        if (previous != null && now - previous < ALERT_COOLDOWN.toMillis()) return;
        reporter.reportScreenshotActivity(cleanProcessName(processName), trigger);
    }

    private String fileName(String command) {
        if (command == null || command.isBlank()) return "";
        try {
            Path file = Paths.get(command).getFileName();
            return file == null ? command : file.toString();
        } catch (Exception e) {
            return command;
        }
    }

    private String cleanProcessName(String processName) {
        String value = processName == null || processName.isBlank() ? "Unknown screenshot activity" : processName.trim();
        return value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(".exe", "").trim();
    }
}
