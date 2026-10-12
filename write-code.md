# Write the Spring Boot Kafka project yourself — step by step

Pratik, use this as your writing checklist. Keep the **Kafka edition** of `spring_boot_communication_lab.zip` on your second screen. Write into a separate empty folder on your laptop. Follow the numbered files in order; the short instruction under each file tells you what to focus on while typing it.

This guide accounts for **every one of the 114 files in the reference ZIP**. Application code, tests and configuration are writing tasks. Runtime tokens, build reports and checksums are marked **Generate** because they must reflect your own run.

## Before you start

1. Create a folder such as `communication-lab-practice` and open it in your IDE. All paths below are relative to that folder, which becomes your Maven project root.
2. Keep the reference folder separate and read-only while you learn. Run one local stack at a time because both projects use the same ports.
3. Have Java 17+, Docker with Compose v2 and Python 3.9+ ready. Use the dependency versions from the reference POMs throughout this exercise.
4. For each Java file, type its package, imports, declaration, fields/constructor and methods. Write your own explanatory comments as you go.
5. Finish a phase before running its checkpoint. Early commands deliberately build selected modules; the optional Discovery application is written later.
6. After each phase, close the reference and explain the checkpoint question aloud. Then compare your own code with the reference and fix your typing mistakes.

**The application you are recreating:** five runnable Spring Boot applications plus one shared module. It uses PostgreSQL entities with explicit HQL, OAuth/JWT, REST clients, Resilience4j, Kafka, an outbox, tracing and tests. A saga and transport encryption are not implemented in this reference.

## Your route through the project

| Phases | What you will have working |
|---|---|
| 01–03 | Maven structure, infrastructure and a real token issuer |
| 04–06 | Shared API rules, Inventory and Payment persistence/API |
| 07–09 | Order CRUD and synchronous service-to-service calls |
| 10–11 | Kafka event delivery, eventual completion and failure scenarios |
| 12–15 | PostgreSQL integration tests, discovery, load balancing, metrics and tracing |
| 16–17 | Kubernetes exercise, your own notes and final verification |

The order stays `PENDING` during Phase 09 intentionally: you will see the exact difference made by adding the outbox publisher and Kafka listeners in Phase 10.

## Phase 01 — Create the build structure

Create the six module folders first: `lab-common`, `auth-service`, `inventory-service`, `payment-service`, `order-service`, and `discovery-server`. Write the complete reference POMs, including test dependencies.

- [ ] **001. Write `pom.xml`**

  Write the parent, pinned versions, modules, dependency-management BOMs and integration-test profile. Preserve the reference BOM order.

- [ ] **002. Write `lab-common/pom.xml`**

  Shared web/security/JPA/Kafka/tracing dependencies; this module is a library.

- [ ] **003. Write `auth-service/pom.xml`**

  Authorization-server dependencies and the executable Spring Boot Maven plugin.

- [ ] **004. Write `inventory-service/pom.xml`**

  Depend on lab-common; add discovery and test dependencies.

- [ ] **005. Write `payment-service/pom.xml`**

  Depend on lab-common; add discovery and test dependencies.

- [ ] **006. Write `order-service/pom.xml`**

  Add the HTTP clients, OAuth client, Resilience4j, discovery and client-testing dependencies.

- [ ] **007. Write `discovery-server/pom.xml`**

  Add the Eureka server dependency and executable Spring Boot plugin.

- [ ] **008. Write `.gitignore`**

  Write the reference exclusions for generated build output, local settings and logs.

- [ ] **009. Write `.dockerignore`**

  Write the reference exclusions so Docker build context stays small.

- [ ] **010. Write `.mvn/wrapper/maven-wrapper.properties`**

  Write the pinned Maven distribution settings.

- [ ] **011. Write `mvnw`**

  Type the reference Unix Maven launcher. This generated script supplies Maven bootstrapping; focus on matching its content and executable permission.

- [ ] **012. Write `mvnw.cmd`**

  Type the reference Windows Maven launcher for file completeness; your Mac uses mvnw.

- [ ] **013. Write `lab-common/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`**

  Create this exact filename without adding .txt, and type the reference Mockito setting. Each module has its own test resources.

