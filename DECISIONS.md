# Decision Log
## 1. How did you handle the concurrency race condition?
I implemented a **two-layer defense strategy** to handle both idempotency and concurrent balance modifications:
### Layer 1 — Idempotency via Database Unique Constraint
The `processed_transactions` table has a **unique constraint on `transaction_id`**. When duplicate webhook payloads arrive simultaneously (e.g., 3 requests with the same `transactionId` within 50ms), all three threads attempt to INSERT into this table. Only one INSERT succeeds — the other two throw a `DataIntegrityViolationException`, which is caught and mapped to a **409 Conflict** response.
This is more reliable than an application-level check (`existsByTransactionId`) alone, because between the CHECK and the INSERT, another thread could slip through (classic TOCTOU race condition). The unique constraint is enforced atomically at the database level, making it immune to race conditions.
### Layer 2 — Pessimistic Locking on Wallet Row
The `WalletRepository.findByUserIdWithLock()` method uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`, which translates to a `SELECT ... FOR UPDATE` SQL statement. This acquires an **exclusive row-level lock** on the wallet row.
When 10 concurrent debit requests arrive for the same wallet, they are forced to execute **one at a time** — each thread must wait for the previous one to commit or rollback before it can read and modify the balance. This guarantees:
- No two threads ever read the same stale balance
- The balance can never go negative
- Exactly 5 out of 10 ₹100 debits succeed for a ₹500 wallet
### Why Pessimistic over Optimistic Locking?
I chose pessimistic locking over optimistic locking (`@Version` with retry) because:
- **High contention scenario**: The assignment explicitly tests 10 concurrent requests on the same wallet. Optimistic locking would cause a "retry storm" — multiple threads failing and retrying repeatedly, leading to unpredictable behavior.
- **Simpler code**: No retry loop needed. The database handles serialization naturally.
- **Deterministic results**: With pessimistic locking, exactly 5 succeed and 5 fail, every single time. With optimistic locking + retries, the outcome depends on timing and retry limits.
  I still kept the `@Version` field on the `Wallet` entity as an additional safety net, but the primary concurrency control is the pessimistic lock.
---

## 2. Where did your AI assistant give you an incorrect or sub-optimal suggestion?
### Sub-optimal: Initially suggested only a single layer of locking
The AI assistant initially suggested using **only a pessimistic lock** (`SELECT ... FOR UPDATE`) on the wallet row to handle both idempotency and concurrency. While this would serialize concurrent requests, it was **sub-optimal for idempotency** because:
- A pessimistic lock alone doesn't prevent duplicate processing — it just serializes it. If the same `transactionId` arrives twice, the second request would wait for the lock, then proceed to debit the balance again, resulting in a **double deduction**.
- I had to explicitly add the **unique constraint on `transaction_id`** in the `processed_transactions` table as a separate idempotency guard. This ensures that even if the lock serializes the requests, the second INSERT fails with a constraint violation.
  The AI conflated **concurrency control** (preventing negative balances) with **idempotency** (preventing duplicate processing). These are two distinct problems requiring two distinct solutions:
- **Concurrency** → Pessimistic lock (`SELECT FOR UPDATE`)
- **Idempotency** → Database unique constraint on `transactionId`