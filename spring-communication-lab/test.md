# Scenario-based test guide

Read `VERIFICATION.md` for the checks performed before delivery. This file is the repeatable test plan, not a claim that every environment-specific scenario was already executed.

## A. Prerequisites and commands

Run from the extracted project root. Docker Compose v2 is required for the full stack, Java 17+ for host Maven tests, and Python 3.9+ for scripts. Keep other traffic out of the lab during the scenario run: assertions compare before/after counts and fault switches are per Payment instance.

```bash
# Fast/local Java verification: no Docker needed
./mvnw verify

# Real PostgreSQL integration tests: Docker daemon must be running
./mvnw -P integration verify

# Full running system
# First startup may take several minutes while images/dependencies download.
docker compose up --build -d
python3 scripts/scenarios.py --traces

# Also stop/restart Kafka to test outbox recovery
python3 scripts/scenarios.py --traces --disrupt
```

The default script runs S01–S15. `--traces` adds S16; `--lb` adds S17; `--disrupt` adds S18. It prints one PASS per scenario group and exits with a nonzero error on failed assertions. It creates orders/payments; repeat runs use new UUIDs and compare ledger deltas. It resets fault settings and the circuit in `finally` when services remain reachable.

**Success evidence:** all selected scenario groups print PASS, the process exits 0, and all assertions in the expected-result column below hold. A timeout/error is a failure to investigate, not a skipped/pass result.

## B. Executable end-to-end scenarios

Implementation: [scripts/scenarios.py](scripts/scenarios.py).

| ID | Scenario and steps | Expected result | Main code to inspect |
|---|---|---|---|
| S01 | Wait for Auth + three service readiness endpoints; request real client-credentials token | UP; OAuth bearer token issued | `AuthorizationConfig`, `get_token.py` |
| S02 | Read without token, with malformed token, then with viewer; viewer tries POST | 401, 401, 200, 403 respectively | `SecurityConfig`, `@PreAuthorize` |
| S03 | Send quantity 0; malformed UUID; nonexistent UUID; size 0; invalid sort | 400/400/404/400/400; no writes | DTO validation, `ErrorAdvice`, `OrderStore.page` |
| S04 | POST draft, GET/list, PUT quantity 2, PATCH note, DELETE draft | 201; amount 500 then 1000; note changes; 204; later GET 404 | `OrderController`, HQL store methods |
| S05 | Call same Payment endpoint through four clients; send correlation header; forward token | All 200; same supplied correlation ID received by Payment | `ClientLabController`, clients, `CorrelationFilter` |
| S06 | Create and checkout order; poll Order and Inventory | 202/200; eventual PAID; one new sales unit/receipt; pending/paid edits and deletion rejected with 409 | Checkout, relay and both listeners |
| S07 | Replay exact payment key/body; change amount with same key; use another key for same order | Original payment ID and replay header; 409 for both conflicting cases | `PaymentStore`, unique constraints |
| S08 | Replay the same persisted outbox event | Publish attempts increase; sold units do not increase again | Outbox replay, `ProcessedEvent`, `SalesReceipt` |
| S09 | Six concurrent checkouts of one fresh order | Requests accepted/completed; one payment-linked order and exactly one new sales receipt | DB constraints; frozen payload |
| S10 | Payment returns 503 twice, then succeeds | Three HTTP attempts; one business payment; eventual PAID | `paymentRetry`, stable key |
| S11 | Inject persistent 400, 500, then 429 separately | One attempt each; Order returns 502/502/503; 429 Retry-After survives mapping | Retry predicate, `DownstreamAdvice` |
| S12 | Delay each Payment request 2 seconds before processing | Caller returns 503 within bounded time; same-order recovery becomes PAID | Read timeout; total deadline; idempotency |
| S13 | Commit payment, delay only first response | Caller retries; two attempts; one original payment, eventual PAID | `FaultInjector.after`, durable key |
| S14 | Fail four checkouts; make fifth call; repair provider; wait five seconds; probe | CLOSED→OPEN; fifth makes zero provider calls; successful half-open probe closes circuit | Breaker outside retry |
| S15 | Fetch v1/v2 order, metrics and OpenAPI | v1 flat money; v2 nested total; request/client/timeout metrics present; OpenAPI readable | `OrderV2Controller`, Actuator, springdoc |
| S16 | Fetch the checkout's returned trace ID from Zipkin | Same trace contains Order, Payment and Inventory service spans | Saved outbox `traceparent`, HTTP/Kafka observation |
| S17 | Run LB overlay and call Feign 12 times | See payment-1, payment-2 and payment-3 | Simple discovery + Spring Cloud LoadBalancer |
| S18 | Stop broker, capture payment, inspect pending outbox, start broker | Payment committed; order PENDING during outage; queued outbox delivered later; PAID after recovery | Transactional outbox, relay retry |

