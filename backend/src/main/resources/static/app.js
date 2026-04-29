// ======================================
// AUTH HELPER
// ======================================

function authFetch(url, options = {}) {

const token = localStorage.getItem("token");

options.headers = {
...(options.headers || {}),
"Authorization": "Bearer " + token
};

return fetch(url, options);

}
// ===============================
// AUTH CHECK
// ===============================

const token = localStorage.getItem("token");
const publicPaths = [
    "/login.html",
    "/dashboard.html",
    "/performance.html",
    "/files.html",
    "/processes.html",
    "/history.html",
    "/connect.html",
    "/pair.html",
    "/controls.html"
];

const currentPath = window.location.pathname.toLowerCase();
const isPublicPage = publicPaths.some(path => currentPath.endsWith(path));

if (!token && !isPublicPage) {
    window.location = "login.html";
}
// ======================================
// PERFORMANCE MONITORING
// ======================================

const cpuCanvas = document.getElementById("cpuChart");
const ramCanvas = document.getElementById("ramChart");

if(cpuCanvas && ramCanvas){

const cpuCtx = cpuCanvas.getContext("2d");
const ramCtx = ramCanvas.getContext("2d");

const cpuValue = document.getElementById("cpuValue");
const ramValue = document.getElementById("ramValue");
const uptimeText = document.getElementById("uptime");
const batteryText = document.getElementById("battery");

let cpuData = [];
let ramData = [];

async function loadMetrics(){

try{

const res = await authFetch("/metrics/latest");

if(!res.ok) return;

const m = await res.json();


// ==========================
// Update CPU / RAM %
 // ==========================
cpuValue.innerText = m.cpu.toFixed(1);
ramValue.innerText = m.ram.toFixed(1);


// ==========================
// Battery
// ==========================
if(m.battery >= 0){
batteryText.innerText = m.battery.toFixed(0);
}else{
batteryText.innerText = "N/A";
}


// ==========================
// Uptime
// ==========================
uptimeText.innerText = formatUptime(m.uptime);


// ==========================
// Graph Data
// ==========================
cpuData.push(m.cpu);
ramData.push(m.ram);

if(cpuData.length > 40){
cpuData.shift();
ramData.shift();
}


// ==========================
// Draw Graph
// ==========================
drawGraph(cpuCtx,cpuData,"#38bdf8");
drawGraph(ramCtx,ramData,"#f97316");

}catch(err){

console.error("Metrics error:",err);

}

}


// ======================================
// DRAW GRAPH
// ======================================

function drawGraph(ctx,data,color){

ctx.clearRect(0,0,800,200);

ctx.strokeStyle=color;
ctx.beginPath();
ctx.moveTo(0,200);

data.forEach((v,i)=>{
ctx.lineTo(i*20,200-v*2);
});

ctx.stroke();

}


// ======================================
// FORMAT UPTIME
// ======================================

function formatUptime(seconds){

let days = Math.floor(seconds / 86400);
let hrs = Math.floor((seconds % 86400) / 3600);
let mins = Math.floor((seconds % 3600) / 60);
let secs = seconds % 60;

return `${days}d ${hrs}h ${mins}m ${secs}s`;

}


setInterval(loadMetrics,3000);

}

// ======================================
// CONTROL PAGE FUNCTIONS
// ======================================

async function turnOn(){
  try{
    const res = await authFetch("/commands/on", { method: "POST" });
    if(res.ok) alert("✅ Monitoring started");
    else alert("❌ Failed to start monitoring");
  }catch(err){
    console.error("Error:", err);
    alert("❌ Error: " + err.message);
  }
}

async function turnOff(){
  try{
    const res = await authFetch("/commands/off", { method: "POST" });
    if(res.ok) alert("⛔ Monitoring stopped");
    else alert("❌ Failed to stop monitoring");
  }catch(err){
    console.error("Error:", err);
    alert("❌ Error: " + err.message);
  }
}

async function shutdown(){
  try{
    const res = await authFetch("/commands/shutdown", { method: "POST" });
    if(res.ok) alert("🛑 Shutdown initiated");
    else alert("❌ Failed to shutdown");
  }catch(err){
    console.error("Error:", err);
    alert("❌ Error: " + err.message);
  }
}

