package com.iotmonitor.controller;

import com.iotmonitor.model.Alert;
import com.iotmonitor.model.SecurityScreenshot;
import com.iotmonitor.model.UsbActivity;
import com.iotmonitor.repository.AlertRepository;
import com.iotmonitor.repository.SecurityScreenshotRepository;
import com.iotmonitor.repository.UsbActivityRepository;
import com.iotmonitor.service.SecurityMonitoringService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/security")
public class SecurityController {

    private final SecurityMonitoringService securityService;
    private final AlertRepository alertRepository;
    private final UsbActivityRepository usbActivityRepository;
    private final SecurityScreenshotRepository screenshotRepository;

    public SecurityController(SecurityMonitoringService securityService,
                              AlertRepository alertRepository,
                              UsbActivityRepository usbActivityRepository,
                              SecurityScreenshotRepository screenshotRepository) {
        this.securityService = securityService;
        this.alertRepository = alertRepository;
        this.usbActivityRepository = usbActivityRepository;
        this.screenshotRepository = screenshotRepository;
    }

    @PostMapping("/alerts")
    public ResponseEntity<?> createAlert(@RequestBody Alert alert) {
        return securityService.recordSecurityAlert(alert)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().body(Map.of("duplicate", true)));
    }

    @GetMapping("/alerts")
    public List<Alert> getAlerts(@RequestParam(value = "deviceId", required = false) String deviceId) {
        return deviceId == null || deviceId.isBlank()
                ? alertRepository.findTop100ByOrderByTimestampDesc()
                : alertRepository.findTop100ByDeviceIdOrderByTimestampDesc(deviceId);
    }

    @PostMapping("/usb")
    public ResponseEntity<?> createUsbActivity(@RequestBody UsbActivity activity) {
        return securityService.recordUsbActivity(activity)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().body(Map.of("duplicate", true)));
    }

    @GetMapping("/usb")
    public List<UsbActivity> getUsbActivity(@RequestParam(value = "deviceId", required = false) String deviceId) {
        return deviceId == null || deviceId.isBlank()
                ? usbActivityRepository.findTop100ByOrderByTimestampDesc()
                : usbActivityRepository.findTop100ByDeviceIdOrderByTimestampDesc(deviceId);
    }

    @PostMapping(value = "/screenshot", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public SecurityScreenshot uploadScreenshot(@RequestParam String deviceId,
                                               @RequestParam(required = false) Long alertId,
                                               @RequestParam(required = false) Long usbActivityId,
                                               @RequestParam(defaultValue = "security") String eventType,
                                               @RequestBody byte[] image) throws IOException {
        return securityService.saveScreenshot(deviceId, alertId, usbActivityId, eventType, image);
    }

    @GetMapping("/screenshots/{deviceId}/{fileName:.+}")
    public ResponseEntity<FileSystemResource> getScreenshot(@PathVariable String deviceId, @PathVariable String fileName) {
        return screenshotRepository.findAll().stream()
                .filter(s -> s.getUrl() != null && s.getUrl().equals("/security/screenshots/" + deviceId + "/" + fileName))
                .findFirst()
                .map(s -> new File(s.getFilePath()))
                .filter(File::exists)
                .map(file -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(new FileSystemResource(file)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