- [ ] **014. Write `auth-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`**

  Create this exact filename without adding .txt, and type the reference Mockito setting. Each module has its own test resources.

- [ ] **015. Write `inventory-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`**

  Create this exact filename without adding .txt, and type the reference Mockito setting. Each module has its own test resources.

- [ ] **016. Write `payment-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`**

  Create this exact filename without adding .txt, and type the reference Mockito setting. Each module has its own test resources.

- [ ] **017. Write `order-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`**

  Create this exact filename without adding .txt, and type the reference Mockito setting. Each module has its own test resources.

- [ ] **018. Write `discovery-server/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`**

  Create this exact filename without adding .txt, and type the reference Mockito setting. Each module has its own test resources.

**Checkpoint:** On macOS/Linux, type `chmod +x mvnw`, then run `./mvnw -DskipTests compile`. Maven should recognize all six modules. **Explain:** why is lab-common a library while the other five modules are applications?

## Phase 02 — Write infrastructure before starting services

Write the complete files, but start only PostgreSQL, Kafka and Zipkin at this checkpoint.

- [ ] **019. Write `infra/postgres/init.sql`**

  Create the three database owners and databases. Schema/table creation belongs to each service’s Flyway migration, which you write later.

- [ ] **020. Write `Dockerfile`**

  Write the Maven build stage and Java runtime stage. Follow how SERVICE chooses the application JAR.

- [ ] **021. Write `compose.yml`**

  Write PostgreSQL, Zipkin, Auth, Inventory, Payment, Order and Kafka definitions, then volumes. Match ports, environment variables, health checks and dependency names.

**Checkpoint:** Run `docker compose up -d postgres kafka zipkin`, then `docker compose ps`. PostgreSQL and Kafka should become healthy. **Explain:** which hostname is used from a container and which address is used from your laptop? This edition uses Compose project name `communication-lab-kafka`.

## Phase 03 — Build Auth first

A working token issuer lets you test every protected service as soon as you write it.

- [ ] **022. Write `auth-service/src/main/java/dev/pratik/lab/auth/AuthApplication.java`**

  Write the application entry point and package declaration.

- [ ] **023. Write `auth-service/src/main/resources/application.yml`**

  Write its application name, port 9000 and management settings.

- [ ] **024. Write `auth-service/src/main/java/dev/pratik/lab/auth/AuthorizationConfig.java`**

  Write endpoint security, registered clients/scopes, client-credentials grant settings, RSA signing keys, issuer and JWT audience. Keep the three client identities distinct.

- [ ] **025. Write `auth-service/src/test/java/dev/pratik/lab/auth/AuthorizationServerTest.java`**

  Write the successful real token grant and wrong-secret rejection tests.

- [ ] **026. Write `scripts/get_token.py`**

  Write the client selection, token HTTP request, response handling and optional file output. Understand the Basic client credentials sent to the token endpoint.

**Checkpoint:** Run `./mvnw -pl auth-service package`. Start `java -jar auth-service/target/auth-service-1.0.0.jar` in its own terminal. From another terminal run `python3 scripts/get_token.py`. **Explain:** who issues the JWT, and whose identity does a client-credentials token represent?

## Phase 04 — Write the shared HTTP and event contracts

Finish these dependencies before writing business controllers and stores. Kafka transport configuration comes in Phase 10.

- [ ] **027. Write `lab-common/src/main/java/dev/pratik/lab/common/ApiError.java`**

  Write the common error-response record, including correlation and trace IDs.

- [ ] **028. Write `lab-common/src/main/java/dev/pratik/lab/common/ApiException.java`**

  Write the exception with HTTP status and machine-readable error code.

- [ ] **029. Write `lab-common/src/main/java/dev/pratik/lab/common/CorrelationFilter.java`**

  Write header validation/generation, request context, response headers and MDC cleanup. Trace ID and correlation ID have different jobs.

- [ ] **030. Write `lab-common/src/main/java/dev/pratik/lab/common/ErrorAdvice.java`**

  Write body construction, individual exception handlers and the response helper. Match validation, conflict, security and framework HTTP statuses.

