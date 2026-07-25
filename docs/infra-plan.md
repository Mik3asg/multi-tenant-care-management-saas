# Multi-Tenant Care Management SaaS — Infra Buildout Plan

> Living document tracking the AWS EKS + Terraform + GitOps buildout. Phase status is kept current here as work lands. See `docs/adr/` for the formal architecture decision records this plan produces (e.g. ADR 0004).

## Context

`multi-tenant-care-management-saas` (Java 21/Spring Boot backend, React/Vite frontend, Auth0 OIDC BFF, Postgres+RLS, Redis sessions) currently runs only via local docker-compose. This plan builds a production-grade AWS EKS + Terraform + GitOps stack from scratch, as a DevOps portfolio piece, mirroring the structure of the user's existing `eks-statuspage` project but adapted for this app's stack (managed RDS/ElastiCache instead of in-cluster Postgres, External Secrets Operator instead of static k8s Secrets, monitoring included from the start).

Decisions locked in:
- **Region:** eu-west-2 (London)
- **Domain/DNS:** owner already has a domain on Cloudflare — zone/token supplied when Phase 4/7 is reached
- **Cost model:** cluster is destroyed/recreated on demand (not always-on) — Terraform must be cleanly apply/destroy-repeatable; keep an `infrastructure/scripts/` helper for spin-up/teardown
- **Monitoring:** kube-prometheus-stack + Grafana included from Phase 4, not deferred
- **DB/cache hosting:** managed AWS (RDS Postgres + ElastiCache Redis), not in-cluster
- **Secrets:** External Secrets Operator + AWS Secrets Manager (IRSA-authenticated), nothing secret ever committed to git
- **CI/CD:** GitHub Actions — Checkov on Terraform PRs, Trivy on image builds, push to ECR, GitOps pull-based sync via ArgoCD (CI does not `kubectl apply` directly)
- **DNS/TLS:** ExternalDNS + Cloudflare, cert-manager + Let's Encrypt (DNS01 challenge), NGINX ingress
- **Repo layout:** single repo (app + infra together) — `github.com/Mik3asg/multi-tenant-care-management-saas`

Lower-impact defaults chosen (revisit only if this changes):
- Cluster add-ons (ingress-nginx, cert-manager, external-dns, ESO, kube-prometheus-stack) installed via Terraform `helm_release`, not manually — keeps them in the same apply/destroy lifecycle as the cluster itself. ArgoCD manages only *app* manifests, not itself or other add-ons.
- GitOps image updates via a CI bot-commit (`kustomize edit set image`) into a `kubernetes/overlays/production` overlay, rather than installing ArgoCD Image Updater.
- ArgoCD UI: `kubectl port-forward` only for now (no public ingress).
- Kustomize: `base` + one `overlays/production` only, no staging overlay (no staging cluster exists).
- GitHub Actions authenticates to AWS via GitHub OIDC → dedicated IAM role (no long-lived AWS keys as repo secrets).
- Auth0/third-party secrets are seeded into Secrets Manager via a one-off `aws secretsmanager create-secret` CLI command (not Terraform-managed).

---

## Phase 0 — App remediation ✅ Done

No AWS involved — fixes to the existing backend needed before/alongside infra work. Documented in **ADR 0004** (`docs/adr/docs/adr/0004-kubernetes-production-readiness.md`).

- Removed hardcoded Auth0 `client-id`/`client-secret`/`issuer-uri` defaults from `application.yml`; secret rotated in the Auth0 dashboard.
- Added `spring-boot-starter-actuator` + health-probe config; `/actuator/health/**` made `permitAll` in `SecurityConfig` (narrowly — not all of `/actuator/**`).
- `DataSeeder` gated `@Profile("!prod")` — no demo data/credentials in production.
- Added `application-prod.yml` (no secrets, just prod-only overrides).

Accepted, not fixed: `hibernate.ddl-auto: update` running against RDS — documented MVP tradeoff, out of scope for this pass.

---

## Phase 1 — Terraform bootstrap (remote state backend) ✅ Done

S3 bucket `carecloudly-terraform-state-<account-id>` and DynamoDB table `carecloudly-terraform-lock` created and confirmed in AWS (`eu-west-2`).

**Creates:** `infrastructure/terraform/bootstrap/{main.tf,variables.tf,outputs.tf,provider.tf,version.tf}` — a versioned, SSE-encrypted, public-access-blocked S3 bucket for state, plus a DynamoDB table (on-demand billing) for state locking. This stack keeps its own local state (nothing else exists yet to be a backend).

**Requires:** AWS credentials configured locally (`aws configure`/SSO), region `eu-west-2`.

