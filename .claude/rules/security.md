# Security rules

## Tenancy
- `care_home_id` is derived server-side from the authenticated principal, always.
  No request body, query param, header, or DTO may supply it.
- Cross-tenant access returns 404, never 403 (don't reveal existence).
- Tenant-scoped reads use `...AndCareHomeId(...)` repository methods; never load by
  id and check tenant afterwards in code.

## Sessions & CSRF
- Cookie-based server-side sessions (Redis). No JWT (ADR 0001).
- CSRF protection must be ON for cookie sessions (CookieCsrfTokenRepository +
  `X-XSRF-TOKEN` header from the frontend). It is currently disabled — treat that
  as a bug to fix, not a baseline to preserve.
- Session cookie flags for production: `HttpOnly`, `Secure`, `SameSite=Lax`.
- Add idle + absolute session timeouts; keep session-id rotation on login.

## Credentials & secrets
- Auth moving to Auth0 (OIDC + BFF). Backend is a confidential client; tokens stay
  server-side and never reach the browser.
- Never commit secrets (Auth0 client secret, DB creds). Use env vars.
- Generic auth errors only ("invalid email or password") — never reveal whether an
  account exists.

## Data
- Prefer parameterised queries / JPA methods; no string-concatenated SQL.
- Postgres Row-Level Security is implemented as defence-in-depth beyond app-level
  scoping (ADR 0002): `RlsInitializer` enables RLS on `resident` and
  `care_log_entry`; `TenantGucAspect` sets `app.care_home_id` per transaction.
  Known gap: when the GUC is unset, all rows are visible (covers `DataSeeder` and
  migration tooling) — next hardening step is a dedicated non-owner DB role to
  close that bypass.