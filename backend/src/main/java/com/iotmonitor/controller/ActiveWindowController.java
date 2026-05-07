package com.iotmonitor.controller;

import com.iotmonitor.model.ActiveWindowActivity;
import com.iotmonitor.repository.ActiveWindowActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/activity")
public class ActiveWindowController {

    private final ActiveWindowActivityRepository activeWindowActivityRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ActiveWindowController(ActiveWindowActivityRepository activeWindowActivityRepository,
                                  SimpMessagingTemplate messagingTemplate) {
        this.activeWindowActivityRepository = activeWindowActivityRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * NEW code: receive active foreground-window changes from the laptop agent.
     * POST /activity/window
     */
    @PostMapping("/window")
    public Map<String, Object> receiveWindowActivity(@RequestBody Map<String, Object> payload) {
        String deviceId = trimToNull(payload.get("deviceId"));
        String activeWindow = trimToNull(payload.get("activeWindow"));
        LocalDateTime timestamp = parseTimestamp(trimToNull(payload.get("timestamp")));

        Map<String, Object> response = new HashMap<>();
        if (deviceId == null || activeWindow == null) {
            response.put("success", false);
            response.put("message", "Missing required fields: deviceId, activeWindow");
            return response;
        }

        ActiveWindowActivity saved = activeWindowActivityRepository.save(
                new ActiveWindowActivity(deviceId, activeWindow, timestamp)
        );

        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "ACTIVE_WINDOW_CHANGED");
        wsEvent.put("event", "active_window_changed");
        wsEvent.put("data", saved);
        messagingTemplate.convertAndSend("/topic/activity-window", wsEvent);

        response.put("success", true);
        response.put("activity", saved);
        return response;
    }

    /**
     * NEW code: latest active-window history.
     * GET /activity/window
     */
    @GetMapping("/window")
    public List<ActiveWindowActivity> getWindowActivity(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "100") int limit) {
        String normalizedDevice = trimToNull(deviceId);
        int safeLimit = safeLimit(limit, 500);
        boolean hasDateFilter = trimToNull(date) != null || trimToNull(startDate) != null || trimToNull(endDate) != null;
        List<ActiveWindowActivity> rows;
        if (hasDateFilter) {
            LocalDate start = parseDate(startDate, parseDate(date, LocalDate.now()));
            LocalDate end = parseDate(endDate, parseDate(date, LocalDate.now()));
            rows = activeWindowActivityRepository.findTimeline(
                    normalizedDevice,
                    start.atStartOfDay(),
                    end.plusDays(1).atStartOfDay().minusNanos(1),
                    PageRequest.of(0, safeLimit)
            );
        } else {
            rows = normalizedDevice == null
                    ? activeWindowActivityRepository.findTop100ByOrderByTimestampDesc()
                    : activeWindowActivityRepository.findByDeviceIdOrderByTimestampDesc(normalizedDevice, PageRequest.of(0, safeLimit));
        }
        return rows.stream().limit(safeLimit).toList();
    }

    /**
     * NEW code: device timeline with optional date/range filters.
     * GET /activity/timeline/{deviceId}
     */
    @GetMapping("/timeline/{deviceId}")
    public List<ActiveWindowActivity> getDeviceTimeline(
            @PathVariable String deviceId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "200") int limit) {

        int safeLimit = safeLimit(limit, 500);
        LocalDate start = parseDate(startDate, parseDate(date, LocalDate.now()));
        LocalDate end = parseDate(endDate, parseDate(date, LocalDate.now()));
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay().minusNanos(1);
        return activeWindowActivityRepository.findByDeviceIdAndTimestampRange(
                deviceId,
                startTime,
                endTime,
                PageRequest.of(0, safeLimit)
        );
    }

    @GetMapping("/devices")
    public List<String> getActivityDevices() {
        return activeWindowActivityRepository.findDistinctDeviceIds();
    }

    private static String trimToNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception ignoredAgain) {
                try {
                    return LocalDateTime.parse(raw);
                } catch (Exception finalIgnored) {
                    return LocalDateTime.now();
                }
            }
        }
    }

    private static int safeLimit(int requested, int max) {
        return Math.max(1, Math.min(requested, max));
    }
}
