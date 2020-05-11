self.addEventListener('push', function(event) {
  var json = event.data.json();
  const promiseChain = self.registration.showNotification(json.title, json.options);

  event.waitUntil(promiseChain);
});

self.addEventListener('notificationclick', function(event) {
  event.notification.close();
  event.waitUntil(clients.openWindow('https://livesdodia.com.br'));
});