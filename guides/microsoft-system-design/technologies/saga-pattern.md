# Saga Pattern

## What It Is
The Saga pattern is a way to manage distributed transactions across multiple services without using traditional two-phase commit (2PC). Each step has a compensating action that undoes its effect if a later step fails.

## The Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         DISTRIBUTED TRANSACTION PROBLEM                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

E-commerce Checkout:
  1. Inventory Service: Reserve items
  2. Payment Service: Charge card
  3. Shipping Service: Create shipment
  4. Notification Service: Send confirmation

PROBLEM: What if Payment succeeds but Shipping fails?
  - Can't use database transaction (different services/databases)
  - 2PC is slow and creates tight coupling
  - Need to undo Inventory and Payment
```

## How Saga Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SAGA: SEQUENCE OF LOCAL TRANSACTIONS                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Each step: T1 → T2 → T3 → T4 (local transactions)
Each step has: C1, C2, C3, C4 (compensating transactions)

SUCCESS PATH:
  T1 (Reserve Inventory) ✓
    → T2 (Charge Payment) ✓
      → T3 (Create Shipment) ✓
        → T4 (Send Notification) ✓
          → COMPLETE

FAILURE PATH (T3 fails):
  T1 (Reserve Inventory) ✓
    → T2 (Charge Payment) ✓
      → T3 (Create Shipment) ✗ FAILED
        → C2 (Refund Payment)     ← Compensate in reverse order
          → C1 (Release Inventory)
            → ROLLED BACK
```

## Orchestration vs Choreography

### Orchestration (Centralized)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         ORCHESTRATION                                                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘

           ┌───────────────────────────────────────────────────────────────┐
           │                    SAGA ORCHESTRATOR                           │
           │  (Knows the entire workflow, coordinates all steps)           │
           └───────────────────────────────────┬───────────────────────────┘
                                               │
         ┌─────────────────┬───────────────────┼───────────────────┬─────────────────┐
         │                 │                   │                   │                 │
         ▼                 ▼                   ▼                   ▼                 ▼
   ┌───────────┐    ┌───────────┐       ┌───────────┐       ┌───────────┐    ┌───────────┐
   │ Inventory │    │  Payment  │       │ Shipping  │       │   Email   │    │  Loyalty  │
   │  Service  │    │  Service  │       │  Service  │       │  Service  │    │  Service  │
   └───────────┘    └───────────┘       └───────────┘       └───────────┘    └───────────┘

Orchestrator:
  1. Call Inventory.reserve()
  2. If success → Call Payment.charge()
  3. If success → Call Shipping.create()
  4. If any fails → Call compensations in reverse

PROS: 
  + Easy to understand and debug
  + Centralized logic
  + Easy to add new steps

CONS:
  - Single point of failure
  - Orchestrator can become complex
  - Tight coupling to orchestrator
```

### Choreography (Decentralized)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CHOREOGRAPHY                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Services communicate via events (no central coordinator):

  ┌───────────┐     ┌───────────┐     ┌───────────┐     ┌───────────┐
  │ Inventory │────▶│  Payment  │────▶│ Shipping  │────▶│   Email   │
  │  Service  │     │  Service  │     │  Service  │     │  Service  │
  └───────────┘     └───────────┘     └───────────┘     └───────────┘
        │                 │                 │                 │
        │                 │                 │                 │
        ▼                 ▼                 ▼                 ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │                      MESSAGE BROKER (Kafka)                      │
  │                                                                  │
  │  Events: InventoryReserved, PaymentCharged, ShipmentCreated...  │
  └─────────────────────────────────────────────────────────────────┘

Flow:
  1. Order Service publishes "OrderCreated"
  2. Inventory listens, reserves, publishes "InventoryReserved"
  3. Payment listens, charges, publishes "PaymentCharged"
  4. Shipping listens, creates, publishes "ShipmentCreated"
  5. Email listens, sends confirmation

Failure:
  1. Shipping publishes "ShipmentFailed"
  2. Payment listens, refunds, publishes "PaymentRefunded"
  3. Inventory listens, releases stock

PROS:
  + No single point of failure
  + Services are loosely coupled
  + Each service owns its logic

CONS:
  - Hard to understand full flow
  - Debugging is difficult
  - Risk of cyclic dependencies
```

## Implementation Example (Orchestration)