- [ ] **031. Write `lab-common/src/main/java/dev/pratik/lab/common/SecurityConfig.java`**

  Write JWT decoding and issuer/audience validation, stateless security, scope restrictions, and consistent 401/403 responses.

- [ ] **032. Write `lab-common/src/main/java/dev/pratik/lab/common/OpenApiConfig.java`**

  Write the API documentation metadata and bearer-auth definition.

- [ ] **033. Write `lab-common/src/main/java/dev/pratik/lab/common/PaymentCaptured.java`**

  Write the immutable event fields and validate(). Pay attention to event ID, order/payment IDs and schema version.

- [ ] **034. Write `lab-common/src/main/resources/contracts/payment-v1-response.json`**

  Write the fixed provider-response fixture. This is test data for the public contract, not an entity or a real payment.

- [ ] **035. Write `lab-common/src/test/java/dev/pratik/lab/common/EventContractTest.java`**

  Write the unsupported-schema-version rejection test.

**Checkpoint:** Run `./mvnw -pl lab-common test`. **Explain:** which object crosses HTTP, which object crosses Kafka, and why neither is a JPA entity?

## Phase 05 — Build Inventory as your first business service

Write each complete file. The sales-recording method can exist before its Kafka caller is written.

- [ ] **036. Write `inventory-service/src/main/java/dev/pratik/lab/inventory/InventoryApplication.java`**

  Write the entry point and scan both the inventory and common packages.

- [ ] **037. Write `inventory-service/src/main/resources/application.yml`**

  Write the whole reference configuration: port 8083, PostgreSQL, JPA/Flyway, auth, Kafka, discovery defaults and observability. Revisit Kafka settings in Phase 10.

- [ ] **038. Write `inventory-service/src/main/resources/db/migration/V1__inventory.sql`**

  Write product and sales-receipt tables, keys/constraints and the two seed products.

- [ ] **039. Write `inventory-service/src/main/java/dev/pratik/lab/inventory/Product.java`**

  Write entity/table mapping, SKU ID, price/currency fields, protected constructor and getters.

- [ ] **040. Write `inventory-service/src/main/java/dev/pratik/lab/inventory/SalesReceipt.java`**

  Write receipt mapping and event/order/payment identifiers. Compare its columns with the migration.

- [ ] **041. Write `inventory-service/src/main/java/dev/pratik/lab/inventory/InventoryStore.java`**

  Write ProductView, then product(), recordSale(), and sales(). Use the explicit HQL from the reference and bind every value parameter.

- [ ] **042. Write `inventory-service/src/main/java/dev/pratik/lab/inventory/InventoryController.java`**

  Write the product and sales endpoints, constructor injection and read-scope requirements.

- [ ] **043. Write `inventory-service/src/test/java/dev/pratik/lab/inventory/InventoryStoreTest.java`**

  Write unknown-product and duplicate-sale tests against H2.

- [ ] **044. Write `inventory-service/src/test/java/dev/pratik/lab/inventory/ApplicationContextTest.java`**

  Write the full bean-graph startup test with its isolated H2 and disabled Kafka-startup settings.

**Checkpoint:** Run `./mvnw -pl inventory-service -am package`. Start its JAR with `--spring.profiles.active=lab`. Use Swagger on port 8083, authorize with your token, and GET `/api/v1/inventory/BOOK-1`; expect price 500. **Explain:** why does Flyway create tables while Hibernate validates them?

## Phase 06 — Build Payment and its local transaction

Write persistence and HTTP first. The outbox row exists now; the publisher will be written in Phase 10.

- [ ] **045. Write `payment-service/src/main/java/dev/pratik/lab/payment/PaymentApplication.java`**

  Write the entry point, shared-package scan and scheduling enablement. The relay bean is added later.

- [ ] **046. Write `payment-service/src/main/resources/application.yml`**

  Write the complete reference configuration, including port 8082 and its separately owned database.

- [ ] **047. Write `payment-service/src/main/resources/db/migration/V1__payments.sql`**

  Write payments and outbox tables, unique constraints and indexes. Identify which constraints prevent duplicate charges.