**Verify:** `terraform apply` in `bootstrap/` succeeds; `aws s3 ls` / `aws dynamodb describe-table` confirm the resources; Phase 2's `environments/production/backend.tf` points at this bucket/table and `terraform init` there succeeds cleanly.

---

## Phase 2 — VPC + EKS + ECR + IRSA ⏳ In progress

**Creates:** `infrastructure/terraform/modules/{vpc,eks,ecr,irsa}` + `infrastructure/terraform/environments/production/{main.tf,variables.tf,outputs.tf,backend.tf,provider.tf,version.tf,terraform.tfvars}`.

- `modules/vpc`: 2 AZs, public+private subnets, 1 IGW, **single NAT gateway** (cost choice, appropriate for a destroy/recreate portfolio cluster), EKS subnet-discovery tags.
- `modules/eks`: control plane + one small managed node group (on-demand, e.g. `t3.medium`/`t3.large`), core addons (vpc-cni, coredns, kube-proxy). No EBS CSI driver needed (no in-cluster PVCs for Postgres/Redis).
- `modules/ecr`: `care-management/backend` and `care-management/frontend` repos, lifecycle policy to expire untagged images.
- `modules/irsa`: reusable OIDC-provider + IAM-role-for-service-account module — consumed by ESO (Phase 4) and the GitHub Actions OIDC role (Phase 6).

**Costs money whenever it's up:** EKS control plane (~$73/mo pro-rated hourly), node EC2+EBS, NAT gateway. Add `infrastructure/scripts/{up.sh,down.sh}` wrapping `terraform apply`/`destroy` for the environments/production stack.

**Verify:** `aws eks update-kubeconfig --name <cluster>`; `kubectl get nodes` shows `Ready`; `kubectl get pods -A` shows core addons `Running`; push a throwaway image to one ECR repo via IAM auth.

---

## Phase 3 — RDS Postgres + ElastiCache Redis

**Creates:** `infrastructure/terraform/modules/{rds,elasticache}`, wired into `environments/production/main.tf`.

- `modules/rds`: single small burstable-class Postgres instance, single-AZ, private subnets only, security group scoped to the EKS cluster only, encrypted storage, automated backups. Use RDS's native `manage_master_user_password` (Secrets Manager-backed) so the password never lands in Terraform state/tfvars.
- `modules/elasticache`: single Redis node (no replica), subnet group, security group scoped to EKS only.
- One-off (not Terraform-managed): seed Auth0 creds (`AUTH0_CLIENT_ID/SECRET`, `AUTH0_MGMT_CLIENT_ID/SECRET`) into AWS Secrets Manager via `aws secretsmanager create-secret`, once, manually.

**Costs money whenever it's up:** small RDS + ElastiCache instances, roughly $15-30/mo combined.

**Verify:** from inside the VPC (`kubectl run psql-test --rm -it --image=postgres:16-alpine -- psql ...`) confirm RDS connectivity; a throwaway pod `redis-cli -h <elasticache-endpoint> ping` confirms Redis connectivity and that security groups correctly scope both to EKS only.

---

## Phase 4 — Cluster add-ons (Helm via Terraform `helm_release`)

**Creates:** `kubernetes/helm/{nginx-ingress,cert-manager,external-dns,external-secrets,kube-prometheus-stack}/values.yaml`, applied via a new `infrastructure/terraform/modules/cluster-addons` (or inline in `environments/production`).

- **ingress-nginx**: `service.type=LoadBalancer` → AWS NLB (no AWS Load Balancer Controller needed at this scale).
- **cert-manager** + `kubernetes/base/cert-manager/cluster-issuer.yaml`: `letsencrypt-staging` and `letsencrypt-prod` `ClusterIssuer`s using Cloudflare DNS01.
- **external-dns**: Cloudflare provider, same API-token dependency as cert-manager.
- **external-secrets (ESO)**: IRSA role scoped to `secretsmanager:GetSecretValue` on only this app's secret ARNs; a `ClusterSecretStore` resource.
- **kube-prometheus-stack**: Prometheus + Grafana + default dashboards/alerts, `values.yaml` sized down for a small node group.

**Requires manually:** Cloudflare API token scoped to `Zone:DNS:Edit` + `Zone:Zone:Read`; a GitHub PAT/deploy key for ArgoCD's repo access — both land in Secrets Manager, then ESO syncs them in-cluster. Order matters: ESO must be live before cert-manager/external-dns can actually solve challenges/create records.

**Costs money whenever it's up:** ingress NLB (~$16-20/mo).

