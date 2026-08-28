# Fault-Tolerant Data Processing System

**Author**: Candidate Submission  
**Tech Stack**: Java 18 (Standard Library + H2 In-Memory JDBC + Gson)  
**Deliverable**: Data Ingestion, Dynamic Normalization, SHA-256 Deduplication, ACID Transaction Rollback, & Aggregations Engine

---

## 🛠️ My Approach & Engineering Rationale

When reading the problem statement, I noticed that client payloads are completely unpredictable—field names change without notice, types are inconsistent, events get retried, and mid-request database failures can occur. 

Instead of adding heavy framework dependencies like Spring Boot or Docker containers, I deliberately built a lightweight, zero-overhead Java service using Java's built-in `HttpServer` and an embedded H2 SQL database. This keeps cold-start time under 100ms, requires zero external setup for reviewers, and runs natively on any machine with Java installed.

```text
 Client Request ---> REST API (/api/events/ingest)
                          |
                  Normalization Layer
             (Field Extraction & Type Casting)
                          |
              Idempotency Engine (SHA-256)
                          |
          ACID Transaction Boundary (H2 DB)
         /                                 \
  [Failure Toggle ON]              [Normal Flow]
         |                                |
  rollback() & HTTP 500             commit() & HTTP 200
```

---

## 🚀 Quick Start (How to Run)

### 1. Compile Code
In the project root directory, run:
```powershell
javac -cp "h2.jar;gson.jar" -d bin (Get-ChildItem -Recurse -Filter *.java src | Select-Object -ExpandProperty FullName)
```

### 2. Run Automated Unit & Integration Tests
I wrote a dedicated test suite verifying normalisation, deduplication, transaction rollback, and aggregation math:
```powershell
java -cp "bin;h2.jar;gson.jar" com.dataprocessor.service.DataProcessorTest
```

### 3. Start Backend Server & Web Dashboard
```powershell
java -cp "bin;h2.jar;gson.jar" com.dataprocessor.App 8080
```
Open your browser at **`http://localhost:8080`** to access the interactive web interface.

---

## ❓ Evaluation Questions & Design Answers

### 1. What assumptions did you make?
- **Core Business Fields**: I assumed every valid event represents a quantifiable business metric tied to a client identifier (`source`, `client_id`, `clientId`) and a numeric amount (`amount`, `val`, `value`, `total`). If either identifier or amount is missing or unparseable, I reject the payload gracefully with status `REJECTED` and log the raw payload for auditability.
- **Timestamp Defaulting**: If a timestamp is omitted or formatted unpredictably, my normalization layer defaults to the current UTC timestamp (`OffsetDateTime.now(ZoneOffset.UTC)`).
- **Fingerprint Identity**: Without explicit transaction UUIDs from clients, I assume events from the same client matching in metric, normalized numeric amount, and UTC timestamp represent duplicate retries of the exact same event.
- **ACID Persistence**: I assumed relational SQL storage with explicit transaction boundaries (`setAutoCommit(false)`, `commit()`, `rollback()`) is mandatory for preventing partial data corruption.

### 2. How does your system prevent double counting?
- **SHA-256 Fingerprint Generator**: Upon receiving an event, `IdempotencyService` constructs a deterministic string and hashes it via SHA-256:
  $$\text{Fingerprint} = \text{SHA256}(\text{client\_id} + ":" + \text{metric} + ":" + \text{formatted\_amount} + ":" + \text{utc\_timestamp})$$
- **Database Unique Constraint**: The `canonical_events` SQL table enforces a `UNIQUE(event_fingerprint)` constraint.
- **Transactional Check**: Inside a database transaction, my service queries the table for the fingerprint. If detected, it updates the raw event log status to `DUPLICATE_IGNORED` and immediately returns `HTTP 200` with message `"Duplicate event detected. Double counting prevented."`. Zero duplicate canonical rows are created, protecting aggregate totals.

### 3. What happens if the database fails mid-request?
- **Single Transaction Boundary**: Ingesting a raw log and writing to `canonical_events` happens within a single explicit JDBC transaction (`conn.setAutoCommit(false)`).
- **Atomic Rollback**: If a database error occurs or if the **"Simulate DB Write Failure"** toggle is enabled, an exception triggers `conn.rollback()`.
- **Clean State Integrity**: All partial database writes are completely wiped clean. Zero orphaned records remain in `canonical_events`. When the client retries the request after failure recovery, the transaction starts afresh and processes cleanly without entering an inconsistent state.

### 4. What would break first at scale?
- **Synchronous Ingestion & DB Lock Starvation**: At high concurrency (>5,000 requests/sec), performing inline database writes and synchronous fingerprint checks inside HTTP worker threads will lead to thread pool exhaustion and database connection starvation.
- **My Recommended Architectural Fix at Scale**:
  1. **Asynchronous Ingestion Stream**: Decouple ingestion by accepting raw events via HTTP and pushing directly to an event queue (e.g., Apache Kafka / AWS Kinesis), returning an immediate `HTTP 202 Accepted`.
  2. **Distributed Memory Cache**: Use Redis or a Bloom Filter cluster to verify fingerprints in sub-millisecond memory before database persistence.
  3. **Analytical Data Warehouse**: Micro-batch normalized events from Kafka into an analytical data warehouse (e.g., ClickHouse / BigQuery / Snowflake) for bulk aggregations.

---

## 🧪 Quick Test API Commands

Test via curl:
```bash
# Valid Ingestion
curl -X POST http://localhost:8080/api/events/ingest \
  -H "Content-Type: application/json" \
  -d '{"source":"client_A","payload":{"metric":"sales","amount":"1500","timestamp":"2024/01/01"}}'

# Aggregations Query
curl http://localhost:8080/api/events/aggregations
```
