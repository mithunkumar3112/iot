// Service Worker for Offline File Access
const CACHE_NAME = 'iot-monitor-v4';
const RUNTIME_CACHE = 'iot-runtime-cache-v4';

// Install event - cache essential files
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      return cache.addAll([
        '/',
        '/index.html',
        '/login.html',
        '/dashboard.html',
        '/activity-timeline.html',
        '/files-explorer.html',
        '/processes.html',
        '/screen-monitor.html',
        '/clipboard.html',
        '/security-alerts.html',
        '/session-history.html',
        '/pair.html',
        '/dashboard-shell.css',
        '/dashboard-shell.js',
        '/style.css',
        '/app.js'
      ]).catch(err => console.log('Cache failed:', err));
    })
  );
  self.skipWaiting();
});

// Activate event - clean up old caches
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(cacheName => {
          if (cacheName !== CACHE_NAME && cacheName !== RUNTIME_CACHE) {
            return caches.delete(cacheName);
          }
        })
      );
    })
  );
  self.clients.claim();
});

// Fetch event - network first, fallback to cache
self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);

  // Skip non-GET requests
  if (request.method !== 'GET') {
    return;
  }

  // For API calls - network first, then cache
  if (url.pathname.includes('/files/') || url.pathname.includes('/metrics/')) {
    // Use a cache key that ignores Authorization / other transient headers so
    // cached responses can be served when the app is offline (e.g. when open
    // via navigation or window.open which doesn't include auth headers).
    const cacheKey = new Request(url.toString(), { method: request.method });

    event.respondWith(
      fetch(request)
        .then(response => {
          if (!response || response.status !== 200) {
            return caches.match(cacheKey);
          }
          // Cache successful API responses
          const clonedResponse = response.clone();
          caches.open(RUNTIME_CACHE).then(cache => {
            cache.put(cacheKey, clonedResponse);
          });
          return response;
        })
        .catch(() => {
          return caches.match(cacheKey);
        })
    );
    return;
  }

  // HTML pages must be network first so UI fixes are visible immediately.
  if (request.mode === 'navigate' || url.pathname.endsWith('.html')) {
    event.respondWith(
      fetch(request)
        .then(response => {
          if (!response || response.status !== 200) {
            return caches.match(request);
          }
          const clonedResponse = response.clone();
          caches.open(CACHE_NAME).then(cache => {
            cache.put(request, clonedResponse);
          });
          return response;
        })
        .catch(() => caches.match(request) || new Response('Offline - No cached version available', {
          status: 503,
          statusText: 'Service Unavailable'
        }))
    );
    return;
  }

  // For other static files - cache first, then network
  event.respondWith(
    caches.match(request).then(cachedResponse => {
      if (cachedResponse) {
        return cachedResponse;
      }
      return fetch(request).then(response => {
        if (!response || response.status !== 200) {
          return response;
        }
        const clonedResponse = response.clone();
        caches.open(CACHE_NAME).then(cache => {
          cache.put(request, clonedResponse);
        });
        return response;
      });
    })
    .catch(() => {
      return new Response('Offline - No cached version available', {
        status: 503,
        statusText: 'Service Unavailable'
      });
    })
  );
});

// Handle background sync - sync file changes when back online
self.addEventListener('sync', event => {
  if (event.tag === 'sync-files') {
    event.waitUntil(syncFiles());
  }
});

async function syncFiles() {
  try {
    const db = await openDB();
    const pendingChanges = await getPendingChanges(db);
    
    for (const change of pendingChanges) {
      try {
        await fetch(change.url, {
          method: change.method,
          body: change.body,
          headers: change.headers
        });
        await markSynced(db, change.id);
      } catch (err) {
        console.log('Sync failed:', err);
      }
    }
  } catch (err) {
    console.log('Sync error:', err);
  }
}

// IndexedDB helpers
function openDB() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open('IoTMonitor', 1);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
    request.onupgradeneeded = (event) => {
      const db = event.target.result;
      if (!db.objectStoreNames.contains('pendingSync')) {
        db.createObjectStore('pendingSync', { keyPath: 'id', autoIncrement: true });
      }
      if (!db.objectStoreNames.contains('cachedFiles')) {
        db.createObjectStore('cachedFiles', { keyPath: 'path' });
      }
    };
  });
}

function getPendingChanges(db) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(['pendingSync'], 'readonly');
    const store = transaction.objectStore('pendingSync');
    const request = store.getAll();
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
}

function markSynced(db, id) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(['pendingSync'], 'readwrite');
    const store = transaction.objectStore('pendingSync');
    const request = store.delete(id);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve();
  });
}
