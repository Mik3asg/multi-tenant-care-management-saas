# ADR 0001: Delegate authentication to Auth0 (OIDC) via Backend-for-Frontend

- **Status:** Implemented and verified
- **Date:** 2026-07-04

## Context

Owning credentials is a permanent liability. Passwords, MFA, lockout, and password reset all need building and maintaining ourselves. This is a care-sector product, so compliance risk matters too. B2B customers will also want per-tenant SSO, and building that in-house would be expensive.

The alternative to a self-hosted login system is to delegate identity to a provider. We chose Auth0.

## Decision

Auth0 handles authentication. The backend acts as a confidential OAuth client using the Backend-for-Frontend (BFF) pattern.

- The backend completes the authorization-code flow server-side.
- All tokens stay in the Redis session. The browser never sees them.
- The browser only receives an `HttpOnly` session cookie.
- No stored passwords, no BCrypt, no local login form.

**How identity maps to a care home.** Auth0 returns a `sub` claim, for example `auth0|abc123`. The app looks this up in `app_user.auth0_sub`. That row tells the app who the user is and which care home they belong to. The client never supplies `care_home_id` itself.

`Auth0OidcUserService` does this lookup on every login and returns an `AppUserPrincipal` with the correct `care_home_id`. The rest of the app does not need to know Auth0 exists.

**Staff creation.** Admins create staff from the app UI. The backend calls the Auth0 Management API automatically. See [ADR 0003](0003-staff-management-auth0-management-api.md).

**Longer term.** Auth0 Organizations, one per care home, would turn per-tenant SSO into a configuration step and remove the need for the local `auth0_sub` lookup entirely.

## Login flow, step by step

1. User clicks **Sign in**. The browser navigates to `/oauth2/authorization/auth0`.
2. Spring Security redirects the browser to Auth0's login page.
3. The user enters credentials. Auth0 authenticates them.
4. Auth0 redirects the browser back to `/login/oauth2/code/auth0` with an authorisation code.
5. Spring Security exchanges the code for tokens. This happens server-side. The browser never sees the tokens.
6. `Auth0OidcUserService` extracts the `sub` claim and looks up the local `app_user` row.
7. An `AppUserPrincipal`, carrying `careHomeId`, is stored in the Redis session.
8. The browser receives an opaque `SESSION` cookie and is redirected to the frontend.
9. Every later request carries that cookie. The server reads the tenant from it.

## Logout flow

1. User clicks **Sign out**. The browser calls `POST /api/auth/logout`.
2. Spring Security invalidates the Redis session and clears the cookie.
3. The browser is redirected to Auth0's `/v2/logout?returnTo=<frontend>`.
4. Auth0 clears its own session and redirects back to the frontend login page.

## Dual-session behaviour

Two independent sessions exist side by side.

| Session | Where it lives | Cleared by |
| ------- | --------------- | ---------- |
| App session | Redis, server-side | Sign out in the app |
| Auth0 SSO session | Auth0, plus a browser cookie | The `/v2/logout` redirect |

The logout flow above clears both. If a user opens a new tab while still logged in, Auth0 recognises the active SSO session. It shows a consent screen ("CareCloudly is requesting access...") instead of the login form. This is normal SSO behaviour, not a bug. Clicking **Accept** resumes the session without re-entering credentials. Only a full sign-out removes both sessions.

## Key files

- `SecurityConfig`: configures `oauth2Login` and logout via Auth0's `/v2/logout`.
- `Auth0OidcUserService`: resolves `sub` to `AppUserPrincipal`.
- `AppUserPrincipal`: implements `OidcUser` and `UserDetails`. Its `oidcDelegate` field is `transient`.
- `application.yml`: holds `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`, and `AUTH0_ISSUER_URI`.

## Consequences

**Positive**
- No passwords stored. Smaller breach surface and less compliance scope.
- MFA, lockout, and password reset are the provider's job, not ours.
- Per-tenant SSO becomes a configuration step, not something to build.

**Negative**
- Auth0 sits in the critical login path. Its availability, cost, and lock-in all become our problem too.
- Auth0's logout is non-standard. It uses `/v2/logout`, not the OIDC standard `end_session` endpoint.
