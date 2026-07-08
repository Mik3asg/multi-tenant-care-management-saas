# Testing rules

## Integration tests
- Use **Testcontainers** with real Postgres + Redis. Do NOT substitute H2 or mock
  the datastore — tenant isolation and session behaviour must be tested against the
  real engines.
- Wire container ports via `@DynamicPropertySource`. Docker must be running.

## The isolation guard
- `TenantIsolationTest` is the load-bearing test: a user in one care home must get
  **404** when requesting another home's resident, **200** for their own, and a
  list scoped to only their home's residents.
- Any change to tenancy, auth, or repository scoping must keep this test green. If
  behaviour legitimately changes, update the test deliberately and say why.

## What to cover for new tenant-scoped features
- Positive: the owning tenant can access/modify its own data.
- Negative: another tenant gets 404 (not 403, not 200-with-empty).
- Never write a test that passes a `care_home_id` in from the client to "set up" a
  scenario — derive it from the authenticated principal like production does.

## Running
- Backend: `mvn test` (needs Docker for Testcontainers).
- Frontend: `npm run typecheck` and `npm run build` must pass before a change is done.