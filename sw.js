/* PEN Field Visit PWA app-shell cache. Scope is the GitHub Pages /PEN-Portal/ path. */
'use strict';
const CACHE_PREFIX = 'pen-field-visit-shell-';
const CACHE_NAME = CACHE_PREFIX + '2026.4';
const APP_BASE = new URL('./', self.registration.scope);
const APP_INDEX = new URL('index.html', APP_BASE).href;
const APP_MANIFEST = new URL('gps-field-visit-manifest.webmanifest', APP_BASE).href;
const APP_ICON = new URL('pen-field-visit-icon.svg', APP_BASE).href;
const CORE_ASSETS = [APP_INDEX, APP_MANIFEST, APP_ICON];

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(function (cache) { return cache.addAll(CORE_ASSETS); })
      .then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys()
      .then(function (names) {
        return Promise.all(names.filter(function (name) {
          return name.indexOf(CACHE_PREFIX) === 0 && name !== CACHE_NAME;
        }).map(function (name) { return caches.delete(name); }));
      })
      .then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (event) {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  const basePath = new URL(self.registration.scope).pathname;
  if (url.origin !== self.location.origin || url.pathname.indexOf(basePath) !== 0) return;
  if (url.pathname === new URL('sw.js', APP_BASE).pathname) return;

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).then(function (response) {
        if (response && response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then(function (cache) { return cache.put(APP_INDEX, copy); }).catch(function () {});
        }
        return response;
      }).catch(function () {
        return caches.match(APP_INDEX).then(function (cached) {
          return cached || new Response('The PEN Field Visit App is not cached on this device yet. Open it online once before going offline.', {
            status: 503,
            headers: { 'Content-Type': 'text/plain; charset=utf-8' }
          });
        });
      })
    );
    return;
  }

  if ([APP_INDEX, APP_MANIFEST, APP_ICON].indexOf(url.href.split('#')[0]) >= 0) {
    event.respondWith(
      caches.match(request).then(function (cached) {
        if (cached) return cached;
        return fetch(request).then(function (response) {
          if (response && response.ok) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then(function (cache) { return cache.put(request, copy); }).catch(function () {});
          }
          return response;
        });
      })
    );
  }
});
