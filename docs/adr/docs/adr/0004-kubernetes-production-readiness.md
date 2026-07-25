# ADR 0004: Backend readiness fixes ahead of Kubernetes deployment

- **Status:** Implemented
- **Date:** 2026-07-10

## Context

The app was moving to a production AWS EKS deployment, built with Terraform and GitOps. See [ADR 0005](0005-aws-eks-production-infrastructure.md) for that build. Preparing for it surfaced three gaps in the backend that had to be fixed first.

1. `application.yml` had a real Auth0 `client-id`, `client-secret`, and `issuer-uri` hardcoded as YAML default fallbacks (`${AUTH0_CLIENT_ID:actual-value}`), already committed to git.
2. Kubernetes had no way to check whether the backend was alive or ready to serve traffic. No health-check endpoint existed.
3. `DataSeeder` unconditionally seeded demo care homes and users, and logged their credentials, on every startup. Nothing stopped this from running against a real production database.

## Decision

**Remove the hardcoded Auth0 defaults.** `application.yml` now declares `${AUTH0_CLIENT_ID}` and the others with no fallback. The app fails fast at startup if any Auth0 variable is unset, rather than silently falling back to a value that could be stale, wrong, or, as happened here, a real leaked credential. The exposed secret was rotated in the Auth0 dashboard.

**Add health probes via Spring Boot Actuator.** `spring-boot-starter-actuator` was added. `management.endpoint.health.probes.enabled`, `management.health.livenessState.enabled`, and `management.health.readinessState.enabled` are all set to `true`. This exposes `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`. The last two map directly to Kubernetes' liveness and readiness probe model. In `SecurityConfig`, only `/actuator/health/**` is `permitAll`, since Kubernetes' kubelet has no session cookie to authenticate with. The rest of `/actuator/**`, such as `/actuator/info`, stays behind authentication. This is a narrow, deliberate carve-out, not a general actuator exposure.

**Gate `DataSeeder` to non-production profiles.** `DataSeeder` is now annotated `@Profile("!prod")`, so it is skipped entirely when `SPRING_PROFILES_ACTIVE=prod`, which the Kubernetes Deployment always sets. Local development, with no profile set, is unaffected. A new `application-prod.yml` holds prod-only overrides. Today that is just reduced log verbosity. It contains no secrets.

## Consequences

**Positive**
- No credential can leak via a forgotten default again. A missing environment variable is now a startup failure, not silent degraded behaviour.
- Kubernetes can tell "process alive" apart from "ready for traffic", which enables correct rolling updates and restarts.
- Production databases never receive demo data or have demo credentials logged on every pod restart.

**Negative**
- Local development now requires all five Auth0 environment variables to be set before the backend will start at all. Previously it would run against a shared dev Auth0 tenant with no setup. Documented in `README.md`.

**Next step:** none of this changes tenant isolation or session handling. ADRs 0001-0003 stand as they are. This ADR is purely about making the existing app deployable and operable in Kubernetes. The actual infrastructure build is [ADR 0005](0005-aws-eks-production-infrastructure.md).
