/*
 * Service worker for the Snip dashboard.
 *
 * Caches the app shell so the dashboard loads instantly and still renders while
 * the free-tier instance is waking from cold start. It deliberately does NOT
 * touch the API or short-code redirects — only the static shell:
 *   - navigations to "/" are network-first with an offline fallback to the cached shell;
 *   - short-code navigations (/{code}) and /api, /actuator are never intercepted,
 *     so redirects and data calls always hit the network.
 */
const CACHE = "snip-shell-v1";
const SHELL = ["/", "/styles.css", "/app.js", "/site.webmanifest", "/icon.svg"];

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting())
    );
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener("fetch", (event) => {
    const req = event.request;
    const url = new URL(req.url);

    if (req.method !== "GET" || url.origin !== location.origin) return;

    // Root navigation: network-first (fresh deploys show immediately), fall back
    // to the cached shell when offline / still waking up. Any other navigation
    // (e.g. a /{code} short link) is left untouched so the 302 redirect works.
    if (req.mode === "navigate") {
        if (url.pathname === "/") {
            event.respondWith(fetch(req).catch(() => caches.match("/")));
        }
        return;
    }

    // Static shell assets: cache-first for instant loads.
    if (SHELL.includes(url.pathname) || req.destination === "script" || req.destination === "style") {
        event.respondWith(caches.match(req).then((cached) => cached || fetch(req)));
    }
});
