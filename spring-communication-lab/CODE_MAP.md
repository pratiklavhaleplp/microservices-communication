# Code map

Use the topic explanations in [notes.md](notes.md), then open the actual files below. Names are stable references; no line numbers are hard-coded.

## lab-common

| File | Methods / purpose |
|---|---|
| [ApiError.java](lab-common/src/main/java/dev/pratik/lab/common/ApiError.java) | ApiError |
| [ApiException.java](lab-common/src/main/java/dev/pratik/lab/common/ApiException.java) | status, code |
| [CorrelationFilter.java](lab-common/src/main/java/dev/pratik/lab/common/CorrelationFilter.java) | current, doFilterInternal |
| [ErrorAdvice.java](lab-common/src/main/java/dev/pratik/lab/common/ErrorAdvice.java) | body, response |
| [OpenApiConfig.java](lab-common/src/main/java/dev/pratik/lab/common/OpenApiConfig.java) | Application configuration / model |
| [PaymentCaptured.java](lab-common/src/main/java/dev/pratik/lab/common/PaymentCaptured.java) | PaymentCaptured, validate |
| [KafkaConfiguration.java](lab-common/src/main/java/dev/pratik/lab/common/KafkaConfiguration.java) | Application configuration / model |
| [SecurityConfig.java](lab-common/src/main/java/dev/pratik/lab/common/SecurityConfig.java) | Application configuration / model |
| [payment-v1-response.json](lab-common/src/main/resources/contracts/payment-v1-response.json) | Configuration, migration or wire-contract fixture |
| [EventContractTest.java](lab-common/src/test/java/dev/pratik/lab/common/EventContractTest.java) | Test: EventContractTest |

## auth-service

| File | Methods / purpose |
|---|---|
| [AuthApplication.java](auth-service/src/main/java/dev/pratik/lab/auth/AuthApplication.java) | main |
| [AuthorizationConfig.java](auth-service/src/main/java/dev/pratik/lab/auth/AuthorizationConfig.java) | client |
| [application.yml](auth-service/src/main/resources/application.yml) | Configuration, migration or wire-contract fixture |
| [AuthorizationServerTest.java](auth-service/src/test/java/dev/pratik/lab/auth/AuthorizationServerTest.java) | Test: AuthorizationServerTest |

## order-service

