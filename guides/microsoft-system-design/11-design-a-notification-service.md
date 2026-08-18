# 11. Design a Notification Service

**Key Terms:**

| Term | Full Name | What it does |
|------|-----------|--------------|
| **APNs** | Apple Push Notification service | Apple's push delivery to iOS devices |
| **FCM** | Firebase Cloud Messaging | Google's push delivery to Android/iOS |
| **SES** | Simple Email Service | AWS email sending service |
| **DLQ** | Dead Letter Queue | Queue for messages that failed processing |
| **2FA** | Two-Factor Authentication | Security requiring two verification methods |

## Problem Statement
Design a scalable notification system that delivers email, SMS, and push notifications to millions of users with high reliability and low latency.

## Clarifying Questions to Ask

1. **Channels**: Email, SMS, push, in-app, or all?
2. **Volume**: How many notifications per day? (1B?)
3. **Latency**: Real-time or batched acceptable?
4. **Priority**: Support urgent vs. normal notifications?
5. **Preferences**: User opt-in/out per channel?
6. **Templates**: Dynamic content or fixed templates?
7. **Tracking**: Track delivery, opens, clicks?

## Design Reasoning

> **Core philosophy**: Reliable, prioritized delivery across multiple channels where no notification is silently lost — using async processing with retry guarantees to handle billions of daily messages.

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Message Queue | Kafka | High throughput for 1B daily notifications; durable log enables replay | RabbitMQ — lower throughput ceiling, harder to scale horizontally |
| Queue Architecture | Priority queues (high/med/low) | 2FA codes and security alerts processed before marketing emails | Single FIFO queue — urgent notifications delayed behind bulk sends |
| Delivery Guarantee | At-least-once with idempotency | Simpler than exactly-once; duplicate detection handled at receiver | Exactly-once — requires distributed transactions, adds significant complexity |
| Failed Message Handling | Dead Letter Queue (DLQ) | Isolates poison messages for inspection and manual replay without blocking | Infinite retry — blocks queue progress, delays all other notifications |
| Template Rendering | Centralized service (Handlebars) | Reusable templates across channels; consistent branding, easy updates | Inline content per request — duplicated logic, hard to maintain at scale |
| Channel Providers | Multi-provider per channel (SendGrid + SES) | Automatic failover if primary provider has outage | Single provider — single point of failure for entire delivery channel |
| User Preferences | PostgreSQL + Redis cache | Structured preference data with fast cached lookup on every request | In-memory only — lost on restart, inconsistent across service instances |

## High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                   NOTIFICATION SERVICE                           │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION REQUEST                          │
│  {user_id, type, channel, template_id, data, priority}          │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION SERVICE                          │
│  1. Validate request                                             │
│  2. Check user preferences                                       │
│  3. Rate limit check                                             │
│  4. Route to priority queue                                      │
└─────────────────────────┬───────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │   HIGH   │    │  MEDIUM  │    │   LOW    │
    │ PRIORITY │    │ PRIORITY │    │ PRIORITY │
    │  QUEUE   │    │  QUEUE   │    │  QUEUE   │
    └────┬─────┘    └────┬─────┘    └────┬─────┘
         │               │               │
         └───────────────┼───────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    WORKER POOL                                   │
└─────────────────────────┬───────────────────────────────────────┘
                          │
    ┌─────────────────────┼─────────────────────┐
    ▼                     ▼                     ▼
┌─────────┐         ┌─────────┐          ┌─────────────┐
│  EMAIL  │         │   SMS   │          │    PUSH     │
│ HANDLER │         │ HANDLER │          │   HANDLER   │
│         │         │         │          │             │
│ SendGrid│         │ Twilio  │          │ APNs / FCM  │
└─────────┘         └─────────┘          └─────────────┘
```


## API Design

### Notification APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/notifications/send` | Send a notification |
| `POST` | `/api/v1/notifications/batch` | Send bulk notifications |
| `GET` | `/api/v1/notifications?userId={id}&limit=20` | Get user's notification history |
| `PUT` | `/api/v1/notifications/{notifId}/read` | Mark notification as read |
| `PUT` | `/api/v1/notifications/read-all` | Mark all as read |