```java
public class OrderSaga {
    
    public void execute(Order order) {
        SagaState state = new SagaState(order);
        
        try {
            // Step 1: Reserve Inventory
            state.inventoryReservationId = inventoryService.reserve(order.items);
            state.addCompensation(() -> inventoryService.release(state.inventoryReservationId));
            
            // Step 2: Charge Payment
            state.paymentId = paymentService.charge(order.customerId, order.total);
            state.addCompensation(() -> paymentService.refund(state.paymentId));
            
            // Step 3: Create Shipment
            state.shipmentId = shippingService.createShipment(order);
            state.addCompensation(() -> shippingService.cancel(state.shipmentId));
            
            // Step 4: Send Notification (no compensation needed)
            notificationService.sendOrderConfirmation(order);
            
            // All steps succeeded
            orderService.markCompleted(order.id);
            
        } catch (Exception e) {
            // Execute compensations in reverse order
            state.compensate();
            orderService.markFailed(order.id, e.getMessage());
            throw new SagaFailedException(e);
        }
    }
}

class SagaState {
    private Stack<Runnable> compensations = new Stack<>();
    
    public void addCompensation(Runnable compensation) {
        compensations.push(compensation);
    }
    
    public void compensate() {
        while (!compensations.isEmpty()) {
            try {
                compensations.pop().run();
            } catch (Exception e) {
                log.error("Compensation failed", e);
                // Log and continue with other compensations
            }
        }
    }
}
```

## Saga State Machine

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SAGA STATE MACHINE                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                          ┌─────────────────┐
                          │     STARTED     │
                          └────────┬────────┘
                                   │
                                   ▼
                     ┌─────────────────────────┐
                     │  INVENTORY_RESERVING    │
                     └─────────────┬───────────┘
                           success │ failure
                    ┌──────────────┴──────────────┐
                    ▼                              ▼
       ┌────────────────────┐         ┌────────────────────┐
       │  PAYMENT_CHARGING  │         │      FAILED        │
       └─────────┬──────────┘         └────────────────────┘
          success│ failure
       ┌─────────┴─────────┐
       ▼                   ▼
┌──────────────┐  ┌────────────────────┐
│SHIPMENT_     │  │PAYMENT_REFUNDING   │
│CREATING      │  └─────────┬──────────┘
└──────┬───────┘            │
  success│ failure          ▼
  ┌──────┴──────┐  ┌────────────────────┐
  ▼             ▼  │INVENTORY_RELEASING │
┌─────┐ ┌───────────┴──────────────────┐
│DONE │ │PAYMENT_REFUNDING             │
└─────┘ └──────────────────────────────┘

Store state in database for recovery:
  CREATE TABLE saga_state (
      saga_id UUID PRIMARY KEY,
      order_id BIGINT,
      current_state VARCHAR(50),
      inventory_reservation_id BIGINT,
      payment_id BIGINT,
      shipment_id BIGINT,
      created_at TIMESTAMP,
      updated_at TIMESTAMP
  );
```

## Handling Edge Cases

### Idempotency
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Each step must be idempotent (safe to retry)                                           │
│                                                                                          │
│  Payment Service:                                                                       │
│    POST /charge                                                                         │
│    {                                                                                    │
│      "idempotency_key": "order_123_payment",  ← Unique per operation                   │
│      "amount": 100.00                                                                  │
│    }                                                                                    │
│                                                                                          │
│  If called twice with same key → Return same result, don't charge twice                │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Timeout Handling
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  What if a service doesn't respond?                                                     │
│                                                                                          │
│  Options:                                                                               │
│    1. Retry with exponential backoff                                                   │
│    2. Query for status (did it complete?)                                              │
│    3. After max retries → Compensate                                                   │
│                                                                                          │
│  NEVER assume failure = didn't happen!                                                 │
│  Payment timeout might mean: charged but response lost                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Compensation Failures
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  What if compensation fails?                                                            │
│                                                                                          │
│  Options:                                                                               │
│    1. Retry compensation (with backoff)                                                │
│    2. Log for manual intervention                                                      │
│    3. Dead letter queue for failed compensations                                       │
│    4. Alert operations team                                                            │
│                                                                                          │
│  Compensations should also be idempotent!                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## Saga vs 2PC

| Feature | Saga | 2PC (Two-Phase Commit) |
|---------|------|------------------------|
| Consistency | Eventual | Strong |
| Isolation | No (sees partial state) | Yes |
| Latency | Lower | Higher (locks held) |
| Availability | Higher | Lower (coordinator dependency) |
| Complexity | Business logic | Protocol complexity |
| Recovery | Compensations | Rollback |

## When to Use

✅ **Good for:**
- Long-running transactions (minutes/hours)
- Cross-service transactions
- High availability requirements
- Loosely coupled microservices
- When eventual consistency is acceptable

❌ **Not ideal for:**
- Strong consistency requirements
- Simple, fast transactions
- When compensation is impossible
- Tightly coupled systems (use DB transaction)

## Trade-offs

| Pros | Cons |
|------|------|
| No distributed locks | Eventually consistent |
| High availability | Complex error handling |
| Loose coupling | Requires compensating logic |
| Scalable | Debugging is harder |
| Works across services | Partial state visible |

## Used In (This Guide)

- **E-commerce (Q46)**: Checkout flow
- **Ticket Booking (Q42)**: Reserve → Pay → Confirm
- **Payment Gateway (Q47)**: Multi-step payment
- **Order Service (Q46)**: Order fulfillment
