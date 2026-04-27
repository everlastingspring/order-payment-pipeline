# AGENTS.md — Order Payment Pipeline

## Project Snapshot
- Monorepo: `payment-order-platform` (Spring Boot multi-module).
- Java: 21.
- Build tool: Maven.
- Architecture: saga choreography over Kafka with outbox pattern.
- Active phase: Phase 3 testing and CI hardening; unit coverage is green and cross-service integration coverage is expanding.

## Current Working State
- Existing unit tests remain green across all modules via:
  - `mvn test`
- Parent build now includes `maven-failsafe-plugin` to support `*IT` integration tests.
- OWASP Dependency-Check now runs as a warm-cache-only fast path in normal CI.
- Full NVD refresh and cache seeding are owned by the scheduled/manual `Security Refresh` GitHub Actions workflow.
- Integration tests currently present (not part of `mvn test`):
  - `order-service/src/test/java/com/paymentplatform/orderservice/integration/OrderServiceIT.java`
  - `payment-service/src/test/java/com/paymentplatform/paymentservice/integration/PaymentServiceIT.java`
  - `wallet-service/src/test/java/com/paymentplatform/walletservice/integration/WalletServiceIT.java`
  - `inventory-service/src/test/java/com/paymentplatform/inventoryservice/integration/InventoryServiceIT.java`
  - `notification-service/src/test/java/com/paymentplatform/notificationservice/integration/NotificationServiceIT.java`
- ITs are designed to run with Testcontainers (Postgres + Kafka) and real Spring context wiring.
- IT classes currently use `@Testcontainers(disabledWithoutDocker = false)` — do not assume auto-skip when Docker is unavailable.

## Commands
- Unit test path (current stable baseline):
  - `mvn test`
- Compile integration tests without running them:
  - `mvn -pl wallet-service,inventory-service -am test-compile`
- Run integration tests (when Docker daemon is running):
  - `mvn verify -DskipITs=false`
- Run only ITs for current slice:
  - `mvn -pl wallet-service,inventory-service -am verify -DskipTests -DskipITs=false`
- Refresh the NVD cache and publish the full OWASP report:
  - Trigger `.github/workflows/security-refresh.yml` manually in GitHub Actions, or wait for the scheduled run.

## Important Constraints
- Do not revert existing unstaged/staged user work.
- Treat the current dirty tree as in-progress user-authoritative state.
- `mvn test` must stay green while expanding S18 coverage.
- Integration tests require Docker for execution; do not assume they will auto-skip when Docker is unavailable.
- Do not reintroduce a live NVD download into the normal CI hot path.

## Known Environment Notes
- On this machine/session, `docker ps` reported daemon unavailable at:
  - 2026-04-27 (Asia/Kolkata)
- Current CI security behavior with this repo state:
  - Deploy-path CI expects a fresh warmed NVD cache from `Security Refresh`.
  - Branch/PR CI avoids live NVD downloads and will skip the OWASP scan when no warmed cache is available.

## Next Rational Steps
1. Start Docker daemon and run `mvn verify -DskipITs=false`.
2. Fix any runtime/container-specific IT failures.
3. Expand S18 coverage to payment/order integration flows once current IT slice is stable.
