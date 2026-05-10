package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;

import java.util.List;
import java.util.Map;

/**
 * BatteryMonitor tracks battery level and charging status
 * Sends updates to backend every 5 seconds
 */
public class BatteryMonitor {

    private final ApiClient apiClient;
    private final HardwareAbstractionLayer hw;

    public BatteryMonitor(ApiClient apiClient) {
        this.apiClient = apiClient;
        SystemInfo systemInfo = new SystemInfo();
        this.hw = systemInfo.getHardware();
    }

    /**
     * Collect and send battery data
     */
    public void collectAndSendBatteryData() {
        try {
            List<PowerSource> powerSources = hw.getPowerSources();
            System.out.println("\n=== 🔋 BATTERY MONITOR CHECK ===");
            System.out.println("🔋 Power sources detected: " + (powerSources != null ? powerSources.size() : "null"));

            if (powerSources != null && !powerSources.isEmpty()) {
                PowerSource battery = powerSources.get(0);
                System.out.println("🔋 Power source 0 details: " + battery);

                double batteryPercent = battery.getRemainingCapacityPercent();
                System.out.println("🔋 Raw battery percent from OSHI: " + batteryPercent);

                if (batteryPercent <= 1.0) {
                    batteryPercent = batteryPercent * 100.0;
                }
                batteryPercent = Math.max(0.0, Math.min(100.0, batteryPercent));
                boolean isCharging = battery.isCharging();

                System.out.println("🔋 Processed battery: " + String.format("%.1f", batteryPercent) + "%");
                System.out.println("🔋 Charging: " + isCharging);

                // Send battery data to backend
                Map<String, Object> payload = Map.of(
                    "deviceId", apiClient.getDeviceId(),
                    "battery", batteryPercent,
                    "charging", isCharging
                );

                System.out.println("🔋 Device ID: " + apiClient.getDeviceId());
                System.out.println("🔋 Sending payload: " + payload);

                apiClient.sendBatteryData(payload);
                System.out.println("✅ Battery data sent successfully via OSHI");
            } else {
                System.out.println("⚠️ No battery detected by OSHI, trying OS commands...");

                // Fallback to OS commands
                double fallbackBattery = readBatteryViaCommands();
                if (fallbackBattery >= 0) {
                    boolean fallbackCharging = readChargingViaCommands();
                    Map<String, Object> payload = Map.of(
                        "deviceId", apiClient.getDeviceId(),
                        "battery", fallbackBattery,
                        "charging", fallbackCharging
                    );

                    System.out.println("🔋 Fallback battery: " + String.format("%.1f", fallbackBattery) + "%");
                    System.out.println("🔋 Fallback charging: " + fallbackCharging);
                    System.out.println("🔋 Sending fallback payload: " + payload);

                    apiClient.sendBatteryData(payload);
                    System.out.println("✅ Battery data sent successfully via OS commands");
                } else {
                    System.out.println("❌ No battery detected by any method");
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error collecting battery data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private double readBatteryViaCommands() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();

            if (os.contains("win")) {
                // Windows: WMIC
                Process p = Runtime.getRuntime()
                    .exec("WMIC Path Win32_Battery Get EstimatedChargeRemaining");
                String out = new String(p.getInputStream().readAllBytes()).trim();
                for (String line : out.split("\\r?\\n")) {
                    line = line.trim();
                    if (line.matches("\\d+")) {
                        return Double.parseDouble(line);
                    }
                }

            } else if (os.contains("linux")) {
                // Linux: /sys/class/power_supply/BAT0/capacity
                java.nio.file.Path batPath =
                    java.nio.file.Paths.get("/sys/class/power_supply/BAT0/capacity");
                if (java.nio.file.Files.exists(batPath)) {
                    return Double.parseDouble(
                        java.nio.file.Files.readString(batPath).trim());
                }

            } else if (os.contains("mac")) {
                // macOS: pmset -g batt
                Process p = Runtime.getRuntime().exec("pmset -g batt");
                String out = new String(p.getInputStream().readAllBytes());
                java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(\\d+)%").matcher(out);
                if (m.find()) {
                    return Double.parseDouble(m.group(1));
                }
            }
        } catch (Exception ignored) {
            System.err.println("Battery command fallback failed: " + ignored.getMessage());
        }
        return -1.0;
    }

    private boolean readChargingViaCommands() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();

            if (os.contains("win")) {
                // Windows: WMIC for charging status
                Process p = Runtime.getRuntime()
                    .exec("WMIC Path Win32_Battery Get BatteryStatus");
                String out = new String(p.getInputStream().readAllBytes()).trim();
                for (String line : out.split("\\r?\\n")) {
                    line = line.trim();
                    if (line.matches("\\d+")) {
                        int status = Integer.parseInt(line);
                        // BatteryStatus: 1=Discharging, 2=AC connected but not charging, 3=Fully charged, 4=Low, 5=Critical, 6=Charging, 7=Charging and high, 8=Charging and low, 9=Charging and critical, 10=Undefined, 11=Partially charged
                        return status == 6 || status == 7 || status == 8 || status == 9;
                    }
                }

            } else if (os.contains("linux")) {
                // Linux: /sys/class/power_supply/BAT0/status
                java.nio.file.Path statusPath =
                    java.nio.file.Paths.get("/sys/class/power_supply/BAT0/status");
                if (java.nio.file.Files.exists(statusPath)) {
                    String status = java.nio.file.Files.readString(statusPath).trim();
                    return "Charging".equalsIgnoreCase(status) || "Full".equalsIgnoreCase(status);
                }

            } else if (os.contains("mac")) {
                // macOS: pmset -g batt
                Process p = Runtime.getRuntime().exec("pmset -g batt");
                String out = new String(p.getInputStream().readAllBytes());
                return out.contains("charging") || out.contains("charged");
            }
        } catch (Exception ignored) {
            System.err.println("Charging command fallback failed: " + ignored.getMessage());
        }
        return false;
    }
}
