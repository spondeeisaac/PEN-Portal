/* PEN Portal PWA shell. Field Visit has a more specific worker in /field-visit/. */
'use strict';
const CACHE_PREFIX = 'pen-main-portal-shell-';
const CACHE_NAME = CACHE_PREFIX + '2026.4';
const ROOT_BASE = new URL('./', self.registration.scope);
const ROOT_PATH = ROOT_BASE.pathname;
const APP_INDEX = new URL('index.html', ROOT_BASE).href;
const APP_MANIFEST = new URL('manifest.webmanifest', ROOT_BASE).href;
const CORE_ASSETS = [
  APP_INDEX,
  APP_MANIFEST,
  new URL('icon-192.png', ROOT_BASE).href,
  new URL('icon-512.png', ROOT_BASE).href,
  new URL('apple-touch-icon.png', ROOT_BASE).href
];
const FIELD_VISIT_PATH = new URL('field-visit/', ROOT_BASE).pathname;
const LEGACY_FIELD_VISIT_CACHE_PREFIX = 'pen-field-visit-shell-';

self.addEventListener('install', function (event) {
  event.waitUntil(caches.open(CACHE_NAME)
    .then(function (cache) { return cache.addAll(CORE_ASSETS); })
    .then(function () { return self.skipWaiting(); }));
});

self.addEventListener('activate', function (event) {
  event.waitUntil(caches.keys().then(function (names) {
    return Promise.all(names.filter(function (name) {
      return (name.indexOf(CACHE_PREFIX) === 0 && name !== CACHE_NAME) ||
        name.indexOf(LEGACY_FIELD_VISIT_CACHE_PREFIX) === 0;
    }).map(function (name) { return caches.delete(name); }));
  }).then(function () { return self.clients.claim(); }));
});

self.addEventListener('fetch', function (event) {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || url.pathname.indexOf(ROOT_PATH) !== 0) return;
  // Do not intercept the standalone offline Field Visit application or its assets.
  if (url.pathname.indexOf(FIELD_VISIT_PATH) === 0) return;
  if (url.pathname === new URL('sw.js', ROOT_BASE).pathname) return;

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then(function (response) {
      if (response && response.ok &&
          (url.pathname === ROOT_PATH || url.pathname === new URL('index.html', ROOT_BASE).pathname)) {
        const copy = response.clone();
        caches.open(CACHE_NAME).then(function (cache) { return cache.put(APP_INDEX, copy); }).catch(function () {});
      }
      return response;
    }).catch(function () {
      return caches.match(APP_INDEX).then(function (cached) {
        return cached || new Response('PEN Portal needs an internet connection. Use the Field Visit App for prepared offline field work.', {
          status: 503, headers: { 'Content-Type': 'text/plain; charset=utf-8' }
        });
      });
    }));
    return;
  }

  if (CORE_ASSETS.indexOf(url.href.split('#')[0]) >= 0) {
    event.respondWith(caches.match(request).then(function (cached) {
      if (cached) return cached;
      return fetch(request).then(function (response) {
        if (response && response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then(function (cache) { return cache.put(request, copy); }).catch(function () {});
        }
        return response;
      });
    }));
  }
});
