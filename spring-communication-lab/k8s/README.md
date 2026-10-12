# Optional Kubernetes DNS exercise

`lab.yml` deploys the same lab to an existing local cluster with a default StorageClass. It includes single-instance PostgreSQL/Kafka with PVCs, Zipkin, Auth, Order, Inventory, and **three Payment replicas**. It is a learning manifest, not a highly available database or production security setup. Its credentials are public local-demo values.

1. Build images from the project root:

```bash
for svc in auth-service order-service payment-service inventory-service; do
  docker build --build-arg SERVICE="$svc" -t "communication-lab/$svc:1.0.0" .
done
```

2. Load those images into your cluster. For a kind cluster named `kind`:

```bash
for svc in auth-service order-service payment-service inventory-service; do
  kind load docker-image "communication-lab/$svc:1.0.0" --name kind
done
kubectl apply -f k8s/lab.yml
kubectl -n communication-lab get pods -w
```

Minikube users can use `minikube image load` instead. There is no service startup ordering in Kubernetes; database initialization may cause initial application restarts. Watch rollout/readiness until all applications are available.

3. Stop conflicting local Compose services. Run these forwards in separate terminals:

```bash
kubectl -n communication-lab port-forward service/auth-service 9000:9000
kubectl -n communication-lab port-forward service/order-service 8081:8081
kubectl -n communication-lab port-forward service/payment-service 8082:8082
kubectl -n communication-lab port-forward service/inventory-service 8083:8083
kubectl -n communication-lab port-forward service/zipkin 9411:9411
```

Run the first-request instructions in the main README. **Do not run the fault scenario suite against three Kubernetes replicas.** Fault switches are intentionally in-memory per instance, so a request routed to a different pod may not see the injected fault. The deterministic fault suite targets the basic Compose stack.

`http://payment-service:8082` is a Service DNS name. Inside this namespace its full DNS name is `payment-service.communication-lab.svc.cluster.local`. The Service selects the ready Payment pods. Kubernetes generally balances new connections; HTTP keep-alive can send many requests over one connection to the same pod. It does not promise round-robin selection on every HTTP request.

The three Payment pods share the Payment DB, and each runs a relay. PostgreSQL row locking with `SKIP LOCKED` distributes outbox work; consumer deduplication still protects against delivery after an ambiguous publisher outcome. Readiness removes a pod from normal Service routing when its database is unavailable. Kafka health is intentionally not required for accepting a payment into the outbox.

Keep the Auth issuer `http://localhost:9000` for this port-forward exercise; its JWT issuer is a logical identifier. Resource servers fetch public keys from the separate internal JWKS URL. For a real ingress deployment, set an externally stable HTTPS issuer and matching token/JWKS configuration.

Verification status for this optional manifest is recorded in the root `VERIFICATION.md`. Cleaning up the namespace deletes its lab resources and can delete their persistent data, so do that only when you intend to remove the exercise.

Kafka uses single-node KRaft, an internal `kafka:29092` listener, and a PVC. Its three event partitions do not provide three broker replicas. This manifest uses PLAINTEXT and no Kafka authentication/ACLs for a private local exercise. The Docker-specific poison/inspection scripts target Compose; for Kubernetes use the corresponding Kafka CLI via `kubectl exec`.