S12 deliberately demonstrates that a server can keep working after the client stops waiting. A 503 from checkout is not proof that no payment exists. Always retry the **same order**.

### Run S17 with three instances

```bash
docker compose -f compose.yml -f compose.lb.yml up --build -d
python3 scripts/scenarios.py --lb --traces
```

The comparison Feign calls load-balance. Core WebClient checkout still targets `payment-service`, so the main fault tests use its controlled fault state.

## C. Manual investigations

### M01 — Invalid schema/JSON and group-specific dead-letter topics

Start the whole Compose stack. Run either case (both are safe test messages):

```bash
python3 scripts/poison_event.py
python3 scripts/poison_event.py --malformed
```

Each command prints a new key. Read both DLTs and locate that key:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 --topic payment.captured.v1.order-service.DLT \
  --from-beginning --timeout-ms 10000 --property print.key=true --property print.headers=true

docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 --topic payment.captured.v1.inventory-service.DLT \
  --from-beginning --timeout-ms 10000 --property print.key=true --property print.headers=true
```

The console consumer intentionally exits after an idle timeout and may return a nonzero code for that timeout; inspect the records printed first. Expected: the same key and original invalid payload appear in **both** DLTs, with error/original-topic metadata. No new business payment/order/sales receipt is created. Invalid schema/JSON bypass retries; transient failures get three attempts. Afterward, an ordinary valid checkout must still become PAID and add one sale.

Code: `PaymentEventCodec`, `KafkaConfiguration.paymentEventErrorHandler`. Automated equivalents: `KafkaDeliveryTest.unsupportedVersionGoesToBothGroupSpecificDeadLetterTopics` and `malformedJsonPreservesRawBytesAndDoesNotBlockLaterRecords`.

### M02 — Consumer stopped while producer continues

1. Create a DRAFT order and record current Inventory sales count for `BOOK-1`.
2. `docker compose stop inventory-service`.
3. Checkout the **already-created** order; new order creation requires Inventory for price lookup.
4. Order becomes PAID. Inventory's group cannot advance while its consumer is down.
5. Inspect group lag with the command below.
6. `docker compose start inventory-service` and poll `/api/v1/inventory/BOOK-1/sales`.

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group inventory-service
```

Expected: Inventory lag increases while stopped, then returns to zero; sold units increase once on restart. Group membership may take a short time to expire. The topic retains records independent of either group's progress (seven-day main-topic retention). Catch-up assumes the records are still retained.

### M03 — Eureka discovery

```bash
docker compose -f compose.yml -f compose.lb.yml -f compose.eureka.yml up --build -d
```

Open http://localhost:8761. Expect Order, Inventory and three Payment registrations after startup/cache refresh. Obtain a token, then call `/api/lab/clients/feign` repeatedly. Eureka provides instance locations; Spring Cloud LoadBalancer chooses one. Stop `payment-2` with the same Compose file list; observe that registry eviction/cache refresh is not instantaneous. Initial calls can fail until the registry view updates. Restart it when finished.

### M04 — Kubernetes DNS and readiness

Follow [k8s/README.md](k8s/README.md). Inspect:

```bash
kubectl -n communication-lab get services
kubectl -n communication-lab get endpointslices -l kubernetes.io/service-name=payment-service
kubectl -n communication-lab get pods -l app=payment-service
```

Expected: one stable Payment Service DNS name and three selected ready pods. Perform an ordinary checkout through port forwards. The fault suite is not deterministic across replicas with independent in-memory fault switches; do not use it here.

### M05 — Inspect the database effects