- [ ] **048. Write `payment-service/src/main/java/dev/pratik/lab/payment/PaymentDtos.java`**

  Write ChargeRequest, PaymentResponse and ChargeResult, including validation annotations.

- [ ] **049. Write `payment-service/src/main/java/dev/pratik/lab/payment/PaymentRecord.java`**

  Write the entity fields, ID, column mappings and getters. Compare amount precision and uniqueness with the migration.

- [ ] **050. Write `payment-service/src/main/java/dev/pratik/lab/payment/OutboxEvent.java`**

  Write stored JSON, traceparent, correlation ID, sent flag, attempts and creation time.

- [ ] **051. Write `payment-service/src/main/java/dev/pratik/lab/payment/PaymentStore.java`**

  Write charge() in this order: key validation; conflict-safe insert/replay lookup; payload comparison; event JSON; outbox insert; response. Then write byOrder(), paymentCount(), and view().

- [ ] **052. Write `payment-service/src/main/java/dev/pratik/lab/payment/FaultInjector.java`**

  Write fault configuration, before()/after(), delay handling and call counts. The after-commit delay is a deliberate ambiguous-response test.

- [ ] **053. Write `payment-service/src/main/java/dev/pratik/lab/payment/PaymentController.java`**

  Write payment creation, lookup and instance endpoints. Match validation, scopes, replay headers and 201-versus-200 behavior.

- [ ] **054. Write `payment-service/src/main/java/dev/pratik/lab/payment/PaymentLabController.java`**

  Write the protected lab fault, outbox inspection and replay endpoints. Preserve the lab profile restriction.

- [ ] **055. Write `payment-service/src/test/java/dev/pratik/lab/payment/PaymentStoreTest.java`**

  Write same-key replay, changed-body conflict and missing-payment tests.

- [ ] **056. Write `payment-service/src/test/java/dev/pratik/lab/payment/PaymentApiContractTest.java`**

  Write response-contract, missing-token, missing-scope and invalid-input tests.

- [ ] **057. Write `payment-service/src/test/java/dev/pratik/lab/payment/ApplicationContextTest.java`**

  Write isolated startup configuration and the bean-graph test.

**Checkpoint:** Run `./mvnw -pl payment-service -am package`. Start the Payment JAR with `--spring.profiles.active=lab` and confirm health on port 8082. Use the tests to study charges now; perform the first live charge through Order in Phase 09 so its order exists. **Explain:** why must payment and outbox rows commit together?

## Phase 07 — Write Order data and state changes

Build the local Order model before its network clients.

- [ ] **058. Write `order-service/src/main/java/dev/pratik/lab/order/OrderApplication.java`**

  Write the entry point, common-package scan and Feign enablement.

- [ ] **059. Write `order-service/src/main/resources/application.yml`**

  Write port 8081, Order database, OAuth service registration, downstream URLs, Kafka, discovery and observability settings.

- [ ] **060. Write `order-service/src/main/resources/db/migration/V1__orders.sql`**

  Write order and processed-event tables, constraints and indexes.

- [ ] **061. Write `order-service/src/main/java/dev/pratik/lab/order/OrderDtos.java`**

  Write the request, note, response and page records, plus the consumer-side Inventory/Payment DTOs.

- [ ] **062. Write `order-service/src/main/java/dev/pratik/lab/order/PurchaseOrder.java`**

  Write entity fields and mappings for DRAFT/PENDING/PAID, amount and payment ID.

- [ ] **063. Write `order-service/src/main/java/dev/pratik/lab/order/ProcessedEvent.java`**

  Write the event-ID entity used for deduplication.

- [ ] **064. Write `order-service/src/main/java/dev/pratik/lab/order/OrderStore.java`**

  Write require()/view(), then create/get/page, replace/note/delete and notEditable, then beginCheckout()/markPaid(). Preserve transactions, allowed sort fields, HQL predicates and persistence-context clears.

- [ ] **065. Write `order-service/src/test/java/dev/pratik/lab/order/OrderStoreTest.java`**

  Write CRUD/pagination, frozen-order/duplicate-event and invalid-query tests.

**Checkpoint:** Run `./mvnw -pl order-service -am test`. Start the actual Order application after Phase 09. **Explain:** why does beginCheckout() freeze the amount before any network call?

