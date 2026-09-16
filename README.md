# 💳 Idempotent Payment/Wallet Event Processor

A Spring Boot service that acts as an internal transaction ledger. It handles duplicate webhook payloads from a payment gateway concurrently, ensuring **exactly-once processing** using database-level idempotency and pessimistic locking.

---

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| **Java 17+** | Core language |
| **Spring Boot 4.1.1** | Application framework |
| **Spring Data JPA** | Database access & locking |
| **H2 Database** | In-memory database (zero-config) |
| **JUnit 5** | Integration testing |
| **Maven** | Build tool |

---

## 📌 Features

- **Idempotent Webhook Ingestion** — Duplicate `transactionId` requests return `409 Conflict` without modifying the balance
- **Pessimistic Locking** — `SELECT ... FOR UPDATE` prevents negative balances during concurrent debits
- **Two-Layer Defense** — Database unique constraint + row-level locking for bulletproof concurrency
- **Zero-Config Testing** — All tests run with an in-memory H2 database, no external setup needed

---

## 📡 API Endpoint

### `POST /api/v1/transactions/process`

**Request Body:**
```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "amount": 250.00,
  "type": "DEBIT"
}
```

**Success Response (200 OK):**
```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "newBalance": 250.00,
  "message": "Transaction processed successfully"
}
```

**Duplicate Response (409 Conflict):**
```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DUPLICATE",
  "newBalance": null,
  "message": "Duplicate transaction: 550e8400-e29b-41d4-a716-446655440000"
}
```

**Insufficient Funds Response (400 Bad Request):**
```json
{
  "status": "FAILED",
  "message": "Insufficient funds: balance=0.00, attempted debit=100.00"
}
```

---

## 🚀 How to Run

### Prerequisites
- Java 17 or higher
- Maven (or use the included Maven wrapper)

### Run the Application
```bash
./mvnw spring-boot:run
```
On Windows:
```bash
mvnw.cmd spring-boot:run
```

The app starts on **http://localhost:8080**

### H2 Console (Optional)
Access the in-memory database at: **http://localhost:8080/h2-console**

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:ledgerdb` |
| Username | `sa` |
| Password | *(leave empty)* |

---

## ✅ How to Run Tests

```bash
./mvnw test
```
On Windows:
```bash
mvnw.cmd test
```

Or in **IntelliJ IDEA**: Right-click on `TransactionIntegrationTest.java` → **Run**

### Test Suite

| # | Test | What it verifies |
|---|---|---|
| 1 | **Happy Path** | A single valid debit transaction succeeds, balance is deducted correctly |
| 2 | **Idempotency** | 3 identical `transactionId` requests sent simultaneously — balance deducted only once, 2 get `409 Conflict` |
| 3 | **Race Condition** | 10 concurrent ₹100 debits on a ₹500 wallet — exactly 5 succeed, 5 fail, final balance is ₹0 |

### Expected Console Output
```
═══════════════════════════════════════════════════
TEST 1: Happy Path — Single Debit
HTTP Status: 200
✅ Balance after debit: ₹250.00
═══════════════════════════════════════════════════
TEST 2: Idempotency — 3 Identical TransactionIDs
  Success: 1 | Conflict: 2
✅ Balance deducted exactly once: ₹400.00
═══════════════════════════════════════════════════
TEST 3: Race Condition — 10 Concurrent ₹100 Debits
  Succeeded: 5 | Failed (insufficient funds): 5
✅ Final balance: ₹0.00
✅ Exactly 5 succeeded, 5 rejected — no negative balance!
```

---

## 🏗️ Project Structure

```
src/
├── main/java/com/webhook/WebhookApplication/
│   ├── WebhookApplication.java            # Main entry point
│   ├── controller/
│   │   └── TransactionController.java     # REST endpoint
│   ├── dto/
│   │   ├── TransactionRequest.java        # Request payload
│   │   └── TransactionResponse.java       # Response payload
│   ├── entity/
│   │   ├── Wallet.java                    # Wallet entity with @Version
│   │   └── ProcessedTransaction.java      # Idempotency record
│   ├── enums/
│   │   └── TransactionType.java           # DEBIT / CREDIT enum
│   ├── exception/
│   │   ├── DuplicateTransactionException.java
│   │   ├── InsufficientFundsException.java
│   │   └── GlobalExceptionHandler.java    # Maps exceptions to HTTP codes
│   ├── repository/
│   │   ├── WalletRepository.java          # Pessimistic lock query
│   │   └── ProcessedTransactionRepository.java
│   └── service/
│       └── TransactionService.java        # Core business logic
├── main/resources/
│   ├── application.properties             # H2 config
│   └── data.sql                           # Seeds test wallet (₹500)
└── test/
    ├── java/com/webhook/WebhookApplication/
    │   └── TransactionIntegrationTest.java # All 3 required tests
    └── resources/
        └── application-test.properties    # Test profile config
```

---

## 🔒 Concurrency Strategy

**Two-Layer Defense:**

1. **Unique Constraint** on `transaction_id` → Prevents duplicate processing (idempotency)
2. **Pessimistic Lock** (`SELECT ... FOR UPDATE`) on wallet row → Serializes concurrent debits (prevents negative balance)

See [DECISIONS.md](DECISIONS.md) for detailed reasoning.