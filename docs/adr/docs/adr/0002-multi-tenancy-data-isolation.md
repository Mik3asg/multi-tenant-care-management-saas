# ADR 0002: Multi-tenancy data isolation via shared schema and application-layer scoping

- **Status:** Implemented
- **Date:** 2026-07-04

## Context

All care homes share one database. A carer from Jupiter House must never see Mars Lodge's data, even if they guess a valid record ID.

Three isolation models exist:

1. **Separate database per tenant.** Strongest isolation. Most expensive to run.
2. **Separate schema per tenant.** Strong isolation. Complex to operate at scale.
3. **Shared schema with a tenant column.** Simplest to run. Isolation is enforced in code, not by the database structure.

This app is an early-stage MVP. Operational simplicity outweighs the stronger guarantees of options 1 and 2, so we chose option 3.

## Decision

Use a shared schema. Every domain table has a `care_home_id` column. The application enforces isolation on every request.

- `care_home_id` is read from the server-side session (`AppUserPrincipal`). The client never supplies it, not in the body, the URL, or a header.
- Every query filters by `care_home_id`. There is no bare `findById` for tenant-scoped data.
- A cross-tenant lookup returns **404**, not 403. The record's existence is never revealed.
- `TenantIsolationTest` enforces this boundary end-to-end against real Postgres and Redis containers. It must stay green on every change to auth or tenancy.

**Correct pattern:**
```java
Optional<Resident> findByIdAndCareHomeId(UUID id, UUID careHomeId);  // repository
residents.detail(principal.getCareHomeId(), residentId);              // controller
```

**Forbidden pattern:**
```java
residents.findById(id).filter(r -> r.getCareHomeId().equals(careHomeId)); // easy to miss
```

## How care_home_id flows through the system

1. **Stored at account creation.** Every `app_user` row has a non-nullable `care_home_id` foreign key. A user is permanently linked to one care home.
2. **Loaded at login.** `Auth0OidcUserService` resolves the Auth0 `sub` to a local `app_user` row and reads `care_home_id` from that row, never from anything the user supplied. See [ADR 0001](0001-auth0-oidc-bff.md).
3. **Stamped into the session.** That ID is stored in `AppUserPrincipal` and serialised into Redis. The client never receives it.
4. **Used on every request.** The controller reads `principal.getCareHomeId()` and passes it to every query. The client is never consulted again.

## Hardening: Postgres Row-Level Security

RLS is the second line of defence. A query that omits the filter returns no rows instead of leaking data.

- `RlsInitializer` runs on startup. It applies `ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY` to `resident` and `care_log_entry`.
- `TenantGucAspect` (`@Order(1)`) sets the Postgres setting `app.care_home_id` inside every `@Service` transaction, using `set_config(..., true)` so it is `LOCAL` and resets automatically at transaction end.
- `@EnableTransactionManagement(order = 0)` ensures the transaction opens before the aspect runs.

**Policy, the same on both tables:**
```sql
CREATE POLICY tenant_isolation ON resident
  USING (
    nullif(current_setting('app.care_home_id', true), '') IS NULL
    OR care_home_id = nullif(current_setting('app.care_home_id', true), '')::uuid
  );
```

**Known trade-off.** When `app.care_home_id` is unset, all rows are visible. This covers `DataSeeder` (`@Component`, not `@Service`) and migration tooling. `app_user` and `care_home` have no RLS, since `app_user` is queried across tenants at login.

**Next hardening step.** Give the app a dedicated, non-owner `care_app` database role at runtime. This would remove the NULL bypass clause entirely.

## Consequences

**Positive**
- One database instance to run. Simple migrations, backups, and monitoring.
- No per-tenant provisioning to manage.

**Negative**
- A query that omits `care_home_id` leaks data silently.
- Isolation is only as strong as developer discipline and code review.
