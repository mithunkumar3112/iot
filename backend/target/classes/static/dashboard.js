// Dashboard JavaScript
document.addEventListener('DOMContentLoaded', function() {
    initializeDashboard();
    connectWebSocket();
    loadData();
});

function initializeDashboard() {
    // Menu toggle
    document.getElementById('menuToggle').addEventListener('click', function() {
        document.querySelector('.sidebar').classList.toggle('active');
    });

    // Initialize charts
    initializeCharts();
}

function initializeCharts() {
    // CPU Chart
    const cpuCtx = document.getElementById('cpuChart').getContext('2d');
    window.cpuChart = new Chart(cpuCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'CPU Usage %',
                data: [],
                borderColor: '#2563eb',
                backgroundColor: 'rgba(37, 99, 235, 0.1)',
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    max: 100
                }
            }
        }
    });

    // File Chart
    const fileCtx = document.getElementById('fileChart').getContext('2d');
    window.fileChart = new Chart(fileCtx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: 'Files Uploaded',
                data: [],
                backgroundColor: '#10b981'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false
        }
    });
}

function connectWebSocket() {
    const socket = new SockJS('/ws');
    const stompClient = Stomp.over(socket);

    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);

        // Subscribe to alerts
        stompClient.subscribe('/topic/alerts', function(message) {
            const alert = JSON.parse(message.body);
            showToast(alert.message, alert.severity.toLowerCase());
            updateNotificationBadge();
        });

        // Subscribe to process updates
        stompClient.subscribe('/topic/processes', function(message) {
            const processes = JSON.parse(message.body);
            updateProcessTable(processes);
        });

        // Subscribe to battery updates
        stompClient.subscribe('/topic/battery', function(message) {
            const batteryEvent = JSON.parse(message.body);
            if (batteryEvent.type === 'BATTERY_UPDATE') {
                updateBatteryDisplay(batteryEvent.data);
            }
        });

        // Subscribe to command status updates
        stompClient.subscribe('/topic/commands', function(message) {
            const commandEvent = JSON.parse(message.body);
            if (commandEvent.type === 'COMMAND_STATUS') {
                showCommandStatusToast(commandEvent);
            }
        });
    });
}

function loadData() {
    // Load metrics
    fetch('/metrics/latest')
        .then(response => response.json())
        .then(data => {
            updateMetrics(data);
            updateCharts(data);
        })
        .catch(error => console.error('Error loading metrics:', error));

    // Load processes
    fetch('/processes/default')
        .then(response => response.json())
        .then(data => updateProcessTable(data))
        .catch(error => console.error('Error loading processes:', error));

    // Load files
    fetch('/files/recent')
        .then(response => response.json())
        .then(data => updateFilesTable(data))
        .catch(error => console.error('Error loading files:', error));
}

function updateMetrics(data) {
    document.getElementById('cpuUsage').textContent = Math.round(data.cpu || 0) + '%';
    document.getElementById('ramUsage').textContent = Math.round(data.ram || 0) + '%';
    // Update other metrics as needed
}

function updateCharts(data) {
    // Update CPU chart
    const now = new Date().toLocaleTimeString();
    window.cpuChart.data.labels.push(now);
    window.cpuChart.data.datasets[0].data.push(data.cpu || 0);

    if (window.cpuChart.data.labels.length > 20) {
        window.cpuChart.data.labels.shift();
        window.cpuChart.data.datasets[0].data.shift();
    }
    window.cpuChart.update();
}

function updateProcessTable(processes) {
    const tbody = document.querySelector('#processTable tbody');
    tbody.innerHTML = '';

    processes.slice(0, 10).forEach(process => {
        const row = document.createElement('tr');
        const cpuClass = process.cpuUsage > 80 ? 'high-cpu' : '';

        row.innerHTML = `
            <td>${process.processName}</td>
            <td class="${cpuClass}">${process.cpuUsage.toFixed(1)}%</td>
            <td>${process.memoryUsage.toFixed(1)} MB</td>
        `;
        tbody.appendChild(row);
    });
}

function updateFilesTable(files) {
    const tbody = document.querySelector('#recentFilesTable tbody');
    tbody.innerHTML = '';

    files.slice(0, 5).forEach(file => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${file.name}</td>
            <td>${formatBytes(file.size)}</td>
            <td>${new Date(file.uploadTime).toLocaleDateString()}</td>
        `;
        tbody.appendChild(row);
    });
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;

    container.appendChild(toast);

    setTimeout(() => {
        toast.remove();
    }, 5000);
}

function updateNotificationBadge() {
    const badge = document.getElementById('notificationBadge');
    const count = parseInt(badge.textContent) + 1;
    badge.textContent = count;
    badge.style.display = count > 0 ? 'block' : 'none';
}

function showCommandStatusToast(commandEvent) {
    const message = `Command ${commandEvent.command} ${commandEvent.status.toLowerCase()} on ${commandEvent.deviceId}`;
    const type = commandEvent.status === 'SUCCESS' ? 'success' : 'error';
    showToast(message, type);
}
    const batteryLevel = Math.round(data.battery);
    const isCharging = data.charging;
    
    // Update dashboard card
    const statusElement = document.getElementById('batteryStatus');
    const iconElement = document.getElementById('batteryIcon');
    
    if (statusElement) {
        statusElement.textContent = batteryLevel + '% ' + (isCharging ? '⚡' : '');
    }
    if (iconElement) {
        iconElement.textContent = getBatteryIcon(batteryLevel, isCharging);
    }
    
    // Update modal if open
    const modalLevel = document.getElementById('batteryLevel');
    const modalStatus = document.getElementById('chargingStatus');
    
    if (modalLevel) {
        modalLevel.textContent = batteryLevel + '%';
        modalLevel.className = 'battery-level ' + getBatteryColorClass(batteryLevel);
    }
    if (modalStatus) {
        if (isCharging) {
            modalStatus.innerHTML = '<span class="charging-indicator">⚡ Charging</span>';
        } else {
            modalStatus.textContent = 'Not Charging';
        }
    }
}

function getBatteryColorClass(level) {
    if (level >= 50) return 'battery-green';
    if (level >= 20) return 'battery-yellow';
    return 'battery-red';
}

function getBatteryIcon(level, charging) {
    if (charging) return '🔋⚡';
    if (level >= 75) return '🔋';
    if (level >= 50) return '🔋';
    if (level >= 25) return '🪫';
    return '🪫';
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}