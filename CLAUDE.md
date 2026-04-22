# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Settlement Reconciliation Service** — a Kotlin + Spring Boot service that compares payment transactions settled by an external payment processor (PaySettler) against internal records, identifying discrepancies.

The service receives a CSV file via API, stores it in S3, triggers async processing via Kafka, and persists reconciliation results in PostgreSQL.

## Development Workflow

Skills must be invoked explicitly — they are not triggered automatically.

Before implementing any feature:
1. `/grill-me <feature>` — clarify requirements and edge cases
2. `/write-a-prd <feature>` — document intent and decisions → saved to `docs/prd/`

After implementing:
3. `/write-a-spec <feature>` — document what was built → saved to `docs/spec/`

At the end, before opening the PR:
4. `/prepare-submission` — generate PR README draft from all PRDs and specs

**If asked to implement a feature without a PRD, do not start. Remind the user to run `/grill-me` and `/write-a-prd` first. If the user explicitly says to skip the workflow, proceed with the implementation.**

## Commit Convention

Follow Conventional Commits:

```
<type>(<scope>): <description>
```

Types used in this project:
- `feat` — new functionality
- `fix` — bug fix
- `test` — adding or updating tests
- `chore` — infra, config, dependencies
- `docs` — documentation, PRDs, specs
- `refactor` — code improvement without behavior change

Examples:
```
feat(reconciliation): implement POST /reconciliations endpoint
chore(infra): add Kafka and LocalStack to docker-compose
docs(prd): add PRD for CSV upload feature
test(reconciliation): add unit tests for matching logic
```

## Commands

```bash
# Start all infrastructure (required before running the app)
docker-compose up -d

# Build
./gradlew build

# Run application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "br.com.marvin.api.SomeTest"

# Run a single test method
./gradlew test --tests "br.com.marvin.api.SomeTest.methodName"
```

## Infrastructure

| Service     | Tool                        | Purpose                        |
|-------------|-----------------------------|--------------------------------|
| Database    | PostgreSQL 16               | Main persistence               |
| Storage     | LocalStack (S3-compatible)  | CSV file storage               |
| Messaging   | Apache Kafka + Zookeeper    | Async processing trigger       |
| Migrations  | Liquibase                   | Schema versioning and seed     |

**Database:** `localhost:5432`, database `settlement_db`, user/password `app`.

## Stack

- **Kotlin** + **Spring Boot 4**
- **JPA/Hibernate** for data access
- **Spring Kafka** for Kafka integration
- **AWS SDK (S3)** for file storage via LocalStack
- **Java 25** toolchain

## Package Structure

```
br.com.marvin.api
  domain/
    model/         ← JPA entities (ReconciliationRun, ReconciliationResult, InternalTransaction)
    vo/            ← value objects and enums (RunStatus, ReconciliationCategory)
  application/
    port/          ← interfaces for infrastructure (FileStorage, CsvParser, AlertEventPublisher, ReconciliationEventPublisher)
    usecase/       ← use cases, orchestration (@Service)
    (root)         ← DTOs and supporting classes (CsvTransaction, MatchResult, ReconciliationMatcher)
  infrastructure/
    persistence/   ← Spring Data repositories (no interface abstraction — JPA is stable enough)
    csv/           ← CSV parsing implementation (ApacheCsvParser)
    storage/       ← S3 client implementation (S3FileStorage)
    messaging/     ← Kafka producers, consumers, topic config
  web/
    dto/           ← request/response objects
  exception/       ← all exceptions (ReferenceDateException, CsvParseException)
```

**Rules:**
- `domain/` must not depend on `infrastructure/` or `web/`
- `application/` orchestrates — depends on `port/` interfaces, not on concrete infrastructure
- Infrastructure classes implement `port/` interfaces and are injected via Spring DI
- JPA annotations in domain entities are acceptable (pragmatic trade-off)
- JPA repositories are NOT abstracted behind interfaces — JPA is stable and unlikely to change
- No circular dependencies between layers

## Error Handling

- All errors handled centrally via `@ControllerAdvice`
- Response format follows **RFC 7807** (`ProblemDetail`) — Spring Boot native support, no custom classes needed
- Never leak stack traces or internal messages to the client

## Testing Strategy

- **Unit tests:** mandatory for all domain and application logic — pure Kotlin, no Spring context, fast
- **Integration tests:** at least one end-to-end happy path per endpoint — use H2 for persistence, mock S3 and Kafka
- Tests must run with `./gradlew test` only — no manual setup required

## Seed Data

- Internal transactions are seeded via **Liquibase changeset with `context="dev"`**
- Source file: `docs/sample-files/internal-transactions.json`
- In production, this changeset is skipped — real transactions arrive via normal application flow

## Performance

- CSV files range from 1,000–50,000 rows normally, up to 500,000 rows on peak periods (Black Friday)
- **Always process CSV via streaming** — never load the entire file into memory
- Use batch inserts when persisting reconciliation results

## Domain

Read `docs/domain-glossary.md` and `docs/processor-api-spec.md` before touching reconciliation code.

Key rules are documented there. Do not reinterpret them — follow them exactly.

## API Overview

| Method | Path                               | Description                        |
|--------|------------------------------------|------------------------------------|
| POST   | `/reconciliations`                 | Upload CSV + referenceDate         |
| GET    | `/reconciliations/{runId}/results` | Query results (filterable)         |
| GET    | `/reconciliations/{runId}/stats`   | Summary stats per category         |

Full feature specs and acceptance criteria are tracked as tasks — not in this file.

## Async Processing Flow

```
POST /reconciliations
  → validate referenceDate
  → upload CSV to S3
  → create ReconciliationRun (status: PENDING)
  → publish event to Kafka
  → return 202 Accepted

Kafka Consumer
  → download CSV from S3
  → update run status: PROCESSING
  → stream CSV line by line
  → reconcile against internal transactions
  → persist results
  → update run status: COMPLETED or FAILED
```

## ReconciliationRun Status

`PENDING` → `PROCESSING` → `COMPLETED`  
`PENDING` → `PROCESSING` → `FAILED`

## Bonus

If >5% of transactions are mismatched or unreconciled, publish an alert event to Kafka topic `settlement.reconciliation.events`.