## Phase 08 — Write the outgoing HTTP calls and resilience

Follow this dependency order so every client has its configuration, token supplier and failure policy available.

- [ ] **066. Write `order-service/src/main/java/dev/pratik/lab/order/DownstreamFailure.java`**

  Write the remote status, retryable flag, Retry-After field and classification helpers.

- [ ] **067. Write `order-service/src/main/java/dev/pratik/lab/order/ResilienceConfiguration.java`**

  Write paymentRetry() and paymentBreaker(). Type the actual retry predicate, backoff/jitter and breaker thresholds from the reference.

- [ ] **068. Write `order-service/src/main/java/dev/pratik/lab/order/ClientConfiguration.java`**

  Write the bounded WebClient connection pool, connector/timeouts, RestClient beans, RestTemplate bean and request-factory helper.

- [ ] **069. Write `order-service/src/main/java/dev/pratik/lab/order/ServiceTokens.java`**

  Write the authorized-client-manager configuration and accessToken(). Follow token caching and token-endpoint timeout settings.

- [ ] **070. Write `order-service/src/main/java/dev/pratik/lab/order/InventoryClient.java`**

  Write the authorized product lookup and its DTO/error mapping.

- [ ] **071. Write `order-service/src/main/java/dev/pratik/lab/order/PaymentClient.java`**

  Write constructor dependencies, charge(), call(), and isTimeout(). Follow POST URL, JSON, headers, error mapping, deadline, context propagation and block().

- [ ] **072. Write `order-service/src/main/java/dev/pratik/lab/order/DownstreamAdvice.java`**

  Write downstream-error and open-circuit HTTP responses, preserving useful Retry-After information.

- [ ] **073. Write `order-service/src/test/java/dev/pratik/lab/order/PaymentClientTest.java`**

  Write the local HTTP stub setup, retry/key test, nonretryable-status test, malformed-response test, breaker tests and deadline test.

- [ ] **074. Write `order-service/src/test/java/dev/pratik/lab/order/PaymentConsumerContractTest.java`**

  Write the WireMock consumer-contract test and required-field/header assertions.

**Checkpoint:** Run `./mvnw -pl order-service -am test`. **Explain:** why is this WebClient flow synchronous, why does a retry reuse its key, and why does the circuit breaker count one final checkout outcome?

## Phase 09 — Finish Order HTTP endpoints and see the synchronous flow

Everything needed for the controller’s constructor is now written.

- [ ] **075. Write `order-service/src/main/java/dev/pratik/lab/order/OrderController.java`**

  Write constructor injection, create/get/page, PUT/PATCH/DELETE, then checkout(). Pay attention to scopes, Location headers and 201/202/204.

- [ ] **076. Write `order-service/src/main/java/dev/pratik/lab/order/OrderV2Controller.java`**

  Write the Money/OrderV2 response records and v2 mapping. Keep the v1 contract intact.

- [ ] **077. Write `order-service/src/main/java/dev/pratik/lab/order/PaymentFeignClient.java`**

  Write the named-service interface and explicit authorization/correlation headers.

- [ ] **078. Write `order-service/src/main/java/dev/pratik/lab/order/ClientLabController.java`**

  Write four-client comparison, token forwarding, circuit state and reset endpoints. Preserve lab profile and bean qualifiers.

- [ ] **079. Write `order-service/src/test/java/dev/pratik/lab/order/ApplicationContextTest.java`**

  Write the isolated full Order context test.

- [ ] **080. Write `requests.http`**

  Type the complete manual request collection. Supply fresh token/order values when running requests; begin with CRUD and one checkout.

**Checkpoint:** Run `./mvnw -pl order-service -am package`, then start Order’s JAR with `--spring.profiles.active=lab`. Keep Auth, Inventory and Payment running. Use the sequence below.

1. Create a BOOK-1 order; expect **201 / DRAFT** and amount 500 for quantity 1.
2. On a draft, exercise GET, PUT and PATCH. Exercise DELETE on a different draft; expect 204.
3. Checkout an existing draft; expect **202 / PENDING**. Look up its payment; it should be CAPTURED.
4. Inspect Payment’s outbox endpoint; the event should be unsent. The order stays PENDING because you have not written the relay/listeners yet.
5. Retry checkout with the same order ID and verify the Payment identity is unchanged.