async function sleep(){
  try{
    const res = await authFetch("/commands/sleep", { method: "POST" });
    if(res.ok) alert("💤 Sleep initiated");
    else alert("❌ Failed to sleep");
  }catch(err){
    console.error("Error:", err);
    alert("❌ Error: " + err.message);
  }
}

async function restartSystem(){
  try{
    const deviceId = localStorage.getItem("deviceId") || "default";
    const res = await authFetch("/commands/send", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId: deviceId, command: "RESTART" })
    });
    if(res.ok) alert("🔄 System restart initiated");
    else alert("❌ Failed to restart system");
  }catch(err){
    console.error("Error:", err);
    alert("❌ Error: " + err.message);
  }
}

async function restart(){
  try{
    const res = await authFetch("/commands/restart-agent", { method: "POST" });
    if(res.ok) alert("🔁 Restart initiated");
    else alert("❌ Failed to restart");
  }catch(err){
    console.error("Error:", err);
    alert("❌ Error: " + err.message);
  }
}

// ======================================
// CLOUD TOGGLE FUNCTIONS
// ======================================

async function loadCloudStatus() {
    try {
        const res = await authFetch("/config/cloud-status");
        if (res.ok) {
            const data = await res.json();
            const toggle = document.getElementById("cloudToggle");
            const status = document.getElementById("cloudStatus");
            if (toggle) toggle.checked = data.enabled;
            if (status) status.innerText = data.enabled ? "ON" : "OFF";
        }
    } catch (err) {
        console.error("Error loading cloud status:", err);
    }
}

async function toggleCloud(enabled) {
    try {
        const res = await authFetch("/config/cloud-toggle", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ enabled })
        });
        if (res.ok) {
            const data = await res.json();
            alert(data.message);
            loadCloudStatus(); // Refresh status
        } else {
            alert("Failed to toggle cloud");
        }
    } catch (err) {
        console.error("Error toggling cloud:", err);
        alert("Error: " + err.message);
    }
}

// Load cloud status on page load if controls page
if (document.getElementById("cloudToggle")) {
    loadCloudStatus();
}

// ======================================
// FILE EXPLORER
// ======================================

const filesContainer = document.getElementById("files");

if(filesContainer){

async function loadFiles(){
    const deviceId = localStorage.getItem("deviceId");

    if(!deviceId){
        filesContainer.innerHTML = "<p>Please log in with your Device ID to view your files.</p>";
        return;
    }

    // Prefer new Supabase-based endpoint
    let res = await authFetch(`/api/files/supabase?deviceId=${encodeURIComponent(deviceId)}`);
    if (!res.ok) {
        // Fallback to local DB path if Supabase is not available
        res = await authFetch(`/api/files/device/${encodeURIComponent(deviceId)}`);
    }

    const files = await res.json();

    filesContainer.innerHTML="";

    files.forEach(file=>{
        const row=document.createElement("div");
        row.className="file";

        const name=document.createElement("span");
        name.innerText=file.file_name || file.name || file;

        const fileUrl = file.file_url || file.fileUrl || file.path || file.url;

        const openButton=document.createElement("button");
        openButton.innerText="Open";
        openButton.onclick=()=>{
            if (fileUrl) window.open(fileUrl, "_blank");
        };

        const download=document.createElement("button");
        download.innerText="Download";
        download.onclick=()=>{
            if (fileUrl) window.location = fileUrl;
            else if(file.path) window.location="/files/download/" + encodeURIComponent(file.path);
        };

        row.appendChild(name);
        row.appendChild(openButton);
        row.appendChild(download);

        filesContainer.appendChild(row);

    });

}

loadFiles();

}

// ===============================
// SCREEN MONITORING
// ===============================

const screenImg = document.getElementById("screen");

if (screenImg) {

function loadScreenshot() {

screenImg.src = "/screenshot?time=" + new Date().getTime();

}

setInterval(loadScreenshot, 3000);

}
// ===============================
// LOGIN
// ===============================

async function login(){

const deviceId = document.getElementById("deviceId").value.trim();

if(!deviceId){
  document.getElementById("error").innerText = "Please enter your Device ID.";
  return;
}

try{

const res = await fetch("/auth/login",{
method:"POST",
headers:{
"Content-Type":"application/json"
},
body:JSON.stringify({
username: deviceId,
password: ""
})
});

if(!res.ok){

  document.getElementById("error").innerText="Invalid Device ID";
  return;

}

const data=await res.json();

// store token + device id
localStorage.setItem("token",data.token);
localStorage.setItem("deviceId", deviceId);

// go to dashboard
window.location="dashboard.html";

}catch(e){

  document.getElementById("error").innerText="Server error";

}

}