**Verify:** all add-on pods `Running`; `kubectl get svc -n ingress-nginx` shows an `EXTERNAL-IP`; a throwaway `ExternalSecret` materializes a real `Secret` with correct keys; `clusterissuer letsencrypt-staging` shows `Ready`; a throwaway `Certificate` against a placeholder host goes `Ready`; `kubectl port-forward` into Grafana shows dashboards with live node/pod metrics.

---

## Phase 5 — App Kubernetes manifests

**Creates:** `kubernetes/base/{namespace.yaml, backend/{deployment.yaml,service.yaml,serviceaccount.yaml,externalsecret.yaml}, frontend/{deployment.yaml,service.yaml}, ingress.yaml, kustomization.yaml}`, `kubernetes/base/argocd/application.yaml`, `kubernetes/overlays/production/kustomization.yaml`.

- No `secret.yaml` — replaced entirely by `backend/externalsecret.yaml`, which pulls `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*`, `AUTH0_*` from Secrets Manager into a runtime `Secret`.
- `backend/deployment.yaml`: ECR image; `SPRING_PROFILES_ACTIVE=prod`; env vars from the ESO Secret; readiness/liveness probes on `/actuator/health/readiness` and `/liveness` (from Phase 0); small resource requests/limits.
- **`backend/service.yaml` must be named `backend`** in the namespace — `frontend/nginx.conf` hardcodes `proxy_pass http://backend:8090`.
- `frontend/deployment.yaml`+`service.yaml`: static nginx, no secrets.
- `ingress.yaml`: nginx ingress class, `cert-manager.io/cluster-issuer` annotation, `/` → frontend service.

**Verify:** first `kubectl apply -k kubernetes/base` manually (bypassing ArgoCD) — pods `Running`, `ExternalSecret` `SYNCED`, ingress gets an address, full Auth0 login round-trip against real RDS+Redis succeeds, a resident/care-log create/read confirms RLS/tenant scoping holds against the managed DB. Only then register the ArgoCD `Application` pointing at the same manifests and confirm `Synced`/`Healthy`.

---

## Phase 6 — GitHub Actions CI/CD (Checkov + Trivy)

**Creates:** `.github/workflows/{pr-checks.yml,build-and-push.yml}`, `infrastructure/terraform/modules/github-oidc`.

- `pr-checks.yml`: Checkov against `infrastructure/terraform` on every PR.
- `build-and-push.yml`: on push to `main` — build both images, run Trivy on each (block merge on CRITICAL, report HIGH), push to ECR tagged with git SHA, bot-commit the new tag into `kubernetes/overlays/production` via `kustomize edit set image` for ArgoCD to pick up.
- AWS auth via GitHub OIDC → IAM role from `modules/github-oidc` (no static AWS keys as repo secrets).

**Verify:** a throwaway PR touching a `.tf` file triggers Checkov; merging to `main` builds+scans+pushes both images, updates the overlay via bot commit, and ArgoCD auto-syncs (confirm via `argocd app get` or pod age reset).

---

## Phase 7 — DNS/TLS cutover to the real domain

1. Point `ingress.yaml`'s host + ExternalDNS annotation at the real subdomain (e.g. `care.yourdomain.com`).
2. Validate against `letsencrypt-staging` first; flip to `letsencrypt-prod` only once clean (avoids Let's Encrypt rate limits).
3. **Manual action in Auth0 dashboard:** update Allowed Callback URLs / Logout URLs / Web Origins to the real domain.
4. Cloudflare DNS-only ("grey cloud") recommended over proxied ("orange cloud").

**Verify:** `dig` resolves to the NLB; browser shows a valid non-staging cert; full Auth0 login works on the real domain; create a resident/care-log entry as an end-to-end smoke test (RDS + Redis + RLS + ingress + TLS all exercised in one flow).

---

## Verification summary (end-to-end, once all phases land)

1. `infrastructure/scripts/up.sh` stands the whole environment up from nothing.
2. Browser login via Auth0 on the real domain, over valid TLS.
3. Create/view a resident and care-log entry as one tenant; confirm a second tenant gets 404 on the first tenant's resident ID (mirrors `TenantIsolationTest`, now proven against RDS in the cloud).
4. Grafana shows live cluster/app metrics.
5. A PR touching Terraform triggers Checkov; a merge to `main` triggers Trivy + build + ArgoCD sync, observable end-to-end.
6. `infrastructure/scripts/down.sh` tears everything down cleanly, leaving only the Phase 1 state bucket/lock table and ECR images behind.

## Critical files referenced
- `backend/pom.xml`, `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/meridian/care/config/DataSeeder.java`, `RlsInitializer.java`
- `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`
- `docker-compose.prod.yml` (env-var/wiring contract to replicate in k8s)