Use PostgreSQL's SQL client for inspection; the application's runtime mutations remain HQL.

```bash
docker compose exec -T postgres psql -U postgres -d orders \
  -c 'select id, status, amount, payment_id from purchase_orders order by created_at desc limit 10;'

docker compose exec -T postgres psql -U postgres -d payments \
  -c 'select order_id, count(*) from payments group by order_id having count(*) > 1;'

docker compose exec -T postgres psql -U postgres -d payments \
  -c 'select id, sent, attempts, traceparent from outbox_events order by created_at desc limit 10;'

docker compose exec -T postgres psql -U postgres -d inventory \
  -c 'select sku, sum(quantity) as sold_units, count(*) as receipts from sales_receipts group by sku;'
```

Expected: duplicate-payment query returns zero rows. Outbox rows become sent after Kafka broker acknowledgement; during broker failure they stay unsent. Relay attempts can be greater than one without creating more payments. `traceparent` is present for payments started under an observed HTTP request.

### M06 — Metrics and dependency latency

```bash
TOKEN=$(python3 scripts/get_token.py)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/actuator/metrics/http.client.requests
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/actuator/metrics/lab.payment.timeouts
```

The timeout counter is created lazily: exercise S12 first, otherwise that particular meter may not exist yet. Run several ordinary and slow checkouts; observe request counts, latency histograms, failure status labels and timeout counts. Prometheus setup/queries are in README/notes. Ensure its token has not expired before diagnosing empty graphs as an application bug.

### M07 — Authentication failure versus authorization failure

```bash
python3 scripts/get_token.py --client viewer
python3 scripts/get_token.py --client order-service
```

Viewer can read but cannot mutate orders. The Order service client can call Payment but cannot administer faults or create orders at the public Order API. Change a JWT character and expect 401; do not assume decoding JWT JSON proves the signature is valid. Restart Auth, obtain a fresh token and confirm it works; old tokens are no longer trusted after the new ephemeral key replaces the old key set (resource-server key caches may affect when rejection is observed).

### M08 — Compare statuses at the provider and consumer

Set a Payment fault:

```bash
TOKEN=$(python3 scripts/get_token.py)
curl -X POST http://localhost:8082/api/lab/faults \
 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
 -d '{"mode":"429","remaining":-1,"delayMs":0}'
```

A valid direct Payment POST now returns **429** and `Retry-After: 2`. A checkout through Order returns **503** with the same header, after one Payment attempt. Repeat with `500` (Order returns 502), `503` (three attempts unless the circuit is open), and `400` (one attempt). Authentication/scope checks execute before controller fault injection, so fake 401/403 faults are not substitutes for the real S02 tests.

Always reset afterward:

```bash
curl -X POST http://localhost:8082/api/lab/faults \
 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
 -d '{"mode":"none","remaining":0,"delayMs":0}'
curl -X POST http://localhost:8081/api/lab/circuit/reset -H "Authorization: Bearer $TOKEN"
```

### M09 — API contract/version change

Read the same order through v1 and v2. v1 must still expose amount/currency; v2 exposes total/value/currency. Run `./mvnw verify`; both consumer and provider fixture tests should pass. As an experiment, change the provider's `currency` field name without changing the consumer/contract fixture and rerun the tests; they should detect incompatibility. Revert that experiment before continuing.

### M10 — Connection failure versus read timeout

Stop the Payment application after creating a draft order, then checkout. Expect connection failure, bounded retries and a 503 response. Restart Payment and retry the same order. Contrast this with the `slow` fault, which accepts the connection but delays processing. For an actual connect-timeout experiment, use a controlled network-fault tool in your own lab; do not rely on an arbitrary public/unroutable IP, which can fail immediately or behave differently on your network.

## D. Java test inventory

