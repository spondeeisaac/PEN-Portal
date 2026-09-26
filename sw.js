const CACHE_NAME='PEN-FIELD-VISIT-2026.1';
const APP_URL=new URL('/gps-field-visit',self.location.origin).href;
self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.add(APP_URL)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
  event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE_NAME).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));
});
self.addEventListener('fetch',event=>{
  const req=event.request;
  if(req.method!=='GET')return;
  const url=new URL(req.url);
  if(url.origin!==self.location.origin)return;
  if(req.mode==='navigate' || url.pathname==='/gps-field-visit'){
    event.respondWith(fetch(req).then(res=>{
      if(res && res.ok){const copy=res.clone();caches.open(CACHE_NAME).then(c=>c.put(APP_URL,copy)).catch(()=>{});}
      return res;
    }).catch(()=>caches.match(APP_URL)));
    return;
  }
  if(url.pathname==='/sw.js'||url.pathname==='/gps-field-visit-manifest.webmanifest'){
    event.respondWith(caches.match(req).then(r=>r||fetch(req).then(res=>{if(res&&res.ok){const copy=res.clone();caches.open(CACHE_NAME).then(c=>c.put(req,copy)).catch(()=>{});}return res;})));
  }
});
