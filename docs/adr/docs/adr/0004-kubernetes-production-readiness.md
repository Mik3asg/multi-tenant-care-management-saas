# ADR 0004: Backend readiness fixes ahead of Kubernetes deployment

- **Status:** Implemented
- **Date:** 2026-07-10

## Context

The app is moving to a production AWS EKS deployment (Terraform + GitOps buildout, see infra plan). Preparing for that surfaced three gaps in the backend that needed fixing before any Kubernetes work could proceed:

1. `application.yml` had a real Auth0 `client-id`/`client-secret`/`issuer-uri` hardcoded as YAML default fallbacks (`${AUTH0_CLIENT_ID:actual-value}`), already committed to git.
2. There was no way for Kubernetes to check whether the backend was alive or ready to serve traffic — no health-check endpoint existed.
3. `DataSeeder` unconditionally seeded demo care homes/users and logged their credentials on every startup, with no guard against running against a real (e.g. RDS) production database.

## Decision

**Remove the hardcoded Auth0 defaults.** `application.yml` now declares `${AUTH0_CLIENT_ID}` etc. with no fallback — the app fails fast at startup if any Auth0 var is unset, rather than silently falling back to a value that could be stale, wrong, or (as found) a real leaked credential. The exposed secret was rotated in the Auth0 dashboard.

**Add health probes via Spring Boot Actuator.** `spring-boot-starter-actuator` was added, with `management.endpoint.health.probes.enabled`, `management.health.livenessState.enabled`, and `management.health.readinessState.enabled` all set to `true`. This exposes `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` — the last two map directly to Kubernetes' liveness/readiness probe model. In `SecurityConfig`, `/actuator/health/**` (only) is `permitAll` — Kubernetes' kubelet has no session cookie to authenticate with, but the rest of `/actuator/**` (e.g. `/actuator/info`) stays behind authentication; this is a narrow, deliberate carve-out, not a general actuator exposure.

**Gate `DataSeeder` to non-production profiles.** `DataSeeder` is now annotated `@Profile("!prod")`, so it's skipped entirely when `SPRING_PROFILES_ACTIVE=prod` — which the Kubernetes Deployment will always set. Local dev (no profile set) is unaffected. A new `application-prod.yml` holds prod-only overrides (currently just reduced log verbosity); it contains no secrets.

## Consequences

**Positive**
- No credential can leak via a forgotten default again — missing env vars are a startup failure, not silent degraded behaviour.
- Kubernetes can distinguish "process alive" from "ready for traffic," enabling correct rolling-update and restart behaviour.
- Production databases (RDS) will never receive demo data or have demo credentials logged on every pod restart.

**Negative**
- Local development now requires all five Auth0 env vars to be set before the backend will start at all (previously it would run against the shared dev Auth0 tenant with no setup). Documented in `README.md`.

**Next step:** none of this changes tenant isolation or session handling (ADRs 0001-0003 stand as-is) — this ADR is purely about making the existing app deployable and operable in Kubernetes.