| Class | Important assertions |
|---|---|
| `EventContractTest` | Unsupported event schema is rejected |
| `AuthorizationServerTest` | Real token issuance; wrong secret rejected |
| `InventoryStoreTest` | HQL receipt insertion; duplicate delivery no-op; unknown SKU |
| `PaymentStoreTest` | Same key/body returns same result; only one outbox row; changed payload conflicts; missing payment |
| `PaymentApiContractTest` | Provider contract response/headers; 401; 403; invalid JSON 400 |
| `OrderStoreTest` | HQL CRUD/pagination; frozen state; duplicate event; invalid sort/size |
| `PaymentConsumerContractTest` | WireMock provider contract; actual request headers/body and response decoding |
| `PaymentClientTest` | Retry count/key stability; nonretryable failures; malformed response; OPEN/half-open behavior; total deadline |
| Three `ApplicationContextTest` classes | Complete application configuration starts against isolated H2, with broker listeners stopped |
| `OrderStoreIT`, `InventoryStoreIT`, `PaymentStoreIT` | Same persistence assertions against PostgreSQL with migrations/validation |
| `PaymentStoreIT` concurrent test | Six transactions race on a fresh key; one winning payment ID/one new charge |

The tests use Mockito's subclass mock maker, so a restricted JVM does not need runtime self-attachment. Context tests deliberately do not require a Kafka broker; only the end-to-end scenarios prove actual messaging/tracing behavior.

## E. A failed test should tell you where to look

| Symptom | Inspect first |
|---|---|
| Build/compilation failure | Java version, Maven download access, pinned dependency versions |
| PostgreSQL IT cannot start | Docker daemon and image pull access |
| Every application fails readiness | PostgreSQL credentials, DB initialization, Flyway logs |
| HTTP 401 across services | Token age, issuer/JWKS configuration, Auth restart |
| Only writes return 403 | Token scopes |
| Checkout quickly returns CIRCUIT_OPEN | Payment fault state and circuit state |
| Payment exists, order remains PENDING | Outbox, Kafka group lag/DLT, Order listener logs |
| Duplicate sale after replay | Event ID stability and consumer unique constraint |
| Trace stops at scheduler | Saved traceparent and relay extraction |
| Prometheus target shows 401 | Refresh its file-mounted token |

Keep the database records while investigating; they are part of the evidence. `docker compose down` stops the lab without removing named volumes. Erasing volumes is not the first debugging step.

## E. Kafka automated regression tests

`./mvnw verify` starts an in-process KRaft broker on random ports; Docker is not required for this test. The shared Kafka configuration is used with independent test consumers. Full Order/Inventory business effects are additionally checked by the HQL tests and by S06/S08/S09 against the running applications.

| Test | Evidence |
|---|---|
| `KafkaDeliveryTest.independentGroupsBothReceiveTheSameEvent` | Both independent groups read the published event |
| `KafkaDeliveryTest.transientConsumerFailureSucceedsOnThirdAttempt` | Both consumers attempt 3 times, then succeed without a DLT record |
| `KafkaDeliveryTest.unsupportedVersionGoesToBothGroupSpecificDeadLetterTopics` | Invalid schema reaches both DLTs, no successful handling |
| `KafkaDeliveryTest.malformedJsonPreservesRawBytesAndDoesNotBlockLaterRecords` | Original bytes/key/header preserved; valid record behind poison in same partition succeeds |
| `KafkaDeliveryTest.exhaustedTransientFailureGoesToDeadLettersAfterThreeAttempts` | Both consumers exhaust 3 attempts; each writes its own DLT |
| `KafkaDeliveryTest.stoppedConsumerCatchesUpWhileOtherGroupContinues` | Order continues while Inventory is stopped; Inventory later reads the retained event |
| `OutboxRelayTest.failedKafkaPublishKeepsOutboxPendingAndLaterAcknowledgementMarksItSent` | Publish failure leaves sent=false; later success marks sent=true, with the same key/event |

### M11 — Topic partitions and independent group offsets

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --describe --topic payment.captured.v1
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group order-service
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group inventory-service
```

Expected: 3 source partitions, replication factor 1, and separate offsets for the two groups. Both groups normally reach lag 0 after a checkout. Consumption does not delete the topic records.

### M12 — Inspect the saga boundary

Read `KafkaConfiguration.paymentEventErrorHandler`, `OrderStore.markPaid` and `PaymentStore.charge`. Confirm that the error path publishes to a DLT and contains no refund or stock-release operation. A dead-letter record does not invoke a refund. The Payment row can remain CAPTURED while an Order is PENDING. This verifies the documented boundary: **no saga/compensation has been implemented**. Read notes.md section 22 for the required saga extension.
