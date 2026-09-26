const CACHE_NAME = 'PEN-FIELD-VISIT-2026.11';
const APP_URL = new URL('./', self.location.href).href;
const SW_URL = new URL('./sw.js', self.location.href).href;
const MANIFEST_URL = new URL('./manifest.webmanifest', self.location.href).href;

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll([APP_URL, MANIFEST_URL]))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then(response => {
          if (response && response.ok) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(APP_URL, copy)).catch(() => {});
          }
          return response;
        })
        .catch(() => caches.match(APP_URL))
    );
    return;
  }

  if (url.href === MANIFEST_URL || url.href === SW_URL) {
    event.respondWith(
      caches.match(url.href).then(cached => {
        if (cached) return cached;
        return fetch(request).then(response => {
          if (response && response.ok) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(url.href, copy)).catch(() => {});
          }
          return response;
        });
      })
    );
  }
});
