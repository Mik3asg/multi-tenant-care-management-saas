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
| Production infra | AWS EKS, provisioned via Terraform; RDS Postgres + ElastiCache Redis; GitOps deployment via ArgoCD — see [ADR 0005](docs/adr/docs/adr/0005-aws-eks-production-infrastructure.md) |

## Architecture

### Application architecture

- **Auth0 OIDC (BFF).** The backend completes the OIDC flow server-side. Tokens stay in Redis. The browser only gets an opaque session cookie.
- **Tenant comes from the session, never the client.** The authenticated principal carries `careHomeId`. Every request reads the tenant server-side. No endpoint accepts a `care_home_id` from the client.
- **Shared schema with Row-Level Security.** Every domain table has a `care_home_id` column. Application code scopes every query first, using methods like `findByIdAndCareHomeId`. Postgres RLS is the second line of defence. A query that forgets the filter returns no rows instead of leaking data.
- **Cross-tenant access returns 404, not 403.** The app never reveals whether a record exists.

See [ADR 0001](docs/adr/docs/adr/0001-auth0-oidc-bff.md), [ADR 0002](docs/adr/docs/adr/0002-multi-tenancy-data-isolation.md), and [ADR 0003](docs/adr/docs/adr/0003-staff-management-auth0-management-api.md) for the full reasoning.

### Infrastructure and deployment architecture

The production environment runs on a real AWS EKS cluster. Terraform provisions all of it. GitOps keeps it in sync, so nobody runs `kubectl apply` by hand. Full reasoning is in [ADR 0005](docs/adr/docs/adr/0005-aws-eks-production-infrastructure.md).

**Compute and networking.** A dedicated VPC. An EKS cluster with a managed node group. ECR repositories for both images. All defined as Terraform modules (`infrastructure/terraform/modules/{vpc,eks,ecr}`).

**Managed data stores.** RDS Postgres and ElastiCache Redis. Neither runs inside Kubernetes. Both are reachable only from the cluster's own security group.

**Cluster add-ons, also via Terraform:**
- `ingress-nginx` handles incoming traffic.
- `cert-manager` issues TLS certificates through Let's Encrypt. Certificates were validated against the staging issuer first, then switched to the production issuer.
- `external-dns` updates Cloudflare DNS records automatically to match the Ingress.
- External Secrets Operator pulls the database password and Auth0 credentials from AWS Secrets Manager at runtime. Nothing secret ever lives in Git.
- `kube-prometheus-stack` provides Prometheus and Grafana.
- ArgoCD manages GitOps deployment.

**GitOps deployment.** ArgoCD watches `kubernetes/overlays/production` in this repository. It reconciles the cluster to match automatically. Nothing else applies application manifests directly.

**CI/CD via GitHub Actions.** GitHub authenticates to AWS through its own OIDC provider. No long-lived AWS keys are stored anywhere. Every pull request touching Terraform triggers a Checkov scan. Every push to `main` that touches the app code builds both images and scans them with Trivy, blocking on any CRITICAL finding. It then pushes the images to ECR and updates the image tags in the overlay ArgoCD watches. This closes the loop without a human touching the cluster.

**Cost-conscious by design.** This is a portfolio project, not a service that needs to run continuously. Data stores are single-AZ. The node group is small. The whole environment can be destroyed and recreated on demand using `infrastructure/scripts/{up.sh,down.sh}`.

### Project layout