### Preference APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/users/{userId}/preferences` | Get notification preferences |
| `PUT` | `/api/v1/users/{userId}/preferences` | Update notification preferences |
| `POST` | `/api/v1/users/{userId}/devices` | Register device for push notifications |
| `DELETE` | `/api/v1/users/{userId}/devices/{deviceId}` | Unregister device |

### Send Notification Request
```json
POST /api/v1/notifications/send
{
  "user_id": "user_123",
  "type": "order_shipped",
  "channels": ["push", "email", "in_app"],
  "priority": "high",
  "template_id": "tmpl_ship_01",
  "data": {
    "order_id": "ORD-456",
    "tracking_url": "https://..."
  }
}
```


## Key Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| API Gateway | Receive notification requests | REST API |
| Preference Service | User notification settings | PostgreSQL |
| Template Service | Render dynamic content | Handlebars/Mustache |
| Queue | Buffer and prioritize | Kafka / RabbitMQ |
| Workers | Process and send | Distributed workers |
| Email Provider | Send emails | SendGrid, SES |
| SMS Provider | Send SMS | Twilio, Nexmo |
| Push Provider | Mobile push | APNs, FCM |

## Notification Flow

```
1. SERVICE SENDS REQUEST:
   POST /notifications
   {
     "user_id": "123",
     "template_id": "order_shipped",
     "channels": ["email", "push"],
     "data": {"order_id": "456", "tracking": "ABC123"},
     "priority": "high"
   }

2. VALIDATION & ROUTING:
   - Check user exists and preferences
   - Check rate limits (max 10 emails/hour per user)
   - Enqueue to appropriate priority queue

3. WORKER PROCESSING:
   - Dequeue message
   - Render template with data
   - Call provider API
   - Handle response (success/failure)
   - Store delivery status

4. RETRY LOGIC:
   - Exponential backoff: 1s, 2s, 4s, 8s...
   - Max 5 retries
   - Dead letter queue for failures
```

## Database Schema

```sql
-- Notifications
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    template_id VARCHAR(50),
    channel VARCHAR(20),  -- email, sms, push
    status VARCHAR(20),   -- pending, sent, delivered, failed
    priority INT,
    data JSONB,
    created_at TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT
);

-- User Preferences
CREATE TABLE notification_preferences (
    user_id BIGINT,
    channel VARCHAR(20),
    enabled BOOLEAN DEFAULT true,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    PRIMARY KEY (user_id, channel)
);
```

## Scalability Considerations

- **Priority queues**: Process high-priority (2FA codes) before newsletters
- **Rate limiting**: Per-user and per-channel limits
- **Batching**: Batch digest emails (daily summary instead of per-event)
- **Multi-provider**: Fallback to secondary provider if primary fails
- **Async processing**: Never block the caller

## Trade-offs

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| Queue | Single queue | Priority queues | **Priority** - urgent first |
| Delivery | At-least-once | Exactly-once | **At-least-once** - simpler |
| Templates | Inline | Centralized | **Centralized** - reusable |
| Tracking | Basic (sent) | Full (opens) | **Full** - better analytics |

## Technologies Deep Dive

📚 **Learn more about the key technologies used in this design:**

| Technology | Role in This Design | Reference |
|------------|---------------------|-----------|
| Kafka | Event streaming for notification queue | [Tech Guide](technologies/kafka.md) |
| Redis | Rate limiting and user preference caching | [Tech Guide](technologies/redis.md) |
| WebSocket | Real-time in-app notifications | [Tech Guide](technologies/websocket.md) |
| SignalR | Managed push notification service | [Tech Guide](technologies/signalr.md) |

---

## How to Explain It Well

> **Opening**: "A notification service needs reliability and low latency for time-sensitive messages. I'll design for 1B daily notifications across email, SMS, and push."

> **Key talking points**:
> - "Priority queues ensure 2FA codes go out before marketing emails"
> - "User preferences checked before sending - respect quiet hours"
> - "Exponential backoff with dead letter queue for failures"
> - "Track delivery status and engagement (opens, clicks)"
> - "Multi-provider setup for failover (SendGrid → SES)"

---