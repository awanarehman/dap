// Lets the DAP open without internet: the page is fetched fresh when possible, and the last copy is used when not.
// Your plan itself is never stored here; it lives in the browser's own storage and your data folder.
const CACHE = 'dap-page-v1';
const PAGE = ['./', './index.html'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(PAGE)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const req = e.request, url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== location.origin) return;          // Google sign-in and Drive always go straight to Google
  e.respondWith((async () => {
    const cache = await caches.open(CACHE);
    try {
      const fresh = await fetch(req, { cache: 'no-cache' });
      if (fresh.ok && (req.mode === 'navigate' || PAGE.some(p => url.pathname.endsWith(p.replace('./', '/'))))) cache.put(req.mode === 'navigate' ? './' : req, fresh.clone());
      return fresh;
    } catch (err) {
      return (await cache.match(req, { ignoreSearch: true })) || (await cache.match('./')) || Response.error();
    }
  })());
});
