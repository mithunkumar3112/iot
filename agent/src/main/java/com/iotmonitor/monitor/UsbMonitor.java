package com.iotmonitor.monitor;

import oshi.SystemInfo;
import oshi.hardware.UsbDevice;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsbMonitor {
    private final SecurityEventReporter reporter;
    private final SystemInfo systemInfo = new SystemInfo();
    private Map<String, UsbSnapshot> knownDevices = new HashMap<>();
    private boolean initialized;

    public UsbMonitor(SecurityEventReporter reporter) {
        this.reporter = reporter;
    }

    public void scanAndReportChanges() {
        try {
            Map<String, UsbSnapshot> current = collectUsbDevices();
            if (!initialized) {
                knownDevices = current;
                initialized = true;
                return;
            }

            current.forEach((key, device) -> {
                if (!knownDevices.containsKey(key) && shouldAlert(device)) {
                    reporter.reportUsbActivity(device.displayName(), device.connectionType(), "CONNECTED");
                }
            });
            knownDevices.forEach((key, device) -> {
                if (!current.containsKey(key) && shouldAlert(device)) {
                    reporter.reportUsbActivity(device.displayName(), device.connectionType(), "REMOVED");
                }
            });
            knownDevices = current;
        } catch (Exception e) {
            System.err.println("USB monitor error: " + e.getMessage());
        }
    }

    private Map<String, UsbSnapshot> collectUsbDevices() {
        Map<String, UsbSnapshot> devices = new HashMap<>();
        List<UsbDevice> roots = systemInfo.getHardware().getUsbDevices(true);
        for (UsbDevice device : roots) collect(device, devices);
        return devices;
    }

    private void collect(UsbDevice device, Map<String, UsbSnapshot> devices) {
        String name = valueOrDefault(device.getName(), "USB device");
        String vendor = valueOrDefault(device.getVendor(), "");
        String serial = valueOrDefault(device.getSerialNumber(), "");
        String vendorId = valueOrDefault(device.getVendorId(), "");
        String productId = valueOrDefault(device.getProductId(), "");
        String combined = String.join(" ", name, vendor, vendorId, productId).toLowerCase(Locale.ROOT);
        String key = serial.isBlank()
                ? vendorId + ":" + productId + ":" + name + ":" + vendor
                : serial + ":" + vendorId + ":" + productId;
        devices.put(key, new UsbSnapshot(name, vendor, vendorId, productId, classifyConnectionType(combined)));
        for (UsbDevice child : device.getConnectedDevices()) collect(child, devices);
    }

    private boolean shouldAlert(UsbSnapshot device) {
        return !"USB_DEVICE".equals(device.connectionType());
    }

    private String classifyConnectionType(String combined) {
        if (containsAny(combined, "iphone", "ipad", "ipod", "apple mobile", "05ac")) {
            return "IPHONE";
        }
        if (containsAny(combined, "android", "adb", "mtp", "ptp", "samsung", "xiaomi", "redmi",
                "oneplus", "oppo", "vivo", "realme", "huawei", "honor", "motorola", "moto",
                "pixel", "google", "lg electronics", "sony mobile", "htc", "nokia",
                "04e8", "18d1", "2717", "2a70", "22d9", "2d95", "12d1")) {
            return "ANDROID_PHONE";
        }
        if (containsAny(combined, "hdd", "hard drive", "external", "seagate", "western digital",
                " wd ", "toshiba", "transcend", "portable drive", "elements", "passport")) {
            return "EXTERNAL_HDD";
        }
        if (containsAny(combined, "pendrive", "pen drive", "flash", "sandisk", "kingston",
                "cruzer", "data traveler", "datatraveler", "mass storage", "usb disk",
                "usb drive", "storage")) {
            return "PENDRIVE";
        }
        return "USB_DEVICE";
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private record UsbSnapshot(String name, String vendor, String vendorId, String productId, String connectionType) {
        String displayName() {
            if (vendor == null || vendor.isBlank() || name.toLowerCase(Locale.ROOT).contains(vendor.toLowerCase(Locale.ROOT))) {
                return name;
            }
            return vendor + " " + name;
        }
    }
}
