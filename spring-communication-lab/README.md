# Spring Boot communication lab — Kafka edition

A small, runnable learning project for Pratik: learn one flow first, then turn failures on deliberately.

**Kafka migration:** [KAFKA.md](KAFKA.md) explains topics, groups, offsets, dead letters, and why this is not yet a saga.

**Start here:** this README → [notes.md](notes.md) → [test.md](test.md). [CODE_MAP.md](CODE_MAP.md) indexes the source. [VERIFICATION.md](VERIFICATION.md) records exactly what was run before packaging.

## What you receive

| Application/module | Port | Job |
|---|---:|---|
| `auth-service` | 9000 | Real OAuth 2.0 client-credentials grants and signed JWTs |
| `order-service` | 8081 | HTTP CRUD, checkout, four HTTP clients, timeouts, retries, circuit breaker |
| `payment-service` | 8082 | Simulated payment, durable idempotency, transactional outbox |
| `inventory-service` | 8083 | Product/price lookup and an event-driven sales ledger |
| `discovery-server` | 8761 | Optional Eureka exercise |
| `lab-common` | — | Error DTOs, security, headers, Kafka topics and recovery, event contract |

PostgreSQL holds **three separately owned databases**. Kafka carries durable events. Zipkin displays traces exported through Micrometer and OpenTelemetry. Prometheus is optional.

**No real money is charged.** Inventory tracks products and sales receipts; stock reservation, refunds and a complete saga are deliberately outside this first lab. The HTTP and event reliability mechanisms are implemented, not pseudocode.

Runtime inserts, selects, updates and deletes use **explicit Hibernate HQL** with `EntityManager`/`Session`. There are no `JpaRepository`, derived repository methods, `save()`, `findById()` or native SQL application queries. Flyway SQL is used for database schema creation and two seed products.

## 1. Start everything with Docker

Install Docker with Compose v2. Allow approximately **6 GB memory**, more for the optional extra instances. The first build downloads dependencies and can take several minutes. No host Java or Maven is required for this path.

This edition uses Compose project name `communication-lab-kafka`, which gives it separate data volumes from the earlier RabbitMQ edition. If the old project is running, first stop it with `docker compose -p communication-lab down` from its directory to free the shared host ports. Keep its volumes if you want to revisit it. Existing RabbitMQ messages and database state are not automatically migrated.

From the extracted `spring-communication-lab` directory:

```bash
docker compose up --build -d
# Observe startup; Ctrl+C exits the log viewer without stopping the services.
docker compose logs -f order-service payment-service inventory-service
```

In another terminal, run the executable scenarios (Python 3.9+):

```bash
python3 scripts/scenarios.py --traces
```

The script waits for application readiness, gets real JWTs, and checks the core scenarios. It creates test data and resets lab fault settings when finished. The additional disruptive scenario is opt-in:

```bash
python3 scripts/scenarios.py --traces --disrupt
```

`--disrupt` temporarily stops and restarts this Compose project's Kafka container. Use it only on your local learning stack.

| UI | Address |
|---|---|
| Order Swagger | http://localhost:8081/swagger-ui/index.html |
| Payment Swagger | http://localhost:8082/swagger-ui/index.html |
| Inventory Swagger | http://localhost:8083/swagger-ui/index.html |
| Zipkin | http://localhost:9411 |
| Kafka bootstrap (host/IDE) | `localhost:9092` — inspect using the CLI commands in test.md |

## 2. Make your first requests

macOS/Linux shell:

```bash
TOKEN=$(python3 scripts/get_token.py)

curl -i http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: first-order' \
  -d '{"sku":"BOOK-1","quantity":1,"note":"Pratik learning HTTP"}'
```

Copy `id` from the JSON response:

```bash
ORDER_ID=replace-with-the-returned-id

curl -i -X POST "http://localhost:8081/api/v1/orders/$ORDER_ID/checkout" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-Correlation-ID: first-checkout'

curl "http://localhost:8081/api/v1/orders/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN"
```

Creation returns `201`. Checkout normally returns `202`; the order becomes `PAID` after the event arrives. Repeating checkout for a paid order returns `200`. If Payment times out, **repeat checkout with the same order ID**. Do not create another order to retry a charge.

`BOOK-1` costs ₹500 and `PEN-1` costs ₹25. Order obtains the price from Inventory; the caller does not choose the checkout amount.

For Windows, the Python scenario runner works unchanged. In PowerShell use `$TOKEN = python scripts/get_token.py` and `curl.exe`, or use Swagger's Authorize button with the printed token. Tokens expire after 30 minutes. Regenerate a token after restarting Auth, because this small lab creates a new signing key on startup.

## 3. Run Java tests

Java 17+ is needed outside Docker. The Maven wrapper downloads Maven 3.9.11 on first use.

```bash
./mvnw verify
# Windows:
# mvnw.cmd verify
```

This runs unit tests, HTTP client/contract tests, real authorization-server tests, H2-backed HQL tests, a real embedded KRaft broker test, outbox recovery tests and complete service-context startup tests. H2 is a **test dependency only**; application startup defaults to PostgreSQL.

