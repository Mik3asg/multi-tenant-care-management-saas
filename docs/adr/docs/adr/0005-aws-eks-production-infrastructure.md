# ADR 0005: AWS EKS production infrastructure via Terraform and GitOps

- **Status:** Implemented
- **Date:** 2026-07-25

## Context

The app previously ran only via local Docker Compose. Taking it to a real, internet-reachable production environment required a genuine cloud build: a Kubernetes cluster, managed data stores, DNS and TLS, secrets handling that never touches version control, and a deployment pipeline that doesn't rely on anyone running `kubectl apply` from a laptop.

This is a portfolio build, not a funded SaaS, so cost discipline shaped several decisions below as much as correctness did.

## Decision

**Region and cost model.** Everything lives in `eu-west-2`. The cluster is designed to be destroyed and recreated on demand rather than left running continuously — `infrastructure/scripts/{up.sh,down.sh}` wrap `terraform apply`/`destroy` for exactly this. All billable resources (EKS control plane, node group, NAT gateway, RDS, ElastiCache, the ingress load balancer) are sized to the smallest burstable/on-demand tier that still behaves like a genuine production topology.

**Terraform layout.** A `bootstrap/` stack (S3 + DynamoDB, versioned and encrypted) holds remote state for everything else. `environments/production/` is the single root module; `modules/` holds each reusable piece (`vpc`, `eks`, `ecr`, `irsa`, `rds`, `elasticache`, `cluster-addons`, `github-oidc`). One environment exists today; a second would reuse the same modules rather than duplicating them.

**Managed data stores, not in-cluster.** RDS Postgres and ElastiCache Redis, both single-AZ/single-node, both reachable only from the EKS cluster's security group. RDS uses `manage_master_user_password` so the password is Secrets-Manager-generated and never appears in Terraform state, `tfvars`, or CLI output. Because that generated secret's name changes on every RDS recreate, Terraform separately maintains a small, stably-named "shadow" secret that always mirrors the current password — the one thing the application's `ExternalSecret` actually reads.

**Cluster add-ons via Terraform, not manually.** `ingress-nginx`, `cert-manager`, `external-dns`, External Secrets Operator, `kube-prometheus-stack`, and ArgoCD are all installed as `helm_release` resources in the same Terraform apply as the cluster itself, keeping them on the same lifecycle. ArgoCD is the one exception to "Terraform manages everything": once installed, it takes over syncing the *application* manifests (`kubernetes/base` + `kubernetes/overlays/production`) from Git — Terraform never touches those.

**Secrets never committed.** External Secrets Operator, authenticated to AWS via IRSA (no static credentials), pulls everything the backend needs — the database password, the Auth0 application credentials, the Cloudflare API token — from Secrets Manager into runtime Kubernetes `Secret` objects. The handful of values that must exist in Secrets Manager before any of this works (the Auth0 credentials, the Cloudflare token) are seeded once, manually, by the operator — deliberately not Terraform-managed, so no secret value ever appears in a `.tf` file or state diff.

**DNS and TLS.** `external-dns` and `cert-manager` both authenticate to Cloudflare using that same ESO-synced token. Certificates were validated against `letsencrypt-staging` first and only flipped to `letsencrypt-prod` once the full DNS01 challenge chain was proven working end-to-end — avoids burning Let's Encrypt's production rate limits while iterating.

**CI/CD via GitHub Actions, GitOps pull-based sync.** GitHub Actions authenticates to AWS through GitHub's own OIDC provider and a narrowly-scoped IAM role (`modules/github-oidc`) — no long-lived AWS keys stored as repository secrets. On every pull request touching `infrastructure/terraform/**`, Checkov scans the Terraform for misconfigurations. On every push to `main` touching `backend/` or `frontend/`, the pipeline builds both images, scans each with Trivy (blocking on CRITICAL findings, reporting HIGH without blocking), pushes to ECR, and bot-commits the new image tags into `kubernetes/overlays/production`. CI never runs `kubectl apply` itself — ArgoCD, watching that same path, picks up the commit and reconciles the cluster on its own schedule. This is the deliberate GitOps split: CI's job ends at "here is a new image and a new commit," ArgoCD's job is everything after that.

## Consequences

**Positive**
- The entire stack — network, cluster, data, add-ons, and CI/CD trust — is reproducible from `terraform apply` plus one `kubectl apply -f` to register the ArgoCD `Application`. Nothing was clicked into existence by hand in a console.
- No AWS credential, database password, or third-party API token ever needs to exist in Git history, a CI log, or a Terraform diff.
- Cost is genuinely proportional to time spent running, not a fixed monthly floor, which suits a portfolio project's usage pattern.

**Negative**
- Single-AZ RDS/ElastiCache and a single EKS node group mean this topology explicitly does not tolerate an availability-zone failure — an accepted trade-off for cost, not something to replicate for a real multi-tenant SaaS handling genuine patient data.
- The destroy/recreate cost model means the running environment is not always available to demo on demand; standing it back up takes on the order of ten minutes (EKS control plane creation is the slow step).
- A handful of Checkov findings (RDS Multi-AZ, deletion protection, enhanced monitoring, EKS control-plane logging, and similar) are deliberate trade-offs for cost reasons rather than oversights — to be recorded as an explicit, justified skip list rather than left as unexplained red checks.

**Next step:** triage the outstanding Checkov findings into "fix" versus "explicitly accept" and encode that decision in `.github/workflows/pr-checks.yml`. `docs/infra-plan.md`, the phase-by-phase build log this ADR was written from, is retired now that the build it tracked is complete.
