/* PEN Field Visit PWA app-shell cache — update-safe release 2026.11.05.2. */
'use strict';
const CACHE_PREFIX = 'pen-field-visit-subapp-shell-';
const CACHE_NAME = CACHE_PREFIX + '2026.11.05.2';
const APP_BASE = new URL('./', self.registration.scope);
const APP_INDEX = new URL('index.html', APP_BASE).href;
const APP_MANIFEST = new URL('manifest.webmanifest', APP_BASE).href;
const APP_ICON = new URL('pen-field-visit-icon.svg', APP_BASE).href;
const APP_ICON_192 = new URL('icon-192.png', APP_BASE).href;
const APP_ICON_512 = new URL('icon-512.png', APP_BASE).href;
const APP_APPLE_ICON = new URL('apple-touch-icon.png', APP_BASE).href;
const CORE_ASSETS = [APP_INDEX, APP_MANIFEST, APP_ICON, APP_ICON_192, APP_ICON_512, APP_APPLE_ICON];

self.addEventListener('install', function (event) {
  event.waitUntil((async function () {
    const cache = await caches.open(CACHE_NAME);
    // Bypass the ordinary HTTP cache when preparing the new app shell.
    for (const url of CORE_ASSETS) {
      const request = new Request(url, { cache: 'reload' });
      const response = await fetch(request);
      if (!response || !response.ok) throw new Error('Unable to cache required Field Visit asset: ' + url);
      await cache.put(url, response);
    }
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', function (event) {
  event.waitUntil((async function () {
    const names = await caches.keys();
    await Promise.all(names.filter(function (name) {
      return name.indexOf(CACHE_PREFIX) === 0 && name !== CACHE_NAME;
    }).map(function (name) { return caches.delete(name); }));
    await self.clients.claim();
  })());
});

self.addEventListener('message', function (event) {
  if (event.data && event.data.type === 'SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('fetch', function (event) {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  const basePath = new URL(self.registration.scope).pathname;
  if (url.origin !== self.location.origin || url.pathname.indexOf(basePath) !== 0) return;
  if (url.pathname === new URL('sw.js', APP_BASE).pathname) return;

  // Always try the network first for navigations so an installed PWA does not
  // indefinitely display an old application shell after a production release.
  if (request.mode === 'navigate') {
    event.respondWith((async function () {
      try {
        const networkRequest = new Request(request, { cache: 'no-store' });
        const response = await fetch(networkRequest);
        if (response && response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then(function (cache) { return cache.put(APP_INDEX, copy); }).catch(function () {});
        }
        return response;
      } catch (_) {
        const cached = await caches.match(APP_INDEX);
        return cached || new Response('The PEN Field Visit App is not cached on this device yet. Open it online once before going offline.', {
          status: 503,
          headers: { 'Content-Type': 'text/plain; charset=utf-8' }
        });
      }
    })());
    return;
  }

  const core = [APP_INDEX, APP_MANIFEST, APP_ICON, APP_ICON_192, APP_ICON_512, APP_APPLE_ICON];
  if (core.indexOf(url.href.split('#')[0]) >= 0) {
    event.respondWith((async function () {
      try {
        const response = await fetch(new Request(request, { cache: 'no-store' }));
        if (response && response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then(function (cache) { return cache.put(request, copy); }).catch(function () {});
        }
        return response;
      } catch (_) {
        const cached = await caches.match(request);
        if (cached) return cached;
        throw new Error('Required Field Visit asset is not cached and the device is offline.');
      }
    })());
  }
});