```
care-management-app/
├── docker-compose.yml
├── docker-compose.prod.yml
├── docs/adr/                          # Architecture Decision Records
├── .github/workflows/                 # CI/CD — Checkov on Terraform PRs; build, Trivy scan, push, bot-commit on main
├── infrastructure/
│   ├── scripts/                       # up.sh / down.sh — stand up / tear down the whole environment
│   └── terraform/
│       ├── bootstrap/                 # Remote state backend (S3 + DynamoDB) — created once, never destroyed
│       ├── environments/production/   # Root module — wires every module together for this environment
│       └── modules/                   # vpc, eks, rds, elasticache, ecr, irsa, cluster-addons, github-oidc
├── kubernetes/
│   ├── base/                          # Namespace, backend/frontend manifests, Ingress, ArgoCD Application
│   └── overlays/production/           # Kustomize overlay — image tags CI updates on every deploy
├── backend/src/main/java/com/meridian/care/
│   ├── domain/                        # CareHome, AppUser, Resident, CareLogEntry
│   ├── repo/                          # Tenant-scoped Spring Data repositories
│   ├── security/                      # AppUserPrincipal, Auth0OidcUserService, Auth0ManagementService
│   ├── config/                        # SecurityConfig, RlsInitializer, TenantGucAspect
│   ├── service/                       # ResidentService, StaffService
│   └── web/                           # ResidentController, StaffController, AuthController, DTOs
└── frontend/src/
    ├── api.ts                         # Typed API client
    ├── auth.tsx                       # Auth context
    ├── App.tsx                        # Routes
    └── pages/                         # Residents, ResidentDetail, Staff, Login
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

Export Auth0 credentials (from the dashboard steps above) — these have no defaults in `application.yml`, so the backend fails fast at startup if any are unset:

```bash
export AUTH0_CLIENT_ID=...
export AUTH0_CLIENT_SECRET=...
export AUTH0_ISSUER_URI=https://<your-tenant>.us.auth0.com/
export AUTH0_MGMT_CLIENT_ID=...
export AUTH0_MGMT_CLIENT_SECRET=...
```

`mvn spring-boot:run` does not read `.env` — if you keep these in the root `.env` file, export them into the shell first: `export $(grep -v '^#' .env | xargs)`.

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

## Running as a full container stack (local, production-like)

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

## Production deployment (AWS)

The real deployment: AWS EKS, provisioned entirely via Terraform, with the application itself deployed and kept in sync by ArgoCD (GitOps). See [ADR 0005](docs/adr/docs/adr/0005-aws-eks-production-infrastructure.md) for why each piece is built the way it is.

**Prerequisites:** an AWS account with credentials configured locally, Terraform ≥ 1.9, `kubectl`, `kustomize`, a Cloudflare-managed domain, and the two Auth0 applications from the setup steps above.

### 1. Bootstrap remote state

```bash
cd infrastructure/terraform/bootstrap
terraform init && terraform apply
```

Creates the S3 bucket and DynamoDB table that hold Terraform state for everything else.

### 2. Fill in your environment values

Create `infrastructure/terraform/environments/production/terraform.tfvars` (gitignored — never committed) with your domain, contact email, and sizing choices. See the variables declared in that directory for the full list.

### 3. Seed the secrets Terraform never touches

Terraform manages the infrastructure but deliberately never sees these values — seed them once, manually:

```bash
aws secretsmanager create-secret --name cloudflare-api-token \
  --secret-string '{"api-token":"<your-cloudflare-token>"}' --region eu-west-2

aws secretsmanager create-secret --name auth0-credentials \
  --secret-string '{"client-id":"...","client-secret":"...","issuer-uri":"...","mgmt-client-id":"...","mgmt-client-secret":"..."}' \
  --region eu-west-2
```

### 4. Stand up the infrastructure

```bash
cd infrastructure/terraform/environments/production
terraform init && terraform apply
```

Provisions the VPC, EKS cluster, RDS Postgres, ElastiCache Redis, ECR repositories, and every cluster add-on (ingress-nginx, cert-manager, external-dns, External Secrets Operator, kube-prometheus-stack, ArgoCD) in one apply.

### 5. Point `kubectl` at the new cluster

```bash
aws eks update-kubeconfig --name <cluster_name> --region eu-west-2
```

### 6. Build and push the first images

```bash
aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin <account_id>.dkr.ecr.eu-west-2.amazonaws.com

docker build -t <account_id>.dkr.ecr.eu-west-2.amazonaws.com/care-management/backend:v1 ./backend
docker push <account_id>.dkr.ecr.eu-west-2.amazonaws.com/care-management/backend:v1

docker build -t <account_id>.dkr.ecr.eu-west-2.amazonaws.com/care-management/frontend:v1 ./frontend
docker push <account_id>.dkr.ecr.eu-west-2.amazonaws.com/care-management/frontend:v1

