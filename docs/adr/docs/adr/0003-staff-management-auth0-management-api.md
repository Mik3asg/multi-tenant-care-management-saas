# ADR 0003: Staff management via Auth0 Management API

- **Status:** Implemented and verified
- **Date:** 2026-07-05

## Context

Care home admins need to create staff accounts, carers and other admins, from within the app. Creating users manually in the Auth0 dashboard and then running a SQL update does not scale, and it requires giving admins dashboard access they should not have.

## Decision

The backend calls the **Auth0 Management API** when an admin creates a staff member.

1. The admin submits name, email, and role in the app UI.
2. The backend calls `POST /api/v2/users` on the Auth0 Management API. Auth0 creates the user with a random, unusable password.
3. The backend then calls Auth0's public `dbconnections/change_password` endpoint. This triggers a real "set your password" email. `verify_email: true` alone only sends an email-verification link, not a usable password-set link, so this second call is required.
4. Auth0 returns the new user's `sub`. The backend creates the `app_user` row with the correct `care_home_id`, taken from the admin's own session, and stores the `sub` in `auth0_sub`. See [ADR 0001](0001-auth0-oidc-bff.md) and [ADR 0002](0002-multi-tenancy-data-isolation.md) for how `care_home_id` and `auth0_sub` are used elsewhere.
5. The staff member clicks the email link, sets their password, and logs in.

The admin never touches the Auth0 dashboard. `care_home_id` always comes from the admin's session, so tenant isolation holds.

**Access control.** `POST /api/staff` and `GET /api/staff` require `ROLE_ADMIN`, enforced with `@PreAuthorize("hasRole('ADMIN')")`.

**Auth0 M2M app required.** A Machine-to-Machine application in Auth0, authorised for the Management API with the `create:users` scope. Credentials are stored as `AUTH0_MGMT_CLIENT_ID` and `AUTH0_MGMT_CLIENT_SECRET`.

**RBAC enforced:**

| Permission                   | Admin | Carer |
| ---------------------------- | :---: | :---: |
| Add / edit / delete resident | Yes   | No    |
| Add / edit / delete care log | Yes   | No    |
| Add care log entry           | Yes   | Yes   |
| View care log                | Yes   | Yes   |
| Add staff member              | Yes   | No    |
| Delete staff member          | No    | No    |

## Key files

- `Auth0ManagementService`: fetches the M2M token, calls the Management API, and triggers the password-set email.
- `StaffService`: creates the `app_user` row and orchestrates the Auth0 call.
- `StaffController`: exposes `GET /api/staff` and `POST /api/staff`.

## Consequences

**Positive**
- Admins manage staff entirely within the app.
- New staff get a genuine "set password" email. No temporary password is ever shared out of band.

**Negative**
- Requires a second Auth0 application (the M2M app), with its own credentials to manage securely.
- The Management API token is fetched on every request. Acceptable for an MVP, but worth caching for a higher-traffic production system.

**Next step:** Auth0 Organizations would model each care home as a native Auth0 organisation, with a built-in invite flow that replaces this Management API call entirely.
