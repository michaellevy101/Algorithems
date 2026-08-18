# Push Notifications (Enhancement)

[← Back to Overview](./README.md) | [Chat](./chat.md) | [Presence →](./presence.md) | [Technologies](./technologies.md)

> 📚 **Key technologies:** Azure Notification Hubs (APNs/FCM/WNS abstraction), Service Workers for web push.

> **This is an optional enhancement.** The core system works without push notifications.
> Add this when: Mobile-first apps, consumer chat (WhatsApp-like expectations), time-sensitive messages.
> Skip this when: Internal enterprise tools, desktop-first apps where users keep the app running.

## High-Level Architecture

```
                           ┌─────────────────────────────────────┐
                           │          AZURE FRONT DOOR           │
                           │         (1. All requests enter)     │
                           └──────────────────┬──────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
                    ▼                         ▼                         ▼
         ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
         │   AUTH SERVICE   │    │ NOTIFICATION SVC │    │  OTHER SERVICES  │
         │  (Azure AD B2C)  │    │      (AKS)       │    │  (Chat, Video)   │
         │                  │    │                  │    │                  │
         │ • JWT validation │◀───│ • Device tokens  │    │ • Trigger notif  │
         │ • Token issuance │ 2. │ • Push routing   │    │   via Event Hubs │
         └──────────────────┘    └────────┬─────────┘    └──────────────────┘
                                          │
                         ┌────────────────┴────────────────┐
                         │                                 │
                         ▼ 3.                              ▼ 4.
              ┌──────────────────┐              ┌──────────────────┐
              │ NOTIFICATION HUBS│              │ PLATFORM SERVICES│
              │ (Token registry) │─────────────▶│ APNs/FCM/WNS     │
              └──────────────────┘              └────────┬─────────┘
                                                        │
                                                        ▼ 5.
                                               ┌──────────────────┐
                                               │  📱 User Device  │
                                               └──────────────────┘
```

| Step | From | To | Action | Sync/Async |
|------|------|-----|--------|------------|
| 1 | Event Hubs | Notification Worker | Consume message event | ASYNC |
| 2 | Worker | Auth Service | Validate sender, check permissions | SYNC |
| 3 | Worker | Notification Hubs | Send push request | ASYNC |
| 4 | Notification Hubs | APNs/FCM/WNS | Forward to platform | ASYNC |
| 5 | Platform | Device | Deliver notification | ASYNC |

## Overview

When a user is **offline** (app closed, phone locked, no WebSocket), we can't push via SignalR. Instead, we use **platform push notifications** via Azure Notification Hubs.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PUSH NOTIFICATION FLOW                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  User offline (no WebSocket)                                                 │
│                                                                              │
│  ┌────────────────┐     ┌─────────────────────┐     ┌──────────────────┐    │
│  │ Notification   │────►│ Azure Notification  │────►│ Platform Service │    │
│  │ Worker         │     │ Hubs                │     │ (APNs/FCM/WNS)   │    │
│  └────────────────┘     └─────────────────────┘     └────────┬─────────┘    │
│                                                              │               │
│                                                              ▼               │
│                                                     ┌──────────────────┐    │
│                                                     │ 📱 User's Device │    │
│                                                     │ "New message     │    │
│                                                     │  from Alice"     │    │
│                                                     └──────────────────┘    │
│                                                                              │
│  User taps → App opens → Fetches full message from Cosmos DB                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## How Push Notifications Work

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    HOW PUSH NOTIFICATIONS WORK                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  DEVICE REGISTRATION (one-time, on app install):                            │
│  ┌──────────────┐     1. Request token      ┌────────────────────────────┐  │
│  │ Teams App    │ ─────────────────────────►│ Platform Service           │  │
│  │ (iOS/Android)│                           │ APNs (Apple) / FCM (Google)│  │
│  └──────────────┘◄───────────────────────── └────────────────────────────┘  │
│         │              2. Device token                                       │
│         │                 (unique ID for this device)                        │
│         │                                                                    │
│         │           3. Register token                                        │
│         ▼              with user tags                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Azure Notification Hubs                                             │    │
│  │  Stores: { userId: "user_123", token: "abc...", platform: "ios" }   │    │
│  │  Tags: ["user:user_123", "team:engineering", "channel:general"]      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SENDING NOTIFICATION (when user is offline):                               │
│                                                                              │
│  ┌──────────────┐     1. Send to user_123   ┌────────────────────────────┐  │
│  │ Notification │ ─────────────────────────►│ Azure Notification Hubs    │  │
│  │ Worker       │     Tag: "user:user_123"  │                            │  │
│  │ (Azure Func) │                           │ 2. Lookup token for user   │  │
│  └──────────────┘                           │    → "abc...", platform iOS│  │
│                                             └─────────────┬──────────────┘  │
│                                                           │                  │
│                                             3. Forward to APNs               │
│                                                           │                  │
│                                                           ▼                  │
│                                             ┌────────────────────────────┐  │
│                                             │ Apple Push Notification    │  │
│                                             │ Service (APNs)             │  │
│                                             └─────────────┬──────────────┘  │
│                                                           │                  │
│                                             4. Deliver to device             │
│                                                           │                  │
│                                                           ▼                  │
│                                             ┌────────────────────────────┐  │
│                                             │ 📱 User's iPhone           │  │
│                                             │ ┌────────────────────────┐ │  │
│                                             │ │ 🔔 Teams               │ │  │
│                                             │ │ Alice: "Hey, meeting   │ │  │
│                                             │ │ at 3pm"                │ │  │
│                                             │ └────────────────────────┘ │  │
│                                             └────────────────────────────┘  │
│                                                                              │
│  User taps notification → App opens → Fetches full messages from Cosmos DB  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Platform Push Services

