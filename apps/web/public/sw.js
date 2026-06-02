/* eslint-disable no-restricted-globals */

// ── 오프라인 캐싱 ──
// 라이브 시세/분석 데이터는 캐시하지 않고(외부 API = cross-origin = 패스),
// 앱 셸(정적 자산)만 캐시 우선으로 제공한다. 네비게이션은 네트워크 우선이며
// 오프라인이면 offline.html 폴백.
const CACHE_VERSION = 'v1';
const CACHE_NAME = `nowini-${CACHE_VERSION}`;
const OFFLINE_URL = '/offline.html';
const PRECACHE = [OFFLINE_URL, '/icon.svg', '/logo.svg'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      // 개별 자산 실패가 install 전체를 깨지 않도록 allSettled 사용.
      .then((cache) => Promise.allSettled(PRECACHE.map((u) => cache.add(u))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((k) => k.startsWith('nowini-') && k !== CACHE_NAME)
            .map((k) => caches.delete(k))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  // 외부(BE API·analytics 등 cross-origin)는 그대로 네트워크로 — 라이브 데이터 미캐시.
  if (url.origin !== self.location.origin) return;

  // 페이지 네비게이션: 네트워크 우선, 실패 시 오프라인 폴백.
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match(OFFLINE_URL)));
    return;
  }

  // 정적 자산: 캐시 우선 + 백그라운드 갱신(stale-while-revalidate).
  const isStatic =
    url.pathname.startsWith('/_next/static/') ||
    PRECACHE.includes(url.pathname) ||
    /\.(?:js|css|woff2?|png|svg|ico|webp)$/.test(url.pathname);

  if (isStatic) {
    event.respondWith(
      caches.open(CACHE_NAME).then(async (cache) => {
        const cached = await cache.match(request);
        const network = fetch(request)
          .then((resp) => {
            if (resp && resp.status === 200) cache.put(request, resp.clone());
            return resp;
          })
          .catch(() => cached);
        return cached || network;
      })
    );
  }
  // 그 외 same-origin GET(RSC·data 등)은 기본 네트워크 처리(미개입).
});

// ── Web Push ──
self.addEventListener('push', (event) => {
  if (!event.data) return;

  try {
    const data = event.data.json();
    const title = data.title || 'AI Stock Advisor';
    const options = {
      body: data.body || '',
      icon: data.icon || '/icon.svg',
      badge: '/icon.svg',
      data: data.url ? { url: data.url } : undefined,
    };
    event.waitUntil(self.registration.showNotification(title, options));
  } catch {
    // 파싱 실패 시 기본 알림
    event.waitUntil(
      self.registration.showNotification('AI Stock Advisor', {
        body: event.data.text(),
        icon: '/icon.svg',
      })
    );
  }
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = event.notification.data?.url || '/';
  event.waitUntil(clients.openWindow(url));
});
