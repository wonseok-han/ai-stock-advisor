/* eslint-disable no-restricted-globals */

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
