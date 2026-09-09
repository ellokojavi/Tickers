// Offline support.
//
// The app shell and the UF series are cached on install, so the page opens and
// every chart and date works with no connection at all. Requests to the data
// API go to the network first and are never cached: a stale price is worse
// than no price, and the bundled series already answers when the network does
// not.
//
// The page itself is the exception to caching first. Its <script> tag names a
// content-hashed bundle, so a cached page from an older deploy asks for a file
// that no longer exists and renders nothing at all. The page therefore goes to
// the network first and falls back to the cache only when there is no network,
// which is the case the cache is actually for.
const VERSION = "ufchile-v2";
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

/** A request for the document itself, however the browser phrases it. */
const isPage = (request, url) =>
  request.mode === "navigate" ||
  url.pathname.endsWith("/") ||
  url.pathname.endsWith("/index.html");

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return; // the data API handles its own failures

  if (isPage(request, url)) {
    event.respondWith(
      fetch(request)
        .then((fresh) => {
          // Only a real page is worth keeping. Caching an error page would
          // leave the offline fallback showing that error forever.
          if (fresh.ok) {
            const copy = fresh.clone();
            void caches.open(VERSION).then((c) => c.put("./index.html", copy));
          }
          return fresh;
        })
        .catch(() => caches.match("./index.html").then((page) => page ?? Response.error())),
    );
    return;
  }

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
