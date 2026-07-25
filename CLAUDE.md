# Care Management App

Multi-tenant, B2B care-management web app. Each **care home is a tenant**;
every request is scoped to the authenticated user's care home.

## Stack
- Backend: Java 21, Spring Boot 3.3.x (Web, Security, Data JPA, Spring Session + Redis)
- Auth: Auth0 (OIDC) via Backend-for-Frontend — tokens stay server-side
- DB: PostgreSQL + Row-Level Security · Session/cache: Redis
- Frontend: React + Vite + TypeScript
- Local infra: docker-compose (Postgres + Redis only)
- Production infra: docker-compose.prod.yml (all services including backend + frontend/nginx)

## Run (local dev)
1. `docker compose up -d`                          # Postgres + Redis
2. `cd backend && mvn spring-boot:run`             # backend on :8090 (requires Auth0 env vars)
3. `cd frontend && npm install && npm run dev`     # dev server :5173
4. Login: `http://localhost:8090/oauth2/authorization/auth0`

Required env vars: `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`, `AUTH0_ISSUER_URI`,
`AUTH0_MGMT_CLIENT_ID`, `AUTH0_MGMT_CLIENT_SECRET`.

## Non-negotiable invariants
- **Tenant is always server-derived.** `care_home_id` comes from the authenticated
  principal — NEVER from a request body, query param, or header. No DTO or endpoint
  accepts a client-supplied `care_home_id`. Cross-tenant access returns **404**
  (don't reveal existence), not 403.
- **Sessions, not JWT.** Auth is cookie-based server-side sessions (Spring Session +
  Redis). Do not introduce JWT — see ADR 0001.
- **Decisions are logged as ADRs** (Nygard format) in `docs/adr/` before/while
  implementing. Treat them as the source of truth for architecture choices.

## Architecture decisions (read before changing auth or tenancy)
@docs/adr/docs/adr/0001-auth0-oidc-bff.md
@docs/adr/docs/adr/0002-multi-tenancy-data-isolation.md
@docs/adr/docs/adr/0003-staff-management-auth0-management-api.md
@docs/adr/docs/adr/0004-kubernetes-production-readiness.md

## Gotchas
- `DataSeeder` is `@Component` not `@Service` — the RLS GUC aspect does not intercept it (intentional). It's also `@Profile("!prod")` — never seeds demo data or logs demo credentials when `SPRING_PROFILES_ACTIVE=prod`.
- `DataSeeder`'s demo accounts are seeded with `auth0_sub = NULL` and are **not** loggable-in via Auth0 out of the box — `Auth0OidcUserService.loadUser()` matches strictly by `auth0_sub` with no email fallback, so a `NULL` never matches a real login and fails with `user_not_linked`. This means the very first admin in *any* environment (local dev included, not just prod) must be bootstrapped manually: attempt one Auth0 login to obtain the real `sub` (Auth0 Dashboard → User Management → Users), then set it on an `app_user` row directly. `password_hash` on seeded/bootstrapped rows is vestigial from before ADR 0001 removed local password login — no code path checks it anymore.
- `oidcDelegate` on `AppUserPrincipal` is `transient` — excluded from Redis serialisation, only needed during login.
- Vite proxies `/api`, `/oauth2`, and `/login/oauth2` to the backend. Changes require a dev-server restart.
- All frontend API calls go through `src/api.ts`. No `fetch` calls in pages.
- CSRF is disabled — a known hardening item. Do not rely on this baseline.
- Auth0 `client-id`/`client-secret`/`issuer-uri` have no defaults in `application.yml` — the app fails fast at startup if any are unset. Never reintroduce a hardcoded fallback here (ADR 0004).
- `/actuator/health/**` is intentionally `permitAll` in `SecurityConfig` for Kubernetes probes. Do not widen this to all of `/actuator/**` — `/actuator/info` and others stay authenticated.

## Conventions
- Prose commit messages, present-tense imperative ("Add CSRF token repository").
- Keep `README.md` run steps in sync with any port/infra change.
- Detailed rules: see `.claude/rules/`.
