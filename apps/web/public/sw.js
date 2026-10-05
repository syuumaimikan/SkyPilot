// SkyPilot service worker: displays Web Push flip alerts. It does nothing else (no caching,
// no background fetches) and alerts only link back to SkyPilot.
self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { title: "SkyPilot", body: event.data ? event.data.text() : "" };
  }
  const title = typeof data.title === "string" ? data.title : "SkyPilot";
  const url = typeof data.url === "string" && data.url.startsWith("/") ? data.url : "/market/flips";
  event.waitUntil(
    self.registration.showNotification(title, {
      body: typeof data.body === "string" ? data.body : "",
      tag: typeof data.tag === "string" ? data.tag : undefined,
      icon: "/icon.svg",
      data: { url },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = new URL(event.notification.data?.url ?? "/", self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((wins) => {
      for (const w of wins) {
        if (w.url === url && "focus" in w) return w.focus();
      }
      return self.clients.openWindow(url);
    }),
  );
});
