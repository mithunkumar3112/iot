// Laptop Monitor Dashboard Logic
const RENDER_BACKEND_URL = 'https://laptop-monitor-backend.onrender.com';

// DOM Elements
const deviceIdInput = document.getElementById('device-id-input');
const fetchBtn = document.getElementById('fetch-btn');
const fileGrid = document.getElementById('file-grid');
const loader = document.getElementById('loader');

const filesTabBtn = document.getElementById('files-tab-btn');
const historyTabBtn = document.getElementById('history-tab-btn');
const filesSection = document.getElementById('files-section');
const historySection = document.getElementById('history-section');

const histDeviceId = document.getElementById('hist-device-id');
const histAppName = document.getElementById('hist-app-name');
const fetchHistoryBtn = document.getElementById('fetch-history-btn');
const historyTimeline = document.getElementById('history-timeline');

const notificationContainer = document.getElementById('notification-container');

// Load last used device ID from localStorage
const lastDeviceId = localStorage.getItem('lastDeviceId') || 'Nandy-pc-66d0';
deviceIdInput.value = lastDeviceId;
histDeviceId.value = lastDeviceId;
fetchFiles(lastDeviceId);

// --- TAB SWITCHING ---
filesTabBtn.addEventListener('click', () => {
    filesTabBtn.classList.add('active');
    historyTabBtn.classList.remove('active');
    filesSection.style.display = 'block';
    historySection.style.display = 'none';
});

historyTabBtn.addEventListener('click', () => {
    historyTabBtn.classList.add('active');
    filesTabBtn.classList.remove('active');
    historySection.style.display = 'block';
    filesSection.style.display = 'none';
    fetchHistory();
});

// --- FILE SYNC LOGIC ---
fetchBtn.addEventListener('click', () => {
    const deviceId = deviceIdInput.value.trim();
    if (deviceId) {
        localStorage.setItem('lastDeviceId', deviceId);
        histDeviceId.value = deviceId;
        fetchFiles(deviceId);
    }
});

async function fetchFiles(deviceId) {
    fileGrid.innerHTML = '';
    loader.style.display = 'block';

    try {
        const response = await fetch(`${RENDER_BACKEND_URL}/files/${encodeURIComponent(deviceId)}`);
        if (response.ok) {
            const files = await response.json();
            displayFiles(files);
        } else {
            fileGrid.innerHTML = '<p style="color: #ef4444; padding: 1rem;">Failed to fetch files. Is the backend running?</p>';
        }
    } catch (err) {
        fileGrid.innerHTML = '<p style="color: #ef4444; padding: 1rem;">Connection error. Check console.</p>';
    } finally {
        loader.style.display = 'none';
    }
}

function displayFiles(files) {
    if (!files || files.length === 0) {
        fileGrid.innerHTML = '<p style="text-align: center; grid-column: 1/-1; color: var(--text-muted); padding: 2rem;">No files found for this device.</p>';
        return;
    }

    files.forEach(file => {
        const card = document.createElement('div');
        card.className = 'file-card';
        const date = new Date(file.uploaded_at).toLocaleString();
        card.innerHTML = `
            <h3>${file.file_name}</h3>
            <div class="file-meta">
                <span>📅 Synchronized: ${date}</span>
            </div>
            <a href="${file.file_url}" target="_blank" class="file-link">View File →</a>
        `;
        fileGrid.appendChild(card);
    });
}

// --- HISTORY LOGIC ---
fetchHistoryBtn.addEventListener('click', fetchHistory);

async function fetchHistory() {
    const deviceId = histDeviceId.value.trim();
    const appName = histAppName.value.trim();
    
    historyTimeline.innerHTML = '';
    loader.style.display = 'block';

    try {
        let url = `${RENDER_BACKEND_URL}/history?deviceId=${encodeURIComponent(deviceId)}`;
        if (appName) url += `&appName=${encodeURIComponent(appName)}`;

        const response = await fetch(url);
        if (response.ok) {
            const data = await response.json();
            displayHistory(data);
        }
    } catch (err) {
        console.error('History Fetch Error:', err);
    } finally {
        loader.style.display = 'none';
    }
}

function displayHistory(items) {
    if (!items || items.length === 0) {
        historyTimeline.innerHTML = '<p style="color: var(--text-muted); padding: 1rem;">No activity logs found.</p>';
        return;
    }

    items.forEach(item => {
        const div = document.createElement('div');
        div.className = `timeline-item ${item.type === 'alert' ? 'alert' : ''}`;
        
        const time = new Date(item.timestamp).toLocaleTimeString();
        const date = new Date(item.timestamp).toLocaleDateString();

        if (item.type === 'alert') {
            div.innerHTML = `
                <span class="item-time">${date} ${time}</span>
                <span class="item-title"><span class="badge alert">ALERT</span> ${item.app_name} Detected</span>
                <p class="item-details">${item.message}</p>
            `;
        } else {
            div.innerHTML = `
                <span class="item-time">${date} ${time}</span>
                <span class="item-title"><span class="badge process">PROCESS</span> ${item.process_name}</span>
                <p class="item-details">CPU: ${item.cpu_usage}% | RAM: ${item.memory_usage} MB</p>
            `;
        }
        historyTimeline.appendChild(div);
    });
}

// --- REAL-TIME NOTIFICATIONS (Socket.io) ---
const socket = io(RENDER_BACKEND_URL);

socket.on('connect', () => {
    console.log('✅ Connected to backend for real-time alerts');
});

socket.on('app_detected', (data) => {
    console.log('🎯 App Detected payload:', data);
    showToast(data.app, data.message);
    
    // If currently on history tab for the same device, refresh it
    if (historyTabBtn.classList.contains('active') && histDeviceId.value === data.deviceId) {
        fetchHistory();
    }
});

function showToast(app, message) {
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.innerHTML = `
        <h4>🎯 App Alert</h4>
        <p><strong>${app}</strong> is now running on your device.</p>
    `;
    notificationContainer.appendChild(toast);

    // Auto remove after 5 seconds
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        setTimeout(() => toast.remove(), 300);
    }, 5000);
}
