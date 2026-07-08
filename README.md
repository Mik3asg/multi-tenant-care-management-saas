# Care Management App

Multi-tenant B2B care management app. Each care home is an isolated tenant. Carers log in and manage residents and care records for their home only.

## Stack

| Layer         | Technology                                              |
| ------------- | ------------------------------------------------------- |
| Frontend      | React + Vite (TypeScript)                               |
| Backend       | Java 21, Spring Boot 3 (Web, Security, Data JPA)        |
| Auth          | Auth0 (OIDC) via Backend-for-Frontend — no passwords stored |
| Session store | Redis via Spring Session (cookie-based, not JWT)        |
| Database      | PostgreSQL with Row-Level Security                      |
| Local infra   | Docker Compose (Postgres + Redis)                       |

## Architecture

- **Auth0 OIDC (BFF).** The backend completes the OIDC flow server-side. Tokens stay in Redis. The browser receives only an opaque session cookie.
- **Tenant from the session, never the client.** The authenticated principal carries `careHomeId`. Every request reads the tenant server-side. No endpoint accepts a `care_home_id` from the client.
- **Shared schema + Row-Level Security.** Every domain table has a `care_home_id`. Application-layer scoping (`findByIdAndCareHomeId`) is the first line. Postgres RLS is the second — a query that omits the filter returns no rows instead of leaking data.
- **Cross-tenant access returns 404**, not 403. Existence of a record is never revealed.

### Project layout

```
care-management-app/
├── docker-compose.yml
├── docs/adr/                       # Architecture Decision Records
├── backend/src/main/java/com/meridian/care/
│   ├── domain/                     # CareHome, AppUser, Resident, CareLogEntry
│   ├── repo/                       # Tenant-scoped Spring Data repositories
│   ├── security/                   # AppUserPrincipal, Auth0OidcUserService
│   ├── config/                     # SecurityConfig, RlsInitializer, TenantGucAspect
│   ├── service/                    # ResidentService, StaffService
│   └── web/                        # ResidentController, StaffController, AuthController, DTOs
└── frontend/src/
    ├── api.ts                      # Typed API client
    ├── auth.tsx                    # Auth context
    ├── App.tsx                     # Routes
    └── pages/                      # Residents, ResidentDetail, Staff, Login
```

## Data model

```
CareHome(id, name, slug)
AppUser(id, care_home_id, email, password_hash, display_name, role, auth0_sub)
  role ∈ {ADMIN, CARER}
  auth0_sub — links to the Auth0 user identity
Resident(id, care_home_id, full_name, date_of_birth, room)
CareLogEntry(id, care_home_id, resident_id, author_user_id, created_at, category, note)
```

---

## Auth0 setup

