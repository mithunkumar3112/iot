package com.iotmonitor.controller;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/commands")
public class CommandController {

    // 🔴🟢 Monitoring state
    private static final AtomicBoolean MONITORING_ENABLED =
            new AtomicBoolean(true);

    // 🧾 Command history
    private static final List<String> HISTORY = new ArrayList<>();

    // 📡 Latest command for agent
    private static volatile String LAST_COMMAND = "NONE";

    // ==========================
    // 🔍 AGENT CHECKS STATUS
    // ==========================
    @GetMapping("/status")
    public boolean getStatus() {
        return MONITORING_ENABLED.get();
    }

    // ==========================
    // 🟢 TURN MONITORING ON
    // ==========================
    @PostMapping("/on")
    public void on() {
        MONITORING_ENABLED.set(true);
        HISTORY.add("ON at " + Instant.now());
        System.out.println("🟢 Monitoring ENABLED");
    }

    // ==========================
    // 🔴 TURN MONITORING OFF
    // ==========================
    @PostMapping("/off")
    public void off() {
        MONITORING_ENABLED.set(false);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        HISTORY.add("OFF at " + formatter.format(Instant.now()));
        System.out.println("🔴 Monitoring DISABLED");
    }

    // ==========================
    // ⏹ SHUTDOWN COMMAND
    // ==========================
    @PostMapping("/shutdown")
    public void shutdown() {
        LAST_COMMAND = "SHUTDOWN";
        HISTORY.add("SHUTDOWN at " + Instant.now());
        System.out.println("⏹ Shutdown command sent");
    }

    // ==========================
    // 🔁 RESTART AGENT
    // ==========================
    @PostMapping("/restart-agent")
    public void restartAgent() {
        LAST_COMMAND = "RESTART_AGENT";
        HISTORY.add("RESTART_AGENT at " + Instant.now());
        System.out.println("🔁 Restart agent command sent");
    }

    // ==========================
    // 📡 AGENT POLLS THIS
    // ==========================
    @GetMapping("/latest")
    public String latestCommand() {
        return LAST_COMMAND;
    }

    // ==========================
    // 📜 HISTORY FOR DASHBOARD
    // ==========================
    @GetMapping("/history")
    public List<String> history() {
        return HISTORY;
    }
}
