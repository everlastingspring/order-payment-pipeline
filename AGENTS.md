# AGENTS.md — Order Payment Pipeline

## Project Snapshot
- Monorepo: `payment-order-platform` (Spring Boot multi-module).
- Java: 21.
- Build tool: Maven.
- Architecture: saga choreography over Kafka with outbox pattern.
- Active phase: Phase 3 testing; unit coverage is green and initial integration-test scaffold is now added.

## Current Working State
- Existing unit tests remain green across all modules via:
  - `mvn test`
- Parent build now includes `maven-failsafe-plugin` to support `*IT` integration tests.
- Integration tests added (not part of `mvn test`):
  - `wallet-service/src/test/java/com/paymentplatform/walletservice/integration/WalletServiceIT.java`
  - `inventory-service/src/test/java/com/paymentplatform/inventoryservice/integration/InventoryServiceIT.java`
- ITs are designed to run with Testcontainers (Postgres + Kafka) and real Spring context wiring.
- IT classes use `@Testcontainers(disabledWithoutDocker = true)` — tests are gracefully skipped when Docker is unavailable.

## Commands
- Unit test path (current stable baseline):
  - `mvn test`
- Compile integration tests without running them:
  - `mvn -pl wallet-service,inventory-service -am test-compile`
- Run integration tests (when Docker daemon is running):
  - `mvn verify -DskipITs=false`
- Run only ITs for current slice:
  - `mvn -pl wallet-service,inventory-service -am verify -DskipTests -DskipITs=false`

## Important Constraints
- Do not revert existing unstaged/staged user work.
- Treat the current dirty tree as in-progress user-authoritative state.
- `mvn test` must stay green while expanding S18 coverage.
- Integration tests require Docker for execution; when Docker is unavailable they are auto-skipped by design.

## Known Environment Notes
- On this machine/session, `docker ps` reported daemon unavailable at:
  - 2026-03-26 (Asia/Kolkata)
- Current behavior with this constraint:
  - `verify -DskipITs=false` remains green but marks ITs as skipped.

## Next Rational Steps
1. Start Docker daemon and run `mvn verify -DskipITs=false`.
2. Fix any runtime/container-specific IT failures.
3. Expand S18 coverage to payment/order integration flows once current IT slice is stable.