| Platform | Push Service | How it works |
|----------|--------------|--------------|
| **iOS** | APNs (Apple Push Notification) | Works even when app killed |
| **Android** | FCM (Firebase Cloud Messaging) | Works even when app killed |
| **Windows Desktop** | WNS (Windows Notification Service) | Toast notifications via Action Center |
| **Mac Desktop** | APNs (same as iOS) | Native macOS notifications |
| **Web Browser** | Web Push API + Service Worker | Works if browser open OR user allowed background |

## Web Push + Service Worker

Web push is more complex than mobile push because browsers weren't designed for background notifications.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WEB PUSH + SERVICE WORKER EXPLAINED                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  WHAT IS A SERVICE WORKER?                                                   │
│  A JavaScript file that runs in the background, SEPARATE from the web page. │
│  It stays alive even when the browser tab is closed (but browser running).  │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  STEP 1: REGISTRATION (one-time, when user visits Teams web)                 │
│                                                                              │
│  ┌────────────────┐    1. Register SW         ┌────────────────────────┐    │
│  │ Teams Web App  │ ─────────────────────────►│ Browser                │    │
│  │ (JavaScript)   │                           │ Installs service-worker│    │
│  └────────────────┘                           │ .js in background      │    │
│         │                                     └────────────────────────┘    │
│         │         2. Request permission                                      │
│         │            "Teams wants to send notifications"                     │
│         ▼                     ┌─────────┐                                   │
│  ┌────────────────┐           │ [Allow] │ ← User clicks                     │
│  │ Browser Prompt │           │ [Block] │                                   │
│  └────────────────┘           └─────────┘                                   │
│         │                                                                    │
│         │ 3. If allowed, get Push Subscription                               │
│         ▼                                                                    │
│  ┌────────────────┐    4. Subscribe           ┌────────────────────────┐    │
│  │ Teams Web App  │ ─────────────────────────►│ Browser Push Service   │    │
│  └────────────────┘                           │ (Chrome: FCM,          │    │
│         │                                     │  Firefox: Mozilla Push)│    │
│         │◄─────────────────────────────────── └────────────────────────┘    │
│         │    5. Returns: endpoint URL + keys                                 │
│         │       endpoint: "https://fcm.googleapis.com/fcm/send/abc123..."   │
│         │                                                                    │
│         │    6. Send subscription to our server                              │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Azure Notification Hubs                                             │    │
│  │  Stores: { userId: "user_123", endpoint: "https://fcm...", keys }   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  STEP 2: RECEIVING PUSH (when browser is closed)                             │
│                                                                              │
│  ┌────────────────┐   1. New message for user_123                           │
│  │ Notification   │ ─────────────────────────►┌────────────────────────┐    │
│  │ Worker         │                           │ Notification Hubs      │    │
│  └────────────────┘                           └───────────┬────────────┘    │
│                                                           │                  │
│                                               2. POST to endpoint URL        │
│                                                  (encrypted payload)         │
│                                                           │                  │
│                                                           ▼                  │
│                                               ┌────────────────────────┐    │
│                                               │ Browser Push Service   │    │
│                                               │ (FCM / Mozilla)        │    │
│                                               └───────────┬────────────┘    │
│                                                           │                  │
│                                               3. Wake up Service Worker      │
│                                                  (even if browser "closed")  │
│                                                           │                  │
│                                                           ▼                  │
│  USER'S COMPUTER:                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Browser Process (minimal, in background)                            │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │  Service Worker (service-worker.js)                            │  │    │
│  │  │                                                                │  │    │
│  │  │  self.addEventListener('push', event => {                      │  │    │
│  │  │    const data = event.data.json();                             │  │    │
│  │  │    self.registration.showNotification(data.title, {            │  │    │
│  │  │      body: "Alice: Hey, meeting at 3pm",                       │  │    │
│  │  │      icon: "/teams-icon.png"                                   │  │    │
│  │  │    });                                                         │  │    │
│  │  │  });                                                           │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                           │                  │
│                                               4. Show OS notification        │
│                                                           │                  │
│                                                           ▼                  │
│                                               ┌────────────────────────┐    │
│                                               │ 🔔 Teams Web           │    │
│                                               │ Alice: "Hey, meeting   │    │
│                                               │ at 3pm"                │    │
│                                               └────────────────────────┘    │
│                                                           │                  │
│                                               5. User clicks notification    │
│                                                  → Browser opens Teams       │
│                                                  → Fetches from Cosmos DB    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Limitations of Web Push:**