For PostgreSQL integration tests, start Docker first:

```bash
./mvnw -P integration verify
```

`*IT.java` uses real PostgreSQL 17 Testcontainers and Flyway migrations. These tests intentionally fail if Docker is unavailable; they do not silently turn into H2 tests. Compose Kafka 4.1.2/Zipkin behavior is covered separately by `scripts/scenarios.py` and [test.md](test.md).

## 4. Run applications from your IDE instead

Start infrastructure only:

```bash
docker compose up -d postgres kafka zipkin
./mvnw clean package
```

Run each command in its own terminal, or run the corresponding `*Application` main class in your IDE:

```bash
java -jar auth-service/target/auth-service-1.0.0.jar
java -jar inventory-service/target/inventory-service-1.0.0.jar --spring.profiles.active=lab
java -jar payment-service/target/payment-service-1.0.0.jar --spring.profiles.active=lab
java -jar order-service/target/order-service-1.0.0.jar --spring.profiles.active=lab
```

The applications use localhost infrastructure by default. Do not also run the Compose application containers on the same ports. `lab` enables fault/replay/comparison endpoints, all protected by `demo.admin`. Without `lab`, those controllers do not exist. The main payment path still has its real resilience configuration.

| DB | Username | Local password |
|---|---|---|
| `orders` | `order` | `order-password` |
| `payments` | `payment` | `payment-password` |
| `inventory` | `inventory` | `inventory-password` |

Host `localhost`, port `5432`. Override `DB_URL`, `DB_USER`, `DB_PASSWORD` per application to use your own PostgreSQL. Create the three databases/owners first; Flyway creates their tables. Existing unrelated databases are never used by these instructions.

## 5. Optional learning exercises

**Three Payment instances with client-side load balancing:**

```bash
docker compose -f compose.yml -f compose.lb.yml up --build -d
python3 scripts/scenarios.py --lb --traces
```

Use `/api/lab/clients/feign` repeatedly to see `payment-1`, `payment-2`, `payment-3`. The primary checkout WebClient intentionally calls the configured Payment URL, so its fault exercises remain deterministic. All three Payment instances use the same Payment database; unique constraints and locked outbox reads coordinate them.

**Eureka instead of the fixed discovery list:**

```bash
docker compose -f compose.yml -f compose.lb.yml -f compose.eureka.yml up --build -d
```

Open http://localhost:8761, wait up to 90 seconds for registration/cache refresh, then repeat the Feign exercise. To return to the basic stack:

```bash
docker compose -f compose.yml -f compose.lb.yml -f compose.eureka.yml down
docker compose up -d
```

`down` keeps named data volumes. Do not use `down -v` unless you intentionally want to erase this lab's databases and Kafka logs.

**Prometheus:**

```bash
python3 scripts/get_token.py --file infra/prometheus/token.txt
docker compose -f compose.yml -f compose.metrics.yml up -d
```

Open http://localhost:9090. Regenerate the file after 30 minutes or an Auth restart. This file-mounted token is a small local demonstration, not a production credential rotation design. See the PromQL examples in the notes.

**Kubernetes DNS and replicas:** see [k8s/README.md](k8s/README.md). This is an optional local-cluster exercise; it is not needed to learn the core flow.

## Troubleshooting

- `401`: obtain a fresh token; confirm the same issuer and current Auth signing key are used. JWTs are credentials: do not paste them into public logs.
- `403`: use a client with the required scope. `viewer` deliberately has read-only scopes.
- `503 TOKEN_SERVICE_UNAVAILABLE`: wait for Auth; check `AUTH_TOKEN_URL` and the service's client secret.
- `503 PAYMENT_UNAVAILABLE`: inspect `/api/lab/faults`, `/api/lab/circuit`, logs and Payment readiness. Reset fault/circuit settings or wait for recovery; retry the same order.
- `PENDING` after successful payment: inspect pending outbox rows, Kafka consumer lag/DLTs and consumer logs. It is an observable eventual-consistency state.
- Database login fails after editing `init.sql`: initialization scripts run only on an empty PostgreSQL volume. Apply a deliberate migration or recreate **only this lab's** data after backing it up.
- Port already in use: stop the other local application or change host port mappings and script environment variables. Container-to-container URLs use container ports.
- Maven in a corporate network: configure your own Maven proxy/mirror settings. No environment-specific proxy or private credential is shipped in this ZIP.

The Java dependency versions are pinned in `pom.xml`: Spring Boot 3.5.16, Spring Cloud 2025.0.3, Resilience4j 2.3.0 and springdoc 2.8.16. They were chosen as a compatible Boot 3 learning stack; this is not a claim that they are the newest versions or an audited production baseline. Spring Kafka 3.3.16 is managed by Boot; Compose uses Apache Kafka 4.1.2 in single-node KRaft mode. The embedded test broker uses Boot's managed Kafka version (3.9.2), so running the Compose scenarios also checks the client against Kafka 4.1.2. Infrastructure tags are not immutable image digests.
