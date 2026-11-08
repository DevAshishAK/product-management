# PCOM – Production Catalog & Order Management Platform

Event-driven, cloud-native microservices platform: **Java 21 · Spring Boot 3.4 · Spring Cloud 2024.0 · Kafka · PostgreSQL/MongoDB · Docker · Kubernetes · GitHub Actions**.

> Status: source package generated as a reference implementation. It was **not compiled or executed** in the generation environment (no Maven/network there). Expect to fix small compile/config issues on first build, and run `mvn verify` to see real coverage numbers (see *Known gaps*).

## 1. Overview
| Capability | Service(s) |
|---|---|
| Catalog (CRUD, search, categories, pricing) | product-service |
| Orders (place, history, tracking, cancel) | order-service |
| Inventory (reserve / release / adjust / events) | inventory-service |
| Payments (initiate, status, refund) | payment-service |
| Invoices (generate, get, PDF download) | invoice-service |
| Notifications (email / SMS / push) | notification-service |
| Users, roles, permissions, profile | user-service (+ login via token-service) |
| Auth (OAuth2-style, JWT RS256, refresh, revocation) | token-service |
| Edge / platform | api-gateway, discovery-server, config-server, audit-service, monitoring-service |

## 2. Service responsibilities & ports
| Service | Port | Data | Notes |
|---|---|---|---|
| api-gateway | 8080 | Redis | routing, JWT validation, revocation check, Redis rate limiting, circuit breaker, correlation ids; `/internal/**` never routed |
| discovery-server | 8761 | – | Eureka, basic-auth |
| config-server | 8888 | – | Spring Cloud Config (`config-repo/` baked in; use Git backend in prod), basic-auth |
| token-service | 9000 | tokendb | `/oauth2/token` (password, refresh_token, client_credentials), `/oauth2/revoke`, `/oauth2/introspect`, `/.well-known/jwks.json` |
| user-service | 8081 | userdb | registration (public), profile, RBAC roles/permissions, internal auth endpoints |
| product-service | 8082 | productdb | Specification-based search |
| inventory-service | 8083 | inventorydb | pessimistic row locks, reservation saga step + compensation |
| order-service | 8084 | orderdb | Idempotency-Key, saga initiator, status history, Feign → product-service |
| payment-service | 8085 | paymentdb | PSP port + simulated adapter, refunds, compensation |
| invoice-service | 8086 | invoicedb | PDF (OpenPDF), Feign → order-service |
| notification-service | 8087 | notificationdb | channel senders, Feign → user-service |
| audit-service | 8088 | MongoDB | consumes every topic, immutable audit trail |
| monitoring-service | 9090 | – | Spring Boot Admin (discovers apps via Eureka) |

Shared libraries (`common/`): `common-dto` (events, DTOs) · `common-core` (error model) · `common-security` · `common-observability` · `common-persistence` (auditing, outbox, inbox, idempotency) · `common-messaging` (topics, outbox relay, replay endpoint). All are Spring Boot auto-configurations.

## 3. Local setup
```bash
./scripts/bootstrap-env.sh                 # creates .env (random secrets + RSA key pair)
docker compose --profile infra up -d       # postgres, mongo, redis, kafka(KRaft), tempo, prometheus, grafana
docker compose --profile apps up -d --build  # all services (first build takes a while)
```
Gateway `http://localhost:8080` · Eureka `:8761` · Boot Admin `:9090` · Grafana `:3000` · Prometheus `:9091`.

Get a token and call the API:
```bash
curl -s -X POST localhost:8080/oauth2/token -d grant_type=password -d client_id=web-app \
     -d username=admin@pcom.local -d password=<BOOTSTRAP_ADMIN_PASSWORD from .env>
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/products
curl -s -X POST localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN" \
     -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' \
     -d '{"items":[{"productId":"<uuid>","quantity":2}]}'
```
Run a service from the IDE: start the infra profile + config-server + discovery-server, set `CONFIG_PASSWORD`, `OPS_PASSWORD`, `DB_PASSWORD`, `EUREKA_URL`, `-Dspring.profiles.active=local` (human-readable logs).

Build/test: `mvn verify` (Docker required for Testcontainers). Unit tests only: `mvn test`.

## 4. Docker
Every service has a multi-stage `Dockerfile` (Maven build stage → layered Spring Boot jar → `eclipse-temurin:21-jre-alpine`, non-root uid 10001, container-aware JVM flags). Build context is the repo root: `docker build -f services/order-service/Dockerfile .`

