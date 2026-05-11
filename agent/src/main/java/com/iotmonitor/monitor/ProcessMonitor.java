package com.iotmonitor.monitor;

import com.iotmonitor.network.ApiClient;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessMonitor {

    private final ApiClient apiClient;
    private final OperatingSystem os;
    private final Map<Integer, OSProcess> lastProcessMap = new HashMap<>();

    private static final int MAX_PROCESSES = 80;
    private final String currentUser = System.getProperty("user.name", "").toLowerCase();

    // Expanded list of user applications
    private static final Set<String> VISIBLE_APP_NAMES = Set.of(
            // Browsers
            "chrome.exe", "firefox.exe", "msedge.exe", "opera.exe", "brave.exe", "safari.exe",
            // Communication
            "teams.exe", "slack.exe", "discord.exe", "skype.exe", "zoom.exe", "webex.exe",
            // Development
            "code.exe", "vscode.exe", "idea64.exe", "eclipse.exe", "sublime_text.exe", "notepad++.exe",
            "intellij.exe", "pycharm.exe", "webstorm.exe", "goland.exe", "rider.exe",
            // Media
            "spotify.exe", "vlc.exe", "itunes.exe", "wmplayer.exe", "audacity.exe", "obs64.exe",
            // Office/Productivity
            "winword.exe", "excel.exe", "powerpnt.exe", "outlook.exe", "onenote.exe",
            "libreoffice.exe", "openoffice.exe",
            // System Utilities
            "notepad.exe", "calc.exe", "explorer.exe", "mspaint.exe", "snippingtool.exe",
            // Terminals/Command Line
            "powershell.exe", "cmd.exe", "bash.exe", "zsh.exe", "terminal.exe", "conhost.exe",
            // File Management
            "7zfm.exe", "winrar.exe", "filezilla.exe", "putty.exe", "mstsc.exe",
            // Graphics/Design
            "gimp.exe", "photoshop.exe", "illustrator.exe", "premiere.exe", "aftereffects.exe",
            "blender.exe", "krita.exe", "inkscape.exe",
            // User-launched tools
            "javaw.exe", "pythonw.exe", "git-bash.exe",
            // Linux/Mac equivalents (without .exe)
            "chrome", "firefox", "safari", "opera", "brave", "spotify", "vlc", "teams", "slack",
            "discord", "code", "vscode", "idea", "sublime", "terminal", "bash", "zsh"
    );

    // Comprehensive background/system processes to exclude
    private static final Set<String> BACKGROUND_PROCESSES = Set.of(
            // Windows System Processes
            "system", "system idle process", "wininit.exe", "winlogon.exe", "csrss.exe",
            "smss.exe", "lsass.exe", "services.exe", "svchost.exe", "spoolsv.exe",
            "taskhost.exe", "taskhostw.exe", "dwm.exe", "sihost.exe", "fontdrvhost.exe",
            "ctfmon.exe", "searchindexer.exe", "searchui.exe", "runtimebroker.exe",
            "backgroundtaskhost.exe", "applicationframehost.exe", "systemsettings.exe",
            "securityhealthservice.exe", "msmpeng.exe", "nissrv.exe", "wscsvc.exe",
            "trustedinstaller.exe", "tiworker.exe", "mousocoreworker.exe", "compattelrunner.exe",
            "audiodg.exe", "rundll32.exe", "werfault.exe", "dllhost.exe", "winver.exe",
            "lpksetup.exe", "mobsync.exe", "wmiprvse.exe", "regsvcs.exe", "regedit.exe",
            "mmc.exe", "taskmgr.exe", "resmon.exe", "perfmon.exe", "eventvwr.exe",
            "compmgmt.msc", "devmgmt.msc", "diskmgmt.msc", "services.msc",
            // Windows Services
            "sqlservr.exe", "sqlagent.exe", "sqlbrowser.exe", "sqlwriter.exe",
            "msdtc.exe", "clussvc.exe", "clusdisk.sys", "iscsi.exe", "hyper-v",
            // Linux System Processes
            "systemd", "init", "kthreadd", "kworker", "ksoftirqd", "migration", "rcu",
            "watchdog", "khungtaskd", "kswapd", "ksmd", "khugepaged", "crypto",
            "kintegrityd", "kblockd", "ata_sff", "md", "raid", "edac-poller",
            "devfreq_wq", "watchdogd", "cpuset", "khelper", "netns", "pm", "writeback",
            "bioset", "kdmflush", "xfsalloc", "xfs_mru_cache", "jfsIO", "jfsCommit",
            "jfsSync", "ext4-rsv-conver", "ext4-unrsv-conv", "kjournald", "flush",
            // Mac System Processes
            "kernel_task", "launchd", "syslogd", "configd", "diskarbitrationd",
            "notifyd", "cfprefsd", "distnoted", "usbmuxd", "mediaremoted",
            "AppleIDAuthAgent", "coreservicesd", "WindowServer", "Dock", "Finder",
            "SystemUIServer", "loginwindow", "UserEventAgent", "AirPort Base Station Agent",
            // Common background services
            "cron", "crond", "anacron", "atd", "dbus-daemon", "dbus-launch",
            "avahi-daemon", "cups", "cupsd", "bluetoothd", "NetworkManager",
            "wpa_supplicant", "dhcpcd", "dhclient", "ntpd", "chronyd",
            "rsyslogd", "syslog-ng", "journald", "rsync", "sshd", "httpd", "nginx",
            "mysqld", "postgresql", "mongod", "redis-server", "memcached",
            "docker", "dockerd", "containerd", "kubelet", "etcd", "apiserver",
            "kube-scheduler", "kube-controller", "kube-proxy", "calico", "flannel"
    );

    public ProcessMonitor(ApiClient apiClient) {
        this.apiClient = apiClient;
        SystemInfo systemInfo = new SystemInfo();
        this.os = systemInfo.getOperatingSystem();
    }

    public void collectAndSendProcesses() {
        List<OSProcess> allProcesses = os.getProcesses();
        Map<String, ProcessGroup> processGroups = new HashMap<>();
        String timestamp = Instant.now().toString();

        // Single pass: collect, calculate CPU, group, and filter
        for (OSProcess process : allProcesses) {
            int pid = process.getProcessID();

            // Skip idle process and invalid PIDs
            if (pid == 0 || pid < 0) continue;

            String processName = process.getName();
            String processUser = process.getUser();

            // Only report user-facing apps/tools or active user processes.
            if (!shouldReportProcess(processName, processUser, process)) continue;

            // Calculate CPU usage
            double cpuPercent = 0.0;
            if (lastProcessMap.containsKey(pid)) {
                cpuPercent = process.getProcessCpuLoadBetweenTicks(lastProcessMap.get(pid)) * 100;
            }
            lastProcessMap.put(pid, process);

            // Filter: Skip only invisible or empty items
            double memoryMB = process.getResidentSetSize() / (1024.0 * 1024.0);
            if ((processName == null || processName.isBlank()) && cpuPercent <= 0 && memoryMB <= 0) continue;

            // Group processes by normalized name
            String normalizedName = normalizeProcessName(processName);

            ProcessGroup group = processGroups.computeIfAbsent(normalizedName, k -> new ProcessGroup(normalizedName));
            group.addProcess(process, cpuPercent, memoryMB, pid);
        }

        // Convert groups to app data.
        List<Map<String, Object>> filteredProcesses = processGroups.values().stream()
            .map(group -> group.toProcessData(timestamp))
            .sorted((p1, p2) -> {
                double cpu1 = ((Number) p1.get("cpu")).doubleValue();
                double cpu2 = ((Number) p2.get("cpu")).doubleValue();
                return Double.compare(cpu2, cpu1); // Descending order
            })
            .limit(MAX_PROCESSES)
            .collect(Collectors.toList());

        // Cleanup dead processes from map
        lastProcessMap.keySet().removeIf(pid ->
            allProcesses.stream().noneMatch(p -> p.getProcessID() == pid)
        );

        // Send grouped, filtered processes to backend
        System.out.println("\n=== 📊 SENDING PROCESSES ===");
        System.out.println("📊 Total processes collected: " + allProcesses.size());
        System.out.println("📊 Groups created: " + processGroups.size());
        System.out.println("📊 Filtered processes to send: " + filteredProcesses.size());
        System.out.println("📊 Device ID: " + apiClient.getDeviceId());
        System.out.println("📊 Backend URL: " + apiClient.getBackendUrl() + "/processes");

        for (Map<String, Object> proc : filteredProcesses) {
            System.out.println("📊 Process: " + proc.get("name") + " | CPU: " + proc.get("cpu") + "% | Memory: " + proc.get("memory") + "MB");
        }

        apiClient.sendProcesses(Map.of(
            "deviceId", apiClient.getDeviceId(),
            "processes", filteredProcesses
        ));

        System.out.println("✅ Processes sent successfully");
    }

    private boolean shouldReportProcess(String processName, String processUser, OSProcess process) {
        if (processName == null || processName.isBlank()) {
            return false;
        }
        String lowerName = processName.toLowerCase();

        if (isBackgroundProcess(lowerName)) {
            return false;
        }

        if (processUser != null && !processUser.isBlank()) {
            String userLower = processUser.toLowerCase();
            if (!userLower.equals(currentUser) && !userLower.contains("user") && !userLower.contains("desktop")) {
                return false;
            }
        }

        if (isVisibleAppProcess(lowerName)) {
            return true;
        }

        double memoryMB = process.getResidentSetSize() / (1024.0 * 1024.0);
        double cpu = process.getProcessCpuLoadCumulative() * 100;
        return cpu > 1.0 || memoryMB > 20.0;
    }

    private boolean isBackgroundProcess(String processName) {
        if (processName == null || processName.isBlank()) {
            return true;
        }
        String lower = processName.toLowerCase();

        // Check exact matches and substrings for background processes
        return BACKGROUND_PROCESSES.stream().anyMatch(bg ->
            lower.equals(bg) || lower.contains(bg) || bg.contains(lower)
        );
    }

    private boolean isVisibleAppProcess(String processName) {
        if (processName == null || processName.isBlank()) {
            return false;
        }
        String lower = processName.toLowerCase();

        // Check if process matches visible app names
        return VISIBLE_APP_NAMES.stream().anyMatch(app ->
            lower.equals(app) || lower.contains(app) || app.contains(lower)
        );
    }

    private String normalizeProcessName(String processName) {
        if (processName == null) return "unknown";

        String lower = processName.toLowerCase();
        // Remove .exe extension
        if (lower.endsWith(".exe")) {
            lower = lower.substring(0, lower.length() - 4);
        }
        return lower.trim();
    }

    // Helper class to group processes
    private static class ProcessGroup {
        final String name;
        double totalCpu = 0.0;
        double totalMemory = 0.0;
        double maxCpu = 0.0;
        int count = 0;
        int representativePid = 0;
        double representativeCpu = 0.0;

        ProcessGroup(String name) {
            this.name = name;
        }

        void addProcess(OSProcess process, double cpuPercent, double memoryMB, int pid) {
            totalCpu += cpuPercent;
            totalMemory += memoryMB;
            count++;

            // Keep track of the process with highest CPU usage as representative
            if (cpuPercent > representativeCpu) {
                representativeCpu = cpuPercent;
                representativePid = pid;
                maxCpu = cpuPercent;
            }
        }

        Map<String, Object> toProcessData(String timestamp) {
            return Map.of(
                "name", name,
                "cpu", Math.round(maxCpu * 100.0) / 100.0, // Use max CPU for display
                "memory", Math.round(totalMemory * 100.0) / 100.0, // Total memory
                "timestamp", timestamp,
                "pid", representativePid,
                "instanceCount", count
            );
        }
    }
}