**Explain:** which calls wait for an HTTP response, and which missing component will eventually change PENDING to PAID? Save this order ID for Phase 10. Leave fault injection in its default `none` state.

## Phase 10 — Add the asynchronous Kafka flow

The event contract and database methods already exist. Now connect them to the broker.

- [ ] **081. Write `lab-common/src/main/java/dev/pratik/lab/common/PaymentEventCodec.java`**

  Write JSON parsing and event validation. Preserve a String payload so bad JSON can reach a dead-letter topic.

- [ ] **082. Write `lab-common/src/main/java/dev/pratik/lab/common/KafkaConfiguration.java`**

  Write topic/group constants, three-partition topics and group-specific DLTs. Then write retry backoff, permanent-error classification and checked DLT publication.

- [ ] **083. Write `payment-service/src/main/java/dev/pratik/lab/payment/OutboxRelay.java`**

  Write the constructor, scheduled pending-row query and publish(). Restore trace context, key by order ID, await Kafka acknowledgement, then update sent/attempts using HQL.

- [ ] **084. Write `order-service/src/main/java/dev/pratik/lab/order/OrderPaymentListener.java`**

  Write the Order consumer group, decode(), correlation context and transactional markPaid() call.

- [ ] **085. Write `inventory-service/src/main/java/dev/pratik/lab/inventory/PaymentEventListener.java`**

  Write the independent Inventory group, decode(), correlation context and transactional recordSale() call.

- [ ] **086. Write `lab-common/src/test/java/dev/pratik/lab/common/KafkaDeliveryTest.java`**

  Write the embedded KRaft test setup and all six scenarios: both groups, transient retry, exhausted retry, bad schema, malformed JSON and stopped-consumer catch-up.

- [ ] **087. Write `payment-service/src/test/java/dev/pratik/lab/payment/OutboxRelayTest.java`**

  Write failed-publish/pending and later-acknowledgement/sent assertions. Verify both attempts preserve the same event and order key.

**Checkpoint:** Run `./mvnw -pl inventory-service,payment-service,order-service -am package`. Stop and restart all three business-service JARs so each loads the new common module. Keep Auth and infrastructure running. The saved PENDING order should become PAID; Inventory should record its sale.

Reopen all three `application.yml` files and connect the settings to the code you just typed: bootstrap address, String serializers, `acks=all`, producer idempotence, disabled auto-commit, RECORD acknowledgement, earliest offset reset and observations.

**Explain:** a Kafka broker acknowledgement, a local DB commit and a consumer-group offset commit are three different events. Identify the duplicate-delivery gap between them and the constraint that protects each consumer.

## Phase 11 — Write executable scenarios and test the whole stack

Switch to Compose here so the broker stop/start and poison-event scripts target the running lab consistently.

- [ ] **088. Write `scripts/poison_event.py`**

  Write the invalid-schema and malformed-JSON options, keyed Kafka CLI publish and diagnostic output.

- [ ] **089. Write `scripts/scenarios.py`**

  Write imports/constants, http(), check(), wait_until(), then run(): token/CRUD/client checks; payment/idempotency/events; faults/retry/circuit; contracts/metrics; optional tracing/LB/outage; cleanup; argument parser.

- [ ] **090. Write `test.md`**

  Write your test checklist in your own words using the reference IDs S01–S18 and M01–M12. For each, record setup, action, expected result and the code to inspect.

**Checkpoint:** Stop the four host-run application JARs, then run `docker compose up --build -d` from your handwritten project. Run `python3 scripts/scenarios.py`, followed by the optional checks below when ready.

- Run `python3 scripts/scenarios.py --traces` to include trace continuity.
- Run `python3 scripts/scenarios.py --traces --disrupt` to stop/restart Kafka and verify pending-outbox recovery.
- Run `python3 scripts/poison_event.py` and its `--malformed` variant; follow M01 to inspect both DLTs.
- Follow M02 to stop Inventory, observe group lag and let it catch up after restart.