cd kubernetes/overlays/production
kustomize edit set image \
  <account_id>.dkr.ecr.eu-west-2.amazonaws.com/care-management/backend=<account_id>.dkr.ecr.eu-west-2.amazonaws.com/care-management/backend:v1 \
  <account_id>.dkr.ecr.eu-west-2.amazonaws.com/care-management/frontend=<account_id>.dkr.ecr.eu-west-2.amazonaws.com/care-management/frontend:v1
```

### 7. First deploy

```bash
kubectl apply -k kubernetes/overlays/production
```

### 8. Register GitOps

```bash
kubectl apply -f kubernetes/base/argocd/application.yaml
```

From here on, ArgoCD watches `kubernetes/overlays/production` and reconciles the cluster automatically — no further manual `kubectl apply` for application changes.

### 9. Bootstrap the first admin

`DataSeeder` never runs against a real database (`SPRING_PROFILES_ACTIVE=prod`), so there is no seeded account to log in with. Sign up via Auth0 on the real domain once — it will fail, since no linked account exists yet — then take the Auth0 `sub` it created and insert a `care_home` plus `app_user` row directly, the same way as [Onboarding a new care home](#onboarding-a-new-care-home) below, but against the real database. Every admin created after that first one goes through the ordinary Staff page (see ADR 0003).

### Continuous deployment

Add `AWS_ROLE_ARN` (from `terraform output github_actions_role_arn`) as a GitHub repository secret. From then on, every push to `main` touching `backend/` or `frontend/` builds both images, scans them with Trivy, pushes to ECR, and bot-commits the new tags into `kubernetes/overlays/production` — ArgoCD picks up the commit and redeploys with no manual step at all.

### Tearing down

The whole point of the destroy/recreate cost model — nothing here should be a surprise, it's the intended workflow:

```bash
cd infrastructure/terraform/environments/production
terraform destroy
```

Terraform destroys everything in the correct dependency order — cluster add-ons (ArgoCD, ingress-nginx, and the rest) first, then the node group, then the EKS cluster, then the VPC. `kubernetes/` is not Terraform-managed, so there's nothing to delete there separately: once the cluster goes, everything running inside it goes with it.

**Never destroy `infrastructure/terraform/bootstrap/`** — that's the remote state backend (S3 + DynamoDB) and is meant to persist across every destroy/recreate cycle.

**After destroy completes, check for two things Terraform doesn't track:**
- **The load balancer.** `ingress-nginx`'s `Service` is `type: LoadBalancer`, so Kubernetes' own AWS cloud-controller provisions the NLB dynamically — Terraform only tracks the Helm release that requested it. `aws elbv2 describe-load-balancers --region eu-west-2` should show nothing a few minutes after destroy; if it does, delete it manually (the single most expensive thing that could be silently left running).
- **Cloudflare DNS.** `external-dns` normally removes its own record when the Ingress disappears, but the whole cluster — including `external-dns` itself — vanishes in one shot, so it may not get a clean chance to. Check the Cloudflare dashboard for a stale `care.virtualscale.dev` record.

### Redeploying afterwards

1. Repeat the deployment steps above, starting from **step 4** (`terraform apply`) — the bootstrap state backend, `terraform.tfvars`, and the manually-seeded secrets (Cloudflare token, Auth0 credentials) all persist across a destroy and don't need recreating.
2. **Step 6 (build and push images) is required, not optional** — ECR itself is destroyed and recreated along with everything else, so it comes back empty every time.
3. Re-register ArgoCD (step 8) — the `Application` resource is a Kubernetes object, not a Terraform one, so it doesn't survive a cluster destroy.
4. Bootstrap the first admin again (step 9) — a new RDS instance means an empty database.

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

`DataSeeder` only runs when no `prod` Spring profile is active (`@Profile("!prod")`) — it never seeds demo data or logs demo credentials when `SPRING_PROFILES_ACTIVE=prod` (always set in Kubernetes). Local dev (no profile set) behaves as before.

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
| GET      | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Public | Kubernetes probes — scoped narrowly, rest of `/actuator/**` stays authenticated |
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
