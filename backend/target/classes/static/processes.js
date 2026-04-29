// Process Monitor JavaScript
document.addEventListener('DOMContentLoaded', function() {
    loadProcesses();
    setupSearch();
    setupRefresh();

    // Auto refresh every 10 seconds
    setInterval(loadProcesses, 10000);
});

function loadProcesses() {
    const deviceId = document.getElementById('deviceSelect').value;

    fetch(`/processes/${deviceId}`)
        .then(response => response.json())
        .then(data => {
            updateProcessTable(data);
        })
        .catch(error => console.error('Error loading processes:', error));
}

function updateProcessTable(processes) {
    const tbody = document.querySelector('#processTable tbody');
    const searchTerm = document.getElementById('searchInput').value.toLowerCase();

    tbody.innerHTML = '';

    processes
        .filter(process => process.processName.toLowerCase().includes(searchTerm))
        .slice(0, 50) // Limit to 50 processes
        .forEach(process => {
            const row = document.createElement('tr');
            const cpuClass = process.cpuUsage > 80 ? 'high-cpu' : '';

            row.innerHTML = `
                <td>${process.processName}</td>
                <td class="${cpuClass}">${process.cpuUsage.toFixed(1)}%</td>
                <td>${process.memoryUsage.toFixed(1)} MB</td>
                <td>
                    <button onclick="killProcess('${process.processName}')" class="kill-btn">Kill</button>
                </td>
            `;
            tbody.appendChild(row);
        });
}

function setupSearch() {
    document.getElementById('searchInput').addEventListener('input', function() {
        loadProcesses();
    });
}

function setupRefresh() {
    document.getElementById('refreshBtn').addEventListener('click', loadProcesses);
}

function killProcess(processName) {
    if (confirm(`Kill process: ${processName}?`)) {
        const deviceId = document.getElementById('deviceSelect').value;

        fetch('/commands/send', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                deviceId: deviceId,
                command: `KILL_PROCESS ${processName}`
            })
        })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'queued') {
                showToast('Kill command sent', 'success');
            }
        })
        .catch(error => {
            console.error('Error sending kill command:', error);
            showToast('Failed to send kill command', 'error');
        });
    }
}

function showToast(message, type = 'info') {
    // Simple toast implementation
    alert(message);
}