| File | Methods / purpose |
|---|---|
| [ClientConfiguration.java](order-service/src/main/java/dev/pratik/lab/order/ClientConfiguration.java) | factory |
| [ClientLabController.java](order-service/src/main/java/dev/pratik/lab/order/ClientLabController.java) | compare, forward, state, reset |
| [DownstreamAdvice.java](order-service/src/main/java/dev/pratik/lab/order/DownstreamAdvice.java) | Application configuration / model |
| [DownstreamFailure.java](order-service/src/main/java/dev/pratik/lab/order/DownstreamFailure.java) | remoteStatus, retryable, retryAfter, response, transport |
| [InventoryClient.java](order-service/src/main/java/dev/pratik/lab/order/InventoryClient.java) | product |
| [OrderApplication.java](order-service/src/main/java/dev/pratik/lab/order/OrderApplication.java) | main |
| [OrderController.java](order-service/src/main/java/dev/pratik/lab/order/OrderController.java) | create, get, page, replace, note, delete, checkout |
| [OrderDtos.java](order-service/src/main/java/dev/pratik/lab/order/OrderDtos.java) | OrderRequest, NoteRequest, OrderResponse, OrderPage, ProductResponse, ChargeRequest, PaymentResponse |
| [OrderPaymentListener.java](order-service/src/main/java/dev/pratik/lab/order/OrderPaymentListener.java) | receive |
| [OrderStore.java](order-service/src/main/java/dev/pratik/lab/order/OrderStore.java) | create, get, require, page, replace, note, delete, notEditable, beginCheckout, markPaid, view |
| [OrderV2Controller.java](order-service/src/main/java/dev/pratik/lab/order/OrderV2Controller.java) | Money, OrderV2, get |
| [PaymentClient.java](order-service/src/main/java/dev/pratik/lab/order/PaymentClient.java) | charge, call, isTimeout |
| [PaymentFeignClient.java](order-service/src/main/java/dev/pratik/lab/order/PaymentFeignClient.java) | Application configuration / model |
| [ProcessedEvent.java](order-service/src/main/java/dev/pratik/lab/order/ProcessedEvent.java) | getEventId, getProcessedAt |
| [PurchaseOrder.java](order-service/src/main/java/dev/pratik/lab/order/PurchaseOrder.java) | getId, getSku, getQuantity, getAmount, getCurrency, getNote, getStatus, getPaymentId, getCreatedAt |
| [ResilienceConfiguration.java](order-service/src/main/java/dev/pratik/lab/order/ResilienceConfiguration.java) | Application configuration / model |
| [ServiceTokens.java](order-service/src/main/java/dev/pratik/lab/order/ServiceTokens.java) | accessToken |
| [application-eureka.yml](order-service/src/main/resources/application-eureka.yml) | Configuration, migration or wire-contract fixture |
| [application-lb.yml](order-service/src/main/resources/application-lb.yml) | Configuration, migration or wire-contract fixture |
| [application.yml](order-service/src/main/resources/application.yml) | Configuration, migration or wire-contract fixture |
| [V1__orders.sql](order-service/src/main/resources/db/migration/V1__orders.sql) | Configuration, migration or wire-contract fixture |
| [ApplicationContextTest.java](order-service/src/test/java/dev/pratik/lab/order/ApplicationContextTest.java) | Test: ApplicationContextTest |
| [OrderStoreIT.java](order-service/src/test/java/dev/pratik/lab/order/OrderStoreIT.java) | Test: OrderStoreIT |
| [OrderStoreTest.java](order-service/src/test/java/dev/pratik/lab/order/OrderStoreTest.java) | Test: OrderStoreTest |
| [PaymentClientTest.java](order-service/src/test/java/dev/pratik/lab/order/PaymentClientTest.java) | Test: PaymentClientTest |
| [PaymentConsumerContractTest.java](order-service/src/test/java/dev/pratik/lab/order/PaymentConsumerContractTest.java) | Test: PaymentConsumerContractTest |

## payment-service

| File | Methods / purpose |
|---|---|
| [FaultInjector.java](payment-service/src/main/java/dev/pratik/lab/payment/FaultInjector.java) | configure, before, after, pause, state |
| [OutboxEvent.java](payment-service/src/main/java/dev/pratik/lab/payment/OutboxEvent.java) | getId, getPayload, getTraceparent, getCorrelationId, getSent, getAttempts, getCreatedAt |
| [OutboxRelay.java](payment-service/src/main/java/dev/pratik/lab/payment/OutboxRelay.java) | publishPending, publish |
| [PaymentApplication.java](payment-service/src/main/java/dev/pratik/lab/payment/PaymentApplication.java) | main |
| [PaymentController.java](payment-service/src/main/java/dev/pratik/lab/payment/PaymentController.java) | charge, byOrder, instance |
| [PaymentDtos.java](payment-service/src/main/java/dev/pratik/lab/payment/PaymentDtos.java) | ChargeRequest, PaymentResponse, ChargeResult |
| [PaymentLabController.java](payment-service/src/main/java/dev/pratik/lab/payment/PaymentLabController.java) | Fault, set, state, outbox, replay |
| [PaymentRecord.java](payment-service/src/main/java/dev/pratik/lab/payment/PaymentRecord.java) | getId, getIdempotencyKey, getOrderId, getSku, getQuantity, getAmount, getCurrency, getStatus, getCreatedAt |
| [PaymentStore.java](payment-service/src/main/java/dev/pratik/lab/payment/PaymentStore.java) | charge, byOrder, paymentCount, view |
| [application-eureka.yml](payment-service/src/main/resources/application-eureka.yml) | Configuration, migration or wire-contract fixture |
| [application.yml](payment-service/src/main/resources/application.yml) | Configuration, migration or wire-contract fixture |
| [V1__payments.sql](payment-service/src/main/resources/db/migration/V1__payments.sql) | Configuration, migration or wire-contract fixture |
| [ApplicationContextTest.java](payment-service/src/test/java/dev/pratik/lab/payment/ApplicationContextTest.java) | Test: ApplicationContextTest |
| [PaymentApiContractTest.java](payment-service/src/test/java/dev/pratik/lab/payment/PaymentApiContractTest.java) | Test: PaymentApiContractTest |
| [PaymentStoreIT.java](payment-service/src/test/java/dev/pratik/lab/payment/PaymentStoreIT.java) | Test: PaymentStoreIT |
| [PaymentStoreTest.java](payment-service/src/test/java/dev/pratik/lab/payment/PaymentStoreTest.java) | Test: PaymentStoreTest |