## 5. Kubernetes
`k8s/base` (Deployment, Service, HPA, PDB, ConfigMap, Ingress, NetworkPolicies, ServiceMonitor) + `k8s/overlays/{staging,production}` (Kustomize: namespace, replicas, image tags, host, Kafka RF). Namespaces in `k8s/namespaces.yaml` (Pod Security *restricted*).
```bash
kubectl apply -f k8s/namespaces.yaml
kubectl -n pcom-staging create secret generic platform-secrets ...   # see k8s/base/secret.example.yaml (use Sealed/External Secrets)
kubectl apply -k k8s/overlays/staging
```
Rolling updates (`maxUnavailable: 0`), startup/readiness/liveness probes on `/actuator/health/{liveness,readiness}`, graceful shutdown (preStop sleep + `server.shutdown=graceful`), resource requests/limits, read-only root FS, default-deny ingress network policy. `k8s/infra/stateful-infra.yaml` has single-replica backing services for staging only – use managed services in production.

## 6. Kafka
Topics: `order-events`, `inventory-events`, `payment-events`, `invoice-events`, `notification-events` (6 partitions, key = aggregate id ⇒ per-order ordering). Retry topics `<topic>-retry-0..2` and dead-letter `<topic>-dlt` are created by `@RetryableTopic` (4 attempts, exponential backoff; malformed messages go straight to DLT).

| Event | Topic | Producer → consumers |
|---|---|---|
| ORDER_CREATED / CONFIRMED / CANCELLED | order-events | order → inventory, payment(cancel), notification, audit |
| INVENTORY_RESERVED / RESERVATION_FAILED / RELEASED / ADJUSTED | inventory-events | inventory → payment, order, audit |
| PAYMENT_INITIATED / COMPLETED / FAILED / REFUNDED | payment-events | payment → order, invoice, notification, audit |
| INVOICE_GENERATED | invoice-events | invoice → notification, audit |
| NOTIFICATION_TRIGGERED | notification-events | notification → audit |

* **Saga (choreography)**: `ORDER_CREATED → inventory reserves → INVENTORY_RESERVED → payment charges → PAYMENT_COMPLETED → order CONFIRMED (+ invoice, notification)`. Compensations: reservation failure or `PAYMENT_FAILED` → order cancelled → `ORDER_CANCELLED` → stock released / payment refunded. Tombstones handle cancellation overtaking reservation/payment.
* **Reliability**: transactional **outbox** (event stored with the state change, relayed with `FOR UPDATE SKIP LOCKED`, idempotent producer, `acks=all`) + **inbox** (`processed_event` dedup in the same transaction) = effectively-once processing. Kafka transactions are not used; the outbox/inbox pair provides the exactly-once *effect*.
* **Versioning / schema**: envelope `{eventId,eventType,version,occurredAt,correlationId,aggregateId,payload}`; additive changes only, breaking changes bump `version` (consumers ignore unknown types/fields). JSON Schemas in `docs/event-schemas/`.
* **Replay**: (a) new/reset consumer group offsets – `scripts/replay-events.sh reset …`; (b) re-publish retained outbox rows `POST /api/v1/admin/outbox/replay?from&to` (ADMIN). Delete the consumer's `processed_event` rows when replaying into the same group.

## 7. Security design
* **token-service** signs RS256 JWTs (`sub`, `roles`, `permissions`, `type`, `jti`, 15 min). Public key at `/.well-known/jwks.json`; every service is an OAuth2 resource server using it. Refresh tokens are opaque, stored hashed, **rotated** on use; reuse of a rotated token revokes all sessions of that user. Revocation: refresh tokens (DB) and access-token `jti` (denylist) – the gateway enforces it through cached introspection.
* **External clients** obtain a JWT (password grant for users, client_credentials for machine clients) before calling the gateway.
* **Service-to-service**: each service has an OAuth client (`client_credentials`, role `SERVICE`); a Feign interceptor attaches the service JWT and propagates the user JWT in `X-On-Behalf-Of` (stripped at the gateway). `/internal/**` requires `ROLE_SERVICE` and is not routed externally.
* **RBAC**: roles → permissions (`order:write`, `product:write`, …) stored in user-service, emitted as JWT claims, enforced with `@PreAuthorize` (method security) + ownership checks.
* Gateway rate limiting/throttling (Redis token bucket per user/IP), secure headers (HSTS, CSP, no-sniff, frame-deny), CORS allow-list, stateless sessions, CSRF off (bearer tokens).
* **Swagger and Actuator** are protected with HTTP Basic (role OPS, credentials from secrets); only `/actuator/health/**` (probes) is open. Swagger “Authorize” accepts a bearer JWT for *Try it out*.
* Secrets only via environment/Secrets; Dockerfiles run as non-root; Trivy scans in CI.

