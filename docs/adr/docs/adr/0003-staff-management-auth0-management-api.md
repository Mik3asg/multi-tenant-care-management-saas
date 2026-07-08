# ADR 0003: Staff management via Auth0 Management API

- **Status:** Implemented and verified
- **Date:** 2026-07-05

## Context

Care home admins need to create staff accounts (carers and other admins) from within the app. Creating users manually in the Auth0 dashboard and then running a SQL update is not scalable and requires dashboard access.

## Decision

The backend calls the **Auth0 Management API** when an admin creates a staff member:

1. Admin submits name, email, and role in the app UI.
2. Backend calls `POST /api/v2/users` on the Auth0 Management API. Auth0 creates the user and sends them a "set your password" email.
3. Auth0 returns the new user's `sub`. Backend creates the `app_user` row with the correct `care_home_id` (from the admin's session) and stores the `sub` in `auth0_sub`.
4. The staff member clicks the email link, sets their password, and logs in.

The admin never touches the Auth0 dashboard. `care_home_id` is always derived from the admin's session — tenant isolation is preserved.

**Access control:** `POST /api/staff` and `GET /api/staff` require `ROLE_ADMIN`. Enforced via `@PreAuthorize("hasRole('ADMIN')")`.

**Auth0 M2M app required:** a Machine-to-Machine application in Auth0 authorised for the Management API with scope `create:users`. Credentials stored as `AUTH0_MGMT_CLIENT_ID` and `AUTH0_MGMT_CLIENT_SECRET`.

**RBAC enforced:**

| Permission                   | Admin | Carer |
| ---------------------------- | :---: | :---: |
| Add / edit / delete resident | Yes   | No    |
| Add / edit / delete care log | Yes   | No    |
| Add care log entry           | Yes   | Yes   |
| View care log                | Yes   | Yes   |
| Add staff member             | Yes   | No    |
| Delete staff member          | No    | No    |

## Key files

- `Auth0ManagementService` — fetches M2M token, calls Management API
- `StaffService` — creates `app_user` row, orchestrates the Auth0 call
- `StaffController` — `GET /api/staff`, `POST /api/staff`

## Consequences

**Positive**
- Admins manage staff entirely within the app.
- New staff receive a secure "set password" email — no temporary passwords shared out-of-band.

**Negative**
- Requires a second Auth0 application (M2M) with credentials managed securely.
- Management API token is fetched on each request (acceptable for MVP; add caching for production).

**Next step:** Auth0 Organizations — model care homes as organisations natively. Built-in invite flow replaces the Management API call entirely.
