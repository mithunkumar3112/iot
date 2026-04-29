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

            if (powerSources != null && !powerSources.isEmpty()) {
                PowerSource battery = powerSources.get(0);

                double batteryPercent = battery.getRemainingCapacityPercent();
                boolean isCharging = battery.isCharging();

                // Send battery data to backend
                Map<String, Object> payload = Map.of(
                    "deviceId", apiClient.getDeviceId(),
                    "battery", batteryPercent,
                    "charging", isCharging
                );

                System.out.println("\n=== 🔋 SENDING BATTERY DATA ===");
                System.out.println("🔋 Device ID: " + apiClient.getDeviceId());
                System.out.println("🔋 Battery: " + String.format("%.1f", batteryPercent) + "%");
                System.out.println("🔋 Charging: " + isCharging);

                apiClient.sendBatteryData(payload);
                System.out.println("✅ Battery data sent successfully");
            } else {
                System.out.println("⚠️ No battery detected on this system");
            }

        } catch (Exception e) {
            System.err.println("❌ Error collecting battery data: " + e.getMessage());
        }
    }
}