// ======================================
// HISTORY PAGE FUNCTIONS
// ======================================

const historyBox = document.getElementById("historyBox");

if(historyBox){
  async function loadHistory(){
    try{
      const deviceId = localStorage.getItem("deviceId");
      const url = deviceId ? `/commands/history?deviceId=${encodeURIComponent(deviceId)}` : "/commands/history";
      const res = await authFetch(url);
      if(!res.ok) throw new Error("Failed to fetch history");
      
      const history = await res.json();
      
      if(history.length === 0){
        historyBox.innerHTML = "<p>No activity recorded yet.</p>";
        return;
      }
      
      let html = "<div class='history-timeline'>";
      history.forEach(entry => {
        let entryClass = "history-entry";
        let icon = "📝";
        
        if(entry.includes("ALERT:")){
          entryClass += " alert-entry";
          icon = "⚠️";
        } else if(entry.includes("PROCESS:")){
          entryClass += " process-entry";
          icon = "📂";
        }
        
        html += `
          <div class="${entryClass}">
            <span class="icon">${icon}</span>
            <div class="details">${entry.replace("🎯 ALERT: ", "").replace("📂 PROCESS: ", "")}</div>
          </div>`;
      });
      html += "</div>";
      
      historyBox.innerHTML = html;
    }catch(err){
      historyBox.innerHTML = "<p style='color:red;'>Error loading history: " + err.message + "</p>";
      console.error("History error:", err);
    }
  }
  
  loadHistory();
  setInterval(loadHistory, 5000);
}

// ======================================
// DEVICE LISTING (for dropdowns)
// ======================================

/**
 * Get list of all devices from backend
 */
async function getDeviceList() {
  try {
    const res = await authFetch("/processes/devices");
    if (!res.ok) return [];
    return await res.json();
  } catch (error) {
    console.error("Error fetching devices:", error);
    return [];
  }
}

/**
 * Populate device dropdown
 */
async function populateDeviceDropdown(selectElementId) {
  const select = document.getElementById(selectElementId);
  if (!select) return;

  const devices = await getDeviceList();
  
  select.innerHTML = '<option value="">All Devices</option>';
  devices.forEach(device => {
    const option = document.createElement("option");
    option.value = device;
    option.textContent = device;
    select.appendChild(option);
  });
}

// ======================================
// APP ACTIVITY TRACKING
// ======================================

/**
 * Setup WebSocket for app activity updates
 */
function setupAppActivityWebSocket() {
  try {
    // Check if we're already connected
    if (window.appActivityWs) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    window.appActivityWs = new WebSocket(protocol + '//' + window.location.host + '/ws');

    window.appActivityWs.onopen = () => {
      console.log('✅ App Activity WebSocket connected');
    };

    window.appActivityWs.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);

        // Handle app activity events
        if (message.type === 'APP_EVENT') {
          handleAppActivityUpdate(message.data);
        }

        // Handle alerts
        if (message.type === 'APP_ALERT') {
          handleAppAlert(message);
        }
      } catch (e) {
        console.error('Error parsing app activity message:', e);
      }
    };

    window.appActivityWs.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    window.appActivityWs.onclose = () => {
      console.log('⚠️ App Activity WebSocket closed, will attempt to reconnect...');
      // Attempt to reconnect after 3 seconds
      setTimeout(setupAppActivityWebSocket, 3000);
    };
  } catch (e) {
    console.error('WebSocket setup error:', e);
  }
}

/**
 * Handle app activity update from WebSocket
 */
function handleAppActivityUpdate(activity) {
  console.log('📱 App activity update:', activity);
  // This can be overridden by pages that need to handle updates
  if (typeof onAppActivityUpdate === 'function') {
    onAppActivityUpdate(activity);
  }
}

/**
 * Handle app alert from WebSocket
 */
function handleAppAlert(alert) {
  console.log('🚨 App alert:', alert);
  // Show notification
  if (typeof showAppAlert === 'function') {
    showAppAlert(alert);
  } else {
    console.warn('Alert:', alert.message);
  }
}

// Initialize WebSocket on page load if authenticated
if (token) {
  document.addEventListener('DOMContentLoaded', setupAppActivityWebSocket);
}