Two Auth0 applications are required. Create them once in the [Auth0 dashboard](https://manage.auth0.com).

### 1. Regular Web Application (for login)

- **Type:** Regular Web Application
- **Allowed Callback URLs:** `http://localhost:8090/login/oauth2/code/auth0`
- **Allowed Logout URLs:** `http://localhost:5173`

Gives you: `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`, `AUTH0_ISSUER_URI`

### 2. Machine to Machine Application (for staff creation)

- **Type:** Machine to Machine
- **Authorised API:** Auth0 Management API
- **Required scope:** `create:users`

Gives you: `AUTH0_MGMT_CLIENT_ID`, `AUTH0_MGMT_CLIENT_SECRET`

---

## Running it

### 1. Start Postgres + Redis

```bash
docker compose up -d
```

### 2. Start the backend

Export Auth0 credentials (from the dashboard steps above):

```bash
export AUTH0_CLIENT_ID=...
export AUTH0_CLIENT_SECRET=...
export AUTH0_ISSUER_URI=https://<your-tenant>.us.auth0.com/
export AUTH0_MGMT_CLIENT_ID=...
export AUTH0_MGMT_CLIENT_SECRET=...
```

```bash
cd backend && mvn spring-boot:run
```

Backend runs on **http://localhost:8090**.

### 3. Start the frontend

```bash
cd frontend && npm install && npm run dev
```

Frontend runs on **http://localhost:5173**.

### 4. Log in

Go to **http://localhost:8090/oauth2/authorization/auth0** — this starts the Auth0 login flow and redirects to the app on success.

> Do not open `http://localhost:5173` directly — it has no session yet.

## Running as a full container stack (production-like)

Uses `docker-compose.prod.yml` — builds and runs all services (Postgres, Redis, backend, frontend/nginx).

Create a `.env` file with your values:

```env
DB_PASSWORD=changeme
AUTH0_CLIENT_ID=...
AUTH0_CLIENT_SECRET=...
AUTH0_ISSUER_URI=https://<your-tenant>.us.auth0.com/
AUTH0_MGMT_CLIENT_ID=...
AUTH0_MGMT_CLIENT_SECRET=...
APP_FRONTEND_URL=http://localhost
```

Then:

```bash
docker compose -f docker-compose.prod.yml up --build
```

The app is available at **http://localhost**. The login URL becomes:

```
http://localhost/oauth2/authorization/auth0
```

**Update Auth0 for the containerised URLs:**
- Allowed Callback URL: `http://localhost/login/oauth2/code/auth0`
- Allowed Logout URL: `http://localhost`

## Stopping

```bash
# Stop backend and frontend: Ctrl-C in each terminal

# Stop containers (data preserved):
docker compose down

# Stop containers and wipe all data (re-seeds on next start):
docker compose down -v
```

---

## Inspecting the database

```bash
docker exec -it care-postgres psql -U care -d care
```

```sql
-- List care homes
SELECT id, name, slug FROM care_home;

-- List users with care home and Auth0 link
SELECT u.email, u.display_name, u.role, u.auth0_sub, c.name AS care_home
FROM app_user u JOIN care_home c ON c.id = u.care_home_id;
```

Exit with **Ctrl+D**.

---

## Seeded data

Two care homes seeded for local development. Passwords are managed by Auth0 — there are no local passwords. To log in, an `app_user` row must have `auth0_sub` set to a valid Auth0 User ID.

| Care home     | Role  | Email                                 |
| ------------- | ----- | ------------------------------------- |
| Jupiter House | ADMIN | `care-home-jupiter.admin@example.com` |
| Jupiter House | CARER | `care-home-jupiter.carer@example.com` |
| Mars Lodge    | ADMIN | `care-home-mars.admin@example.com`    |
| Mars Lodge    | CARER | `care-home-mars.carer@example.com`    |

To link an Auth0 user to a seeded account:

```sql
UPDATE app_user SET auth0_sub = 'auth0|YOUR_USER_ID'
WHERE email = 'care-home-jupiter.admin@example.com';
```

---

## Onboarding a new care home

Performed once per care home by the SaaS owner. After this, the admin manages their own staff from the UI — no more SQL needed.

### Naming conventions

| Field  | Description                        | Example             |
| ------ | ---------------------------------- | ------------------- |
| `name` | Full display name of the care home | `Jupiter House`     |
| `slug` | URL-safe identifier: lowercase, hyphens only, no spaces | `jupiter-house` |

### Step 1 — Connect to the database

```bash
docker exec -it care-postgres psql -U care -d care
```

### Step 2 — Create the care home

```sql
INSERT INTO care_home (id, name, slug)
VALUES (gen_random_uuid(), '<Care Home Name>', '<care-home-slug>')
RETURNING id;
```

Copy the returned `id`.

### Step 3 — Create the first admin in Auth0

Auth0 Dashboard → **User Management → Users → + Create User**.
Enter the admin's email. Copy the **User ID** (format: `auth0|abc123`).

### Step 4 — Create the admin user

```sql
INSERT INTO app_user (id, care_home_id, email, password_hash, display_name, role, auth0_sub)
VALUES (
  gen_random_uuid(),
  '<id from step 2>',
  '<admin-email>',
  '',
  '<Admin Display Name>',
  'ADMIN',
  '<auth0|user_id from step 3>'
);
```

### Step 5 — Admin logs in

Send the admin: **http://localhost:8090/oauth2/authorization/auth0**

They click **"Don't remember your password?"**, set a password via the email Auth0 sends, and log in. They can then create all staff from the **Staff** page.

---

## Staff management and permissions

Admins manage staff from the **Staff** page (header link, visible to admins only).

| Permission                 | Admin | Carer |
| -------------------------- | :---: | :---: |
| View residents & care log  | Yes   | Yes   |
| Add care log entry         | Yes   | Yes   |
| Edit care log entry        | Yes   | No    |
| Delete care log entry      | Yes   | No    |
| Add / edit / delete resident | Yes | No    |
| View staff list            | Yes   | No    |
| Add staff member           | Yes   | No    |
| Delete staff member        | No    | No    |

**Adding a staff member:**
1. Fill in name, email, and role — submit the form on the Staff page
2. The backend creates the Auth0 user and saves the local record
3. The staff member receives an Auth0 email → clicks "Don't remember your password?" → sets a password → logs in

An admin can only create staff for their own care home (enforced server-side).

---

## API

| Method   | Path                                     | Access    | Notes                          |
| -------- | ---------------------------------------- | --------- | ------------------------------ |
| GET      | `/oauth2/authorization/auth0`            | Public    | Starts Auth0 login flow        |
| POST     | `/api/auth/logout`                       | Auth      | Invalidates session            |
| GET      | `/api/auth/me`                           | Auth      | Current user + care home       |
| GET      | `/api/residents`                         | Auth      | Scoped to your care home       |
| POST     | `/api/residents`                         | Admin     | Create a resident              |
| GET      | `/api/residents/{id}`                    | Auth      | Detail + care log; 404 cross-tenant |
| PUT      | `/api/residents/{id}`                    | Admin     | Edit resident details          |
| DELETE   | `/api/residents/{id}`                    | Admin     | Delete resident + care log     |
| POST     | `/api/residents/{id}/care-log`           | Auth      | Add care log entry             |
| PUT      | `/api/residents/{id}/care-log/{logId}`   | Admin     | Edit care log entry            |
| DELETE   | `/api/residents/{id}/care-log/{logId}`   | Admin     | Delete care log entry          |
| GET      | `/api/staff`                             | Admin     | Staff in your care home        |
| POST     | `/api/staff`                             | Admin     | Create staff member            |

---

## Tests

```bash
cd backend && mvn test
```

Requires Docker running — tests use Testcontainers (real Postgres + Redis, not mocks).

`TenantIsolationTest` is the core guard: a user from one care home must get **404** on another home's resident, **200** on their own, and a list scoped only to their home.