| Limitation | Impact |
|------------|--------|
| User must grant permission | Many users click "Block" |
| Browser must be installed | Obvious, but matters |
| Some browsers need to be running | Safari on Mac needs browser running |
| Doesn't work in Incognito | No Service Worker persistence |
| iOS Safari: No Web Push (until iOS 16.4+) | Major gap for iPhone web users |

## Notification Scenarios by Platform

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        WEB BROWSER NOTIFICATIONS                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SCENARIO 1: Browser tab open (background tab)                               │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  SignalR WebSocket is STILL CONNECTED                                  │ │
│  │  → Message delivered via SignalR (same as "online")                    │ │
│  │  → Browser shows notification via Notification API                     │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  SCENARIO 2: Browser closed, but Service Worker registered                   │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  User previously granted notification permission                       │ │
│  │  → Web Push via Service Worker (browser wakes up in background)        │ │
│  │  → Shows OS-level notification                                         │ │
│  │  → User clicks → Browser opens → Fetches from Cosmos DB                │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  SCENARIO 3: Browser closed, no Service Worker / permission denied           │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  ❌ Cannot push notification                                           │ │
│  │  → Message stored in Cosmos DB                                         │ │
│  │  → User sees it when they open Teams next time (pull on reconnect)     │ │
│  │  → May receive notification on mobile device instead                   │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                        DESKTOP APP NOTIFICATIONS                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SCENARIO 1: App running (foreground or minimized)                           │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  SignalR WebSocket is CONNECTED                                        │ │
│  │  → Message delivered via SignalR                                       │ │
│  │  → App shows native OS notification (toast on Windows, banner on Mac)  │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  SCENARIO 2: App fully closed (quit)                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  Windows: WNS can wake app / show notification via Action Center       │ │
│  │  Mac: APNs delivers to Notification Center                             │ │
│  │  → User clicks → App opens → Fetches from Cosmos DB                    │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Delivery Summary by State

| User State | Delivery Method | Reliability |
|------------|-----------------|-------------|
| App open (any platform) | SignalR WebSocket | ✅ Instant |
| App closed - Mobile | APNs/FCM push | ✅ Reliable |
| App closed - Windows | WNS push | ✅ Reliable |
| App closed - Mac | APNs push | ✅ Reliable |
| Browser closed + Service Worker | Web Push | ⚠️ Depends on browser/OS |
| Browser closed, no permission | **No push** - pull on reconnect | ❌ User must open app |
| **Device shut down** | **No push possible** | ❌ Pull on next boot |

## Device Shutdown Scenario

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DEVICE COMPLETELY SHUT DOWN                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  NOTHING can be pushed - no process running to receive it.                   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Messages while shut down:                                           │    │
│  │                                                                      │    │
│  │  1. Stored in Cosmos DB (always - this is our source of truth)      │    │
│  │  2. Push attempted → fails (device unreachable)                      │    │
│  │  3. Push services may QUEUE for limited time:                        │    │
│  │     • APNs: Stores latest notification, delivers on wake             │    │
│  │     • FCM: Stores up to 100 messages for 28 days                     │    │
│  │     • WNS: Stores notifications briefly                              │    │
│  │  4. When device boots → queued push delivered (if not expired)       │    │
│  │  5. App opens → fetches ALL missed messages from Cosmos DB           │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  KEY INSIGHT: Push notifications are just ALERTS, not the messages.         │
│  The real messages are ALWAYS in Cosmos DB. Push is best-effort.            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  User shuts down laptop Friday evening                               │    │
│  │  50 messages arrive over weekend                                     │    │
│  │  User boots laptop Monday morning                                    │    │
│  │                                                                      │    │
│  │  → Maybe 1 push notification shows (APNs keeps latest only)          │    │
│  │  → User opens Teams                                                  │    │
│  │  → App sends lastSeenTimestamp to server                             │    │
│  │  → Server returns all 50 messages from Cosmos DB                     │    │
│  │  → User sees everything they missed                                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  MULTI-DEVICE helps: If laptop is off but phone is on, phone gets push.    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Key Takeaways

- Push notification is just a **"wake up" alert** - it contains minimal data (sender, preview)
- Full message content is fetched from Cosmos DB when app opens
- **Cosmos DB is the source of truth** - push is best-effort delivery
- Device tokens are managed by Apple/Google/Microsoft, not us
- Notification Hubs abstracts the complexity of APNs/FCM/WNS APIs
- **Web is the least reliable** for offline push - encourage users to install desktop/mobile app
- **Multi-device**: If one device is off, another may receive the push

## Why Notification Hubs (not direct APNs/FCM)?

| Without Notification Hubs | With Notification Hubs |
|---------------------------|------------------------|
| Integrate 3+ APIs (APNs, FCM, WNS, Web Push) separately | Single unified API |
| Manage device tokens yourself | Automatic token management |
| Handle token expiry/refresh | Handles automatically |
| Build retry/failover logic | Built-in reliability |
| Scale infrastructure yourself | Scales to millions |

---

[← Back to Overview](./README.md)
