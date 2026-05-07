package com.iotmonitor.monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LoginSessionMonitor {
    private final SecurityEventReporter reporter;
    private final Set<String> seenFailedEvents = new HashSet<>();
    private final String username = System.getProperty("user.name", "unknown");

    public LoginSessionMonitor(SecurityEventReporter reporter) {
        this.reporter = reporter;
    }

    public void reportStartupSession() {
        reporter.reportSecurityAlert("SYSTEM_ON", "Laptop/system turned ON", "HIGH", "agent");
        reporter.reportSession(username, "ACTIVE");
    }

    public void scanFailedLogins() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            Process process = new ProcessBuilder("wevtutil", "qe", "Security",
                    "/q:*[System[(EventID=4625)]]", "/f:text", "/c:6", "/rd:true")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            String text = output.toString();
            if (text.isBlank()) return;
            String fingerprint = Integer.toHexString(text.hashCode());
            if (seenFailedEvents.add(fingerprint)) {
                reporter.reportSecurityAlert("FAILED_LOGIN", "Wrong password/login attempt detected", "CRITICAL", "windows-event-log");
                reporter.reportSession(username, "FAILED");
            }
        } catch (Exception e) {
            System.err.println("Login session monitor skipped: " + e.getMessage());
        }
    }
}
