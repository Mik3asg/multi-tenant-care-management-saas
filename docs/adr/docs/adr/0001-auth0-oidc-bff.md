# ADR 0001: Delegate authentication to Auth0 (OIDC) via Backend-for-Frontend

- **Status:** Implemented and verified
- **Date:** 2026-07-04

## Context

Owning credentials (passwords, MFA, lockout, reset) is a permanent security liability — particularly for a care-sector product with compliance requirements. B2B customers will require per-tenant SSO, which is expensive to build on a self-owned stack.

## Decision

Delegate identity to **Auth0 (OIDC)**. The backend acts as a confidential OAuth client using the BFF pattern:

- Backend completes the authorization-code flow server-side.
- All tokens stay in the Redis session — the browser never sees them.
- The browser receives only an `HttpOnly` session cookie.
- Self-owned credential layer removed: no stored passwords, no BCrypt, no local login form.

**Tenant mapping:** an `auth0_sub` column on `app_user` links an Auth0 identity to a care home. On login, `Auth0OidcUserService` looks up the user by `sub` and returns an `AppUserPrincipal` with the correct `care_home_id`. The rest of the app is unchanged.

**Identity → care home (plain English):** Auth0 returns a `sub` (e.g. `auth0|abc123`). The app looks it up in `app_user.auth0_sub`. That row determines who the user is and which care home they belong to. The client never supplies `care_home_id`.

**Staff creation:** admins create staff from the app UI — the backend calls the Auth0 Management API automatically. See ADR 0003.

**Longer term:** Auth0 Organizations (one per care home) make per-tenant SSO a configuration step and remove the need for a local `auth0_sub` lookup entirely.

## Login flow (step by step)

1. User clicks **Sign in** → browser navigates to `/oauth2/authorization/auth0`.
2. Spring Security redirects the browser to Auth0's login page.
3. User enters credentials on Auth0. Auth0 authenticates them.
4. Auth0 redirects the browser back to `/login/oauth2/code/auth0` with an authorisation code.
5. Spring Security exchanges the code for tokens (server-side, never exposed to the browser).
6. `Auth0OidcUserService` extracts the `sub` claim and looks up the local `app_user` row.
7. An `AppUserPrincipal` (with `careHomeId`) is stored in the Redis session.
8. Browser receives an opaque `SESSION` cookie and is redirected to the frontend.
9. All subsequent requests carry the cookie — the server derives the tenant from it.

## Logout flow

1. User clicks **Sign out** → `POST /api/auth/logout`.
2. Spring Security invalidates the Redis session and clears the cookie.
3. Browser is redirected to `Auth0 /v2/logout?returnTo=<frontend>`.
4. Auth0 clears its own session and redirects back to the frontend login page.

## Dual-session behaviour

Two independent sessions exist:

| Session | Where | Cleared by |
| ------- | ----- | ---------- |
| App session | Redis (server-side) | Sign out in the app |
| Auth0 SSO session | Auth0 + browser cookie | `/v2/logout` redirect |

Both are cleared by the logout flow above. If a user opens a new tab while still logged in, Auth0 recognises the active SSO session and shows a consent screen ("CareCloudly is requesting access…") instead of the login form — this is normal SSO behaviour. Clicking **Accept** resumes the session without re-entering credentials. A full sign-out removes both sessions.

## Key files

- `SecurityConfig` — `oauth2Login`, logout via Auth0 `/v2/logout`
- `Auth0OidcUserService` — resolves `sub` → `AppUserPrincipal`
- `AppUserPrincipal` — implements `OidcUser` + `UserDetails`; `oidcDelegate` is `transient`
- `application.yml` — `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`, `AUTH0_ISSUER_URI`

## Consequences

**Positive**
- No passwords stored. Reduced breach surface and compliance scope.
- MFA, lockout, and password reset are the provider's responsibility.
- Per-tenant SSO is a configuration step, not a build.

**Negative**
- Auth0 is in the critical login path (availability, vendor cost, some lock-in).
- Auth0 logout is non-standard — `/v2/logout`, not OIDC `end_session`.