**Explain:** which retries are Resilience4j HTTP retries, which are Kafka listener retries, and why does a dead-letter event not refund a payment?

## Phase 12 — Verify PostgreSQL rather than relying only on H2

Write the integration subclasses exactly, including Testcontainers properties and migration settings. Docker must be available for this checkpoint.

- [ ] **091. Write `inventory-service/src/test/java/dev/pratik/lab/inventory/InventoryStoreIT.java`**

  Write the PostgreSQL container and dynamic datasource configuration, reusing the Inventory persistence scenarios.

- [ ] **092. Write `payment-service/src/test/java/dev/pratik/lab/payment/PaymentStoreIT.java`**

  Write PostgreSQL configuration plus the concurrent idempotency test. Understand the unique constraint that arbitrates simultaneous requests.

- [ ] **093. Write `order-service/src/test/java/dev/pratik/lab/order/OrderStoreIT.java`**

  Write PostgreSQL configuration and reuse the Order HQL/state tests.

**Checkpoint:** Run `./mvnw -pl inventory-service,payment-service,order-service -am -P integration verify`. **Explain:** why do passing H2 tests not prove PostgreSQL-specific SQL generation, migrations and concurrent constraints?

## Phase 13 — Add three-instance load balancing

The base checkout remains the deterministic WebClient path; the Feign comparison demonstrates load balancing.

- [ ] **094. Write `order-service/src/main/resources/application-lb.yml`**

  Write the three named Payment instance addresses.

- [ ] **095. Write `compose.lb.yml`**

  Write the Order profile/addresses and the two additional Payment containers. All Payment instances share the Payment DB and Kafka broker.

**Checkpoint:** Run `docker compose -f compose.yml -f compose.lb.yml up --build -d`, then `python3 scripts/scenarios.py --lb`. **Explain:** which client uses the named-service instance list, and which client calls the configured direct Payment URL?

## Phase 14 — Add Eureka discovery

Write this phase for the complete project even if you postpone running the optional registry exercise.

- [ ] **096. Write `discovery-server/src/main/java/dev/pratik/lab/discovery/DiscoveryApplication.java`**

  Write the entry point and Eureka server enablement.

- [ ] **097. Write `discovery-server/src/main/resources/application.yml`**

  Write port 8761 and standalone-server settings.

- [ ] **098. Write `order-service/src/main/resources/application-eureka.yml`**

  Write the service’s Eureka-enabled profile exactly, preserving the reference discovery behavior.

- [ ] **099. Write `payment-service/src/main/resources/application-eureka.yml`**

  Write the service’s Eureka-enabled profile exactly, preserving the reference discovery behavior.

- [ ] **100. Write `inventory-service/src/main/resources/application-eureka.yml`**

  Write the service’s Eureka-enabled profile exactly, preserving the reference discovery behavior.

- [ ] **101. Write `compose.eureka.yml`**

  Write the registry container and profile overrides for the application instances.

**Checkpoint:** All five application entry points now exist. Run `./mvnw verify`, then start the three-file Eureka Compose command from the reference README. Inspect registrations on port 8761 and repeat the Feign instance exercise. **Explain:** discovery finds instances; load balancing chooses one.

## Phase 15 — Connect metrics and tracing to observed behavior

The Java observation calls and YAML settings are already present. Here you write the monitoring setup and inspect a real checkout.

- [ ] **102. Write `infra/prometheus/prometheus.yml`**

  Write scrape targets and the bearer-token-file configuration. Match the container service names.

- [ ] **103. Generate `infra/prometheus/token.txt`**

  Run `python3 scripts/get_token.py --file infra/prometheus/token.txt` after Auth is running. This file must contain your fresh runtime token, not the reference placeholder or an expired token.

- [ ] **104. Write `compose.metrics.yml`**

  Write the Prometheus service and its config/token mounts.

**Checkpoint:** Run `docker compose -f compose.yml -f compose.metrics.yml up -d`. Open Prometheus on port 9090 and Zipkin on 9411. Trace one checkout through Order, Payment, the outbox scheduler and both consumer groups; inspect HTTP latency and timeout metrics.

