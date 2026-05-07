(function () {
  var navItems = [
    { id: 'dashboard', label: 'Dashboard', href: 'dashboard.html', icon: 'D' },
    { id: 'screen', label: 'Screen Monitoring', href: 'screen-monitor.html', icon: 'S' },
    { id: 'files', label: 'File Explorer', href: 'files-explorer.html', icon: 'F' },
    { id: 'processes', label: 'Processes', href: 'processes.html', icon: 'P' },
    { id: 'timeline', label: 'Activity Timeline', href: 'activity-timeline.html', icon: 'T' },
    { id: 'clipboard', label: 'Clipboard', href: 'clipboard.html', icon: 'C' },
    { id: 'security', label: 'Security Alerts', href: 'security-alerts.html', icon: 'A' },
    { id: 'sessions', label: 'Session History', href: 'session-history.html', icon: 'H' },
    { id: 'commands', label: 'Commands', href: 'dashboard.html#commands', icon: 'X' }
  ];

  function escapeHtml(value) {
    var div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
  }

  function getStoredDevice() {
    var params = new URLSearchParams(window.location.search);
    var queryDevice = params.get('deviceId') || params.get('device');
    if (queryDevice) {
      localStorage.setItem('deviceId', queryDevice);
      window.history.replaceState({}, document.title, window.location.pathname);
      return queryDevice;
    }
    return localStorage.getItem('deviceId') || '';
  }

  function setDevice(deviceId) {
    var next = deviceId || '';
    var previous = localStorage.getItem('deviceId') || '';
    if (next === previous) return;
    if (deviceId) localStorage.setItem('deviceId', deviceId);
    else localStorage.removeItem('deviceId');
    window.dispatchEvent(new CustomEvent('devicechange', { detail: { deviceId: deviceId || '' } }));
  }

  function toast(title, body, type) {
    var area = document.getElementById('toastArea');
    if (!area) return;
    var el = document.createElement('div');
    el.className = 'toast ' + (type || '');
    el.innerHTML = '<div class="toast-title">' + escapeHtml(title) + '</div><div class="toast-body">' + escapeHtml(body || '') + '</div>';
    area.appendChild(el);
    setTimeout(function () {
      el.style.opacity = '0';
      el.style.transition = 'opacity .25s ease';
      setTimeout(function () { el.remove(); }, 260);
    }, 4200);
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(title, { body: body || '' });
    }
  }

  function formatDateTime(value) {
    if (!value) return '--';
    var date = new Date(value);
    if (isNaN(date.getTime())) return '--';
    return date.toLocaleDateString([], { month: 'short', day: '2-digit' }) + ' ' +
      date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  function formatTime(value) {
    var date = value ? new Date(value) : new Date();
    if (isNaN(date.getTime())) return '--';
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true });
  }

  function connectStomp(subscriptions, onMessage) {
    var ws;
    function connect() {
      try {
        ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
        ws.onopen = function () {
          ws.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0');
        };
        ws.onmessage = function (event) {
          event.data.split('\0').forEach(function (frame) {
            if (!frame.trim()) return;
            if (frame.indexOf('CONNECTED') === 0) {
              subscriptions.forEach(function (topic, index) {
                ws.send('SUBSCRIBE\nid:shell-' + index + '-' + Date.now() + '\ndestination:' + topic + '\n\n\0');
              });
              return;
            }
            var idx = frame.indexOf('\n\n');
            if (idx < 0) return;
            try {
              onMessage(JSON.parse(frame.substring(idx + 2)), frame);
            } catch (e) {
              console.warn('Realtime parse failed', e);
            }
          });
        };
        ws.onclose = function () { setTimeout(connect, 3000); };
      } catch (e) {
        setTimeout(connect, 3000);
      }
    }
    connect();
    return function () { if (ws) ws.close(); };
  }

  function updateDeviceOptions(devices, selected) {
    var select = document.getElementById('globalDeviceSelect');
    if (!select) return;
    var values = Array.from(new Set((devices || []).filter(Boolean)));
    if (selected && values.indexOf(selected) < 0) values.unshift(selected);
    select.innerHTML = '<option value="">All devices</option>' + values.map(function (device) {
      return '<option value="' + escapeHtml(device) + '">' + escapeHtml(device) + '</option>';
    }).join('');
    select.value = selected || '';
  }

  function init(options) {
    options = options || {};
    var active = options.active || 'dashboard';
    var selected = getStoredDevice();
    var nav = navItems.map(function (item) {
      return '<a class="nav-link ' + (item.id === active ? 'active' : '') + '" href="' + item.href + '">' +
        '<span class="nav-icon">' + item.icon + '</span><span class="nav-label">' + item.label + '</span></a>';
    }).join('');
    var actions = options.actions || '';
    document.body.insertAdjacentHTML('afterbegin',
      '<div class="app-shell"><aside class="sidebar"><div class="brand"><div class="brand-mark">LM</div><div><div class="brand-title">Laptop Monitor</div><div class="brand-subtitle">Enterprise console</div></div></div><nav class="nav">' +
      nav + '</nav></aside><div class="sidebar-backdrop" id="sidebarBackdrop"></div><main class="shell-main"><header class="topbar"><div class="page-title"><button class="mobile-menu" id="mobileMenuBtn">Menu</button><div><h1>' +
      escapeHtml(options.title || document.title || 'Dashboard') + '</h1><p>' + escapeHtml(options.subtitle || 'Live monitoring workspace') +
      '</p></div></div><div class="topbar-actions"><select class="device-select" id="globalDeviceSelect"><option value="">All devices</option></select><button class="btn secondary" id="globalRefreshBtn">Refresh</button>' +
      '<span class="status-pill status-offline" id="globalStatus">Waiting</span><button class="icon-btn" id="globalNotifyBtn" title="Enable notifications">N<span class="notif-dot" id="notifDot" style="display:none"></span></button><div class="profile"><div class="avatar">U</div><div><div style="font-weight:800;font-size:12px;">Admin</div><div class="profile-text">Monitoring</div></div></div>' +
      actions + '</div></header><section class="page-content" id="pageContent"></section></main></div><div class="toast-area" id="toastArea"></div>');

    document.getElementById('pageContent').appendChild(options.content || document.createElement('div'));
    updateDeviceOptions(options.devices || [], selected);
    document.getElementById('globalDeviceSelect').addEventListener('change', function () { setDevice(this.value); });
    document.getElementById('globalRefreshBtn').addEventListener('click', function () {
      window.dispatchEvent(new CustomEvent('shellrefresh'));
    });
    document.getElementById('globalNotifyBtn').addEventListener('click', function () {
      if ('Notification' in window) Notification.requestPermission();
    });
    document.getElementById('mobileMenuBtn').addEventListener('click', function () {
      document.body.classList.toggle('sidebar-open');
    });
    document.getElementById('sidebarBackdrop').addEventListener('click', function () {
      document.body.classList.remove('sidebar-open');
    });
    function updateDeviceStatus(deviceId) {
      var status = document.getElementById('globalStatus');
      status.textContent = deviceId ? 'Live' : 'All devices';
      status.className = 'status-pill ' + (deviceId ? 'status-online' : 'status-offline');
    }
    window.addEventListener('devicechange', function (event) {
      updateDeviceStatus(event.detail.deviceId);
    });
    setDevice(selected);
    updateDeviceStatus(selected);
    return {
      setDevices: function (devices) { updateDeviceOptions(devices, localStorage.getItem('deviceId') || ''); },
      getDevice: function () { return localStorage.getItem('deviceId') || ''; },
      setStatus: function (text, online) {
        var status = document.getElementById('globalStatus');
        status.textContent = text;
        status.className = 'status-pill ' + (online ? 'status-online' : 'status-offline');
      }
    };
  }

  window.DashboardShell = {
    init: init,
    toast: toast,
    escapeHtml: escapeHtml,
    formatDateTime: formatDateTime,
    formatTime: formatTime,
    connectStomp: connectStomp,
    setDevice: setDevice,
    getStoredDevice: getStoredDevice
  };
})();