## 8. Observability
JSON logs (Logback + logstash encoder; fields `traceId`, `spanId`, `correlationId`, `requestId`, `service`) · Micrometer → Prometheus (`/actuator/prometheus`, auto-discovered via Eureka) · OpenTelemetry traces → Tempo (OTLP) · Grafana dashboard `PCOM Platform Overview` (RPS, p95, 5xx ratio, JVM, orders, circuit breakers, Kafka lag, Hikari). Correlation ids flow gateway → HTTP → Feign → Kafka envelope → consumers.

## 9. Resilience
Resilience4j on every remote call (`ProductGateway`, `OrderGateway`, `UserGateway`, payment gateway): RateLimiter, Retry (exponential), CircuitBreaker, Bulkhead, fallbacks; Feign connect/read timeouts; gateway circuit breaker + fallback controller; HPA, graceful shutdown, rolling deployments, PDBs. Config in `config-repo/application.yml`.

## 10. Idempotency
`Idempotency-Key` header on place-order, manual payment, refund and invoice generation. Key + request hash + stored response are saved in `idempotency_record` in the same transaction as the action (replay returns the original response with `Idempotency-Replayed: true`; same key with a different body → 422). Saga steps are idempotent via inbox dedup and unique business keys (`order_id` on payment/invoice/reservation).

## 11. CI/CD (`.github/workflows/ci-cd.yml`)
Checkout → build + unit tests + integration tests + **JaCoCo gate (≥ 80 % lines)** → **SonarQube + quality-gate wait** → **Trivy fs scan** → package + Docker build (matrix, all 13 images) → **Trivy image scan** → push to GHCR → `kubectl apply -k` with commit-pinned tags and rollout verification (develop → staging, main → production approval). Any failure fails the pipeline. Required secrets/vars: `SONAR_TOKEN`, `SONAR_HOST_URL`, `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION`, `KUBE_CONFIG_B64`. Replace `your-org` in `k8s/` with your registry owner.

## 12. API documentation & troubleshooting
Swagger UI: `http://<service>:<port>/swagger-ui.html` (Basic auth `ops` / `OPS_PASSWORD`); OpenAPI JSON at `/v3/api-docs`. All endpoints return the standard `ApiError` (`code`, `message`, `violations`, `traceId`). Standards: `/api/v1` versioning, plural nouns, `Idempotency-Key` for unsafe creates, pagination `?page&size&sort`, UTC ISO-8601 timestamps.

| Symptom | Check |
|---|---|
| Service stuck at start | config-server healthy? `CONFIG_PASSWORD` equal on both sides? retries log `Could not locate PropertySource` |
| 401 everywhere | token-service up & `TOKEN_JWKS_URI` reachable; clock skew; token expired (15 min) |
| 403 | JWT lacks the permission – inspect `permissions` claim; role changes apply after refresh/re-login |
| Orders stay PENDING | `outbox_event` rows unpublished (Kafka down?), consumer lag in Grafana, DLT topics (`*-dlt`) |
| `Schema-validation` error on start | Flyway migration vs entity mismatch (`ddl-auto=validate`) |
| Feign `ClientAbortException`/503 | circuit breaker open (`/actuator/circuitbreakers`) |
| Token service restarts invalidate tokens | `JWT_PRIVATE_KEY/PUBLIC_KEY` not set (ephemeral key) |

## Known gaps / honest notes
* Not compiled here; versions pinned to Boot 3.4.1 / Cloud 2024.0.0 – adjust if your mirror differs.
* Coverage gate is enforced on the 9 business services; tests provided cover services, controllers, repositories, consumers and a Kafka saga IT, but the **80 % threshold is unverified** – add tests where `target/site/jacoco` shows gaps (or lower `jacoco.minimum.coverage` temporarily).
* Payment gateway, SMS and push are simulated adapters (ports are in place). Config repo is baked into the image (native profile).
* One Postgres instance with separate databases/roles in compose; use separate instances in production. Kafka/Postgres/Mongo manifests in `k8s/infra` are single-replica and for staging only.
* Non-blocking retries can reorder events per key; handlers are written to tolerate this (tombstones, guarded state transitions).
