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

if(!token && !window.location.pathname.includes("login.html")){

window.location="login.html";

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

// ======================================
// FILE EXPLORER
// ======================================

const filesContainer = document.getElementById("files");

if(filesContainer){

async function loadFiles(){

const res = await authFetch("/files/list");
const files = await res.json();

filesContainer.innerHTML="";

files.forEach(file=>{

const row=document.createElement("div");
row.className="file";

const name=document.createElement("span");
name.innerText=file;

const read=document.createElement("button");
read.innerText="Read";
read.onclick=()=>{
window.open("/files/read/"+file,"_blank");
};

const download=document.createElement("button");
download.innerText="Download";
download.onclick=()=>{
window.location="/files/download/"+file;
};

row.appendChild(name);
row.appendChild(read);
row.appendChild(download);

filesContainer.appendChild(row);

});

}

loadFiles();

}

// ===============================
// ONE DRIVE FILES
// ===============================

const onedriveContainer = document.getElementById("onedrive-files");

if(onedriveContainer){

async function loadOneDriveFiles(){

try{

const res = await authFetch("/onedrive/files");

if(!res.ok){
  onedriveContainer.innerHTML = `<p>Error loading OneDrive files (${res.status} ${res.statusText})</p>`;
  return;
}

const data = await res.json();

onedriveContainer.innerHTML="";

data.value.forEach(file=>{

const row=document.createElement("div");
row.className="file";

const name=document.createElement("span");
name.innerText=file.name;

row.appendChild(name);

onedriveContainer.appendChild(row);

});

}catch(err){

onedriveContainer.innerHTML="<p>Error loading OneDrive files</p>";

}

}

loadOneDriveFiles();

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

const username=document.getElementById("username").value;
const password=document.getElementById("password").value;

try{

const res = await fetch("/auth/login",{
method:"POST",
headers:{
"Content-Type":"application/json"
},
body:JSON.stringify({
username:username,
password:password
})
});

if(!res.ok){

document.getElementById("error").innerText="Invalid login";
return;

}

const data=await res.json();

// store token
localStorage.setItem("token",data.token);

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
      const res = await authFetch("/commands/history");
      if(!res.ok) throw new Error("Failed to fetch history");
      
      const history = await res.json();
      
      if(history.length === 0){
        historyBox.innerHTML = "<p>No history found</p>";
        return;
      }
      
      let html = "<ul>";
      history.forEach(entry => {
        html += `<li>${entry}</li>`;
      });
      html += "</ul>";
      
      historyBox.innerHTML = html;
    }catch(err){
      historyBox.innerHTML = "<p style='color:red;'>Error loading history: " + err.message + "</p>";
      console.error("History error:", err);
    }
  }
  
  loadHistory();
  setInterval(loadHistory, 5000);
}

function logout(){

localStorage.removeItem("token");

window.location="login.html";

}