Revisit `CorrelationFilter`, `ClientConfiguration`, `PaymentStore.charge`, `OutboxRelay.publish` and the Kafka observation settings. Point to where the trace parent is captured, stored and restored. Refresh the Prometheus token after expiry or an Auth restart.

Use the complete Compose file list for the optional mode you currently run. When changing modes, follow the README stop/start instructions so old extra containers do not confuse your observations.

## Phase 16 — Write the Kubernetes exercise

This is the last infrastructure layer; write it after you can run and explain the local Compose flow.

- [ ] **105. Write `k8s/lab.yml`**

  Write documents in reference order: namespace; DB secret/init ConfigMap; PVCs; PostgreSQL/Kafka workloads and Services; Zipkin; Auth; Inventory; three Payment replicas; Order. Match selectors, ports, env values and probes.

- [ ] **106. Write `k8s/README.md`**

  Write image build/load commands, deployment, port forwarding and inspection steps. Explain Service DNS and shared-database outbox coordination in your own words.

**Checkpoint:** If you have a local cluster, follow your k8s/README.md and run an ordinary checkout. **Explain:** how do Service DNS, ready endpoints and multiple Payment pods work together? The deterministic fault suite belongs to the basic single-Payment Compose setup.

## Phase 17 — Write your explanation and produce your own evidence

This finishes the remaining reference files. Use your own descriptions and your actual results.

- [ ] **107. Write `README.md`**

  Write the startup instructions, ports, first requests, IDE/Docker options, optional profiles and troubleshooting for the project you just built.

- [ ] **108. Write `notes.md`**

  Write your what/why/how notes in the reference topic order. Link each concept to the class or configuration you personally typed.

- [ ] **109. Write `KAFKA.md`**

  Write topic/group/partition/offset explanations, producer/listener walkthroughs, retry/DLT behavior and the explicit no-saga boundary.

- [ ] **110. Write `CODE_MAP.md`**

  List every application class, test and configuration with a one-line purpose. Use your own paths and method names to catch missing files.

- [ ] **111. Generate `verification/build-summary.txt`**

  After all handwritten files exist, run `./mvnw clean verify` and retain its real reactor summary here.

- [ ] **112. Generate `verification/test-summary.json`**

  Record test totals and per-suite results from your own target/surefire-reports XML files. The reference baseline is 32 tests; record failures honestly if your run differs.

- [ ] **113. Write `VERIFICATION.md`**

  Write what you actually ran, what passed and what remains untested. Keep Maven, PostgreSQL/Testcontainers, Compose, tracing and Kubernetes evidence separate.

- [ ] **114. Generate `FILE_INDEX.txt`**

  Generate SHA-256 hashes for deliverable source/config/docs after finishing, excluding target directories and FILE_INDEX.txt itself. Its purpose is package integrity, not business behavior.

**Checkpoint:** Your handwritten version should build, the selected scenario groups should pass, and every reference file should have a corresponding file or freshly generated output. Stop at a failing checkpoint and diagnose the first error before changing unrelated code.

## Final explanation check — close the reference screen

- [ ] Explain one order creation: HTTP request → DTO validation/security → Inventory price lookup → Order HQL insert → response DTO.
- [ ] Explain checkout: freeze PENDING → obtain service token → WebClient POST with one stable idempotency key → Payment and outbox commit → HTTP response.
- [ ] Explain asynchronous completion: outbox relay → Kafka record → independent Order/Inventory groups → DB effects → offset commits.
- [ ] Locate the exact code that handles a timeout after Payment already committed.
- [ ] Locate the unique constraints that prevent repeated payment/sales effects.
- [ ] Explain CLOSED, OPEN and HALF_OPEN using the actual numbers in your Resilience4j configuration.
- [ ] Demonstrate a bad event reaching the correct DLT and a stopped consumer catching up.
- [ ] Follow one trace across HTTP, the scheduled outbox and Kafka consumption.
- [ ] Distinguish a signed JWT from encrypted network traffic; identify the local lab’s plaintext connections.
- [ ] Explain why this project has event-driven communication but **no saga compensation**.

When you can explain those flows from your own code, you have connected the individual files into one working system.