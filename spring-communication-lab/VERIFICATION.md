# Verification report — Kafka edition, 12 October 2026

## Executed successfully

- **Maven `clean verify`: BUILD SUCCESS across all seven reactor entries** (parent, shared library and five runnable applications).
- **32 automated tests passed: 0 failures, 0 errors, 0 skipped.**
- Six new `KafkaDeliveryTest` scenarios ran against a **real in-process Kafka 3.9.2 KRaft broker**, using the shared application Kafka configuration: independent consumer groups, transient retry success, retry exhaustion, schema rejection, malformed JSON recovery with preserved key/payload/header, and a stopped consumer catching up.
- `OutboxRelayTest` used H2 with a mocked Kafka send to verify that publish failure keeps the outbox pending, then a later successful acknowledgement marks the same event sent. This does not simulate a real broker crash or PostgreSQL locking.
- Existing JWT grant/rejection, provider contract/scope/validation, real HTTP stub client, HQL CRUD, payment idempotency and consumer deduplication tests all passed.
- Full Order, Payment and Inventory bean graphs started in test contexts with H2. These context tests disable Kafka listener startup and KafkaAdmin topic creation; broker behavior is tested separately above.
- All five executable application JARs and the shared-library JAR built. The ZIP contains source, not generated JARs.
- Java source was formatted before the final build. Python scripts compile. YAML/XML/JSON configuration, internal Markdown source links and ZIP integrity were checked.

Environment: OpenJDK 17.0.20, Maven 3.9.11, Spring Boot 3.5.16, Spring Kafka 3.3.16, managed Kafka client/test broker 3.9.2. Maven verification used the available local dependency cache. No Docker CLI/daemon or Kubernetes cluster was available.

Evidence: [build-summary.txt](verification/build-summary.txt), [test-summary.json](verification/test-summary.json).

## Supplied for you to execute

| Check | Command / instructions | Status here |
|---|---|---|
| PostgreSQL migrations, HQL and concurrent constraints | `./mvnw -P integration verify` | Requires Docker/Testcontainers; not run |
| Compose broker 4.1.2 and image startup | `docker compose up --build -d` | Docker unavailable; not run |
| Full HTTP/JWT/PostgreSQL/Kafka flow | `python3 scripts/scenarios.py` | Full stack not run |
| Real broker outage and outbox recovery | `python3 scripts/scenarios.py --disrupt` | Compose scenario not run; relay behavior covered separately by the focused test |
| Zipkin HTTP-to-outbox-to-Kafka trace continuity | `python3 scripts/scenarios.py --traces` | Configuration supplied; full-stack export not run |
| Poison message CLI and DLT inspection | `scripts/poison_event.py`, test.md M01 | Embedded-broker behavior passed; Compose CLI scenario not run |
| Three-instance load balancing and Eureka | README optional overlays | Not run here |
| Kubernetes deployment and DNS | k8s/README.md | Not run here |
| Prometheus scraping | Metrics overlay | Not run here |

Embedded Kafka 3.9.2 checks do not substitute for running the pinned Kafka 4.1.2 Docker image. H2 checks do not substitute for PostgreSQL-specific integration tests. The supplied scenario scripts are executable test plans, not claimed successful full-stack runs.

This edition implements Kafka events/outbox/idempotent consumers, **not a saga or compensating transactions**. Kafka remains a local PLAINTEXT broker without SASL/ACLs; HTTPS, database TLS and production key management are also outside this revision.
