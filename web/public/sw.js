// Offline support.
//
// The app shell and the UF series are cached on install, so the page opens and
// every chart and date works with no connection at all. Requests to the data
// API go to the network first and are never cached: a stale price is worse
// than no price, and the bundled series already answers when the network does
// not.
const VERSION = "ufchile-v1";
const SHELL = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./uf_daily.txt",
  "./icons/icon.svg",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(VERSION).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return; // the data API handles its own failures

  event.respondWith(
    caches.match(request).then((hit) => {
      if (hit !== undefined) {
        // Refresh in the background so the next load is current.
        void fetch(request)
          .then((fresh) => caches.open(VERSION).then((c) => c.put(request, fresh.clone())))
          .catch(() => {});
        return hit;
      }
      return fetch(request)
        .then((fresh) => {
          const copy = fresh.clone();
          void caches.open(VERSION).then((c) => c.put(request, copy));
          return fresh;
        })
        .catch(() => caches.match("./index.html").then((page) => page ?? Response.error()));
    }),
  );
});