## inventory-service

| File | Methods / purpose |
|---|---|
| [InventoryApplication.java](inventory-service/src/main/java/dev/pratik/lab/inventory/InventoryApplication.java) | main |
| [InventoryController.java](inventory-service/src/main/java/dev/pratik/lab/inventory/InventoryController.java) | product, sales |
| [InventoryStore.java](inventory-service/src/main/java/dev/pratik/lab/inventory/InventoryStore.java) | ProductView, product, recordSale, sales |
| [PaymentEventListener.java](inventory-service/src/main/java/dev/pratik/lab/inventory/PaymentEventListener.java) | receive |
| [Product.java](inventory-service/src/main/java/dev/pratik/lab/inventory/Product.java) | getSku, getName, getPrice, getCurrency |
| [SalesReceipt.java](inventory-service/src/main/java/dev/pratik/lab/inventory/SalesReceipt.java) | getEventId, getOrderId, getPaymentId, getSku, getQuantity, getReceivedAt |
| [application-eureka.yml](inventory-service/src/main/resources/application-eureka.yml) | Configuration, migration or wire-contract fixture |
| [application.yml](inventory-service/src/main/resources/application.yml) | Configuration, migration or wire-contract fixture |
| [V1__inventory.sql](inventory-service/src/main/resources/db/migration/V1__inventory.sql) | Configuration, migration or wire-contract fixture |
| [ApplicationContextTest.java](inventory-service/src/test/java/dev/pratik/lab/inventory/ApplicationContextTest.java) | Test: ApplicationContextTest |
| [InventoryStoreIT.java](inventory-service/src/test/java/dev/pratik/lab/inventory/InventoryStoreIT.java) | Test: InventoryStoreIT |
| [InventoryStoreTest.java](inventory-service/src/test/java/dev/pratik/lab/inventory/InventoryStoreTest.java) | Test: InventoryStoreTest |

## discovery-server

| File | Methods / purpose |
|---|---|
| [DiscoveryApplication.java](discovery-server/src/main/java/dev/pratik/lab/discovery/DiscoveryApplication.java) | main |
| [application.yml](discovery-server/src/main/resources/application.yml) | Configuration, migration or wire-contract fixture |

## Run and inspect

- [README.md](README.md): startup and optional modes.
- [test.md](test.md): expected outcomes and troubleshooting.
- [scripts/scenarios.py](scripts/scenarios.py): end-to-end scenario assertions.
- [scripts/get_token.py](scripts/get_token.py): client-credentials request.
- [scripts/poison_event.py](scripts/poison_event.py): Kafka DLT exercise.
- [requests.http](requests.http): manual HTTP requests.
- [compose.yml](compose.yml), [compose.lb.yml](compose.lb.yml), [compose.eureka.yml](compose.eureka.yml), [compose.metrics.yml](compose.metrics.yml).
- [k8s/lab.yml](k8s/lab.yml): optional local Kubernetes deployment.
- [pom.xml](pom.xml): compatible dependency BOMs and modules.

## Kafka migration additions

| Source | Purpose |
|---|---|
| [PaymentEventCodec.java](lab-common/src/main/java/dev/pratik/lab/common/PaymentEventCodec.java) | Parse and validate event JSON; preserve bad records for DLT recovery |
| [KafkaDeliveryTest.java](lab-common/src/test/java/dev/pratik/lab/common/KafkaDeliveryTest.java) | Real embedded broker: independent groups, retry, DLTs and poison recovery |
| [OutboxRelayTest.java](payment-service/src/test/java/dev/pratik/lab/payment/OutboxRelayTest.java) | Failed publish stays pending; later ACK marks sent |
| [KAFKA.md](KAFKA.md) | Migration mapping, code walkthrough and saga boundary |
