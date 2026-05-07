package com.iotmonitor.controller;

import com.iotmonitor.model.ClipboardEntry;
import com.iotmonitor.repository.ClipboardEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/clipboard")
public class ClipboardController {

    private final ClipboardEntryRepository clipboardEntryRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ClipboardController(ClipboardEntryRepository clipboardEntryRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.clipboardEntryRepository = clipboardEntryRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/update")
    public Map<String, Object> receiveClipboardUpdate(@RequestBody Map<String, Object> payload) {
        String deviceId = trimToNull(payload.get("deviceId"));
        String content = stringValue(payload.get("content"));
        String contentType = normalizeContentType(payload.get("contentType"));
        Object contentSizeObj = payload.get("contentSize");
        LocalDateTime timestamp = parseTimestamp(trimToNull(payload.get("timestamp")));

        Map<String, Object> response = new HashMap<>();
        if (deviceId == null || content == null) {
            response.put("success", false);
            response.put("message", "Missing required fields: deviceId, content");
            return response;
        }

        Long contentSize = parseContentSize(contentSizeObj, content);

        ClipboardEntry saved = clipboardEntryRepository.save(
                new ClipboardEntry(deviceId, content, contentType, contentSize, timestamp)
        );

        Map<String, Object> entry = toClipboardDto(saved);
        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "CLIPBOARD_UPDATED");
        wsEvent.put("event", "clipboard_updated");
        wsEvent.put("deviceId", saved.getDeviceId());
        wsEvent.put("timestamp", saved.getTimestamp().toString());
        wsEvent.put("data", entry);
        messagingTemplate.convertAndSend("/topic/clipboard", wsEvent);

        response.put("success", true);
        response.put("entry", entry);
        return response;
    }

    @GetMapping("/latest/{deviceId}")
    public Map<String, Object> getLatestClipboard(@PathVariable String deviceId) {
        if (trimToNull(deviceId) == null) {
            return Map.of("deviceId", "", "content", "", "contentType", "text", "contentSize", 0, "timestamp", null);
        }

        List<ClipboardEntry> results = clipboardEntryRepository.findTop1ByDeviceIdOrderByTimestampDesc(deviceId);
        if (results.isEmpty()) {
            return Map.of("deviceId", deviceId, "content", "", "contentType", "text", "contentSize", 0, "timestamp", null);
        }

        ClipboardEntry latest = results.get(0);
        return toClipboardDto(latest);
    }

    @GetMapping("/history/{deviceId}")
    public List<Map<String, Object>> getClipboardHistory(
            @PathVariable String deviceId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "50") int limit) {

        int safeLimit = Math.max(1, Math.min(limit, 500));

        if (trimToNull(deviceId) == null) {
            return List.of();
        }

        boolean hasDateFilter = trimToNull(date) != null || trimToNull(startDate) != null || trimToNull(endDate) != null;
        List<ClipboardEntry> results;

        if (hasDateFilter) {
            LocalDate start = parseDate(startDate, parseDate(date, LocalDate.now()));
            LocalDate end = parseDate(endDate, parseDate(date, LocalDate.now()));
            LocalDateTime startTime = start.atStartOfDay();
            LocalDateTime endTime = end.plusDays(1).atStartOfDay().minusNanos(1);
            results = clipboardEntryRepository.findByDeviceIdAndTimestampRange(
                    deviceId,
                    startTime,
                    endTime,
                    PageRequest.of(0, safeLimit)
            );
        } else {
            results = clipboardEntryRepository.findByDeviceIdOrderByTimestampDesc(deviceId, PageRequest.of(0, safeLimit));
        }

        return results.stream().map(ClipboardController::toClipboardDto).toList();
    }

    @GetMapping("/devices")
    public List<String> getClipboardDevices() {
        return clipboardEntryRepository.findDistinctDeviceIds();
    }

    private static String trimToNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeContentType(Object value) {
        String contentType = trimToNull(value);
        if (contentType == null) return "text";
        return "image".equalsIgnoreCase(contentType) ? "image" : "text";
    }

    private static Long parseContentSize(Object value, String content) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value != null) {
            try {
                return Math.max(0L, Long.parseLong(String.valueOf(value)));
            } catch (Exception ignored) {
            }
        }
        return content == null ? 0L : (long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static Map<String, Object> toClipboardDto(ClipboardEntry entry) {
        return Map.ofEntries(
                Map.entry("id", entry.getId()),
                Map.entry("deviceId", entry.getDeviceId()),
                Map.entry("content", entry.getContent()),
                Map.entry("contentType", entry.getContentType()),
                Map.entry("contentSize", entry.getContentSize()),
                Map.entry("timestamp", entry.getTimestamp().toString())
        );
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
}
