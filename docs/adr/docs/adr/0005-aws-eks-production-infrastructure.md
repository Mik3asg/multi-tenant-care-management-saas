# ADR 0005: AWS EKS production infrastructure via Terraform and GitOps

- **Status:** Implemented
- **Date:** 2026-07-25

## Context

The app previously ran only via local Docker Compose, described in [ADR 0004](0004-kubernetes-production-readiness.md). Taking it to a real, internet-reachable production environment meant building several things from scratch: a Kubernetes cluster, managed data stores, DNS and TLS, secrets handling that never touches version control, and a deployment pipeline that does not rely on anyone running `kubectl apply` from a laptop.

This is a portfolio build, not a funded SaaS. Cost discipline shaped several decisions below as much as correctness did.

## Decision

### Region and cost model

Everything lives in `eu-west-2`. The cluster is designed to be destroyed and recreated on demand, not left running continuously. `infrastructure/scripts/up.sh` and `down.sh` wrap `terraform apply` and `terraform destroy` for exactly this. Every billable resource, the EKS control plane, the node group, the NAT gateway, RDS, ElastiCache, and the ingress load balancer, is sized to the smallest tier that still behaves like a genuine production topology.

### Terraform layout

A `bootstrap/` stack holds remote state for everything else: an S3 bucket and a DynamoDB table, both versioned and encrypted. `environments/production/` is the single root module. `modules/` holds each reusable piece. One environment exists today. A second environment would reuse these same modules rather than duplicate them.

| Module | Key resources | Purpose |
| ------ | ------------- | ------- |
| `bootstrap` | S3 bucket, DynamoDB table | Remote Terraform state and locking |
| `vpc` | VPC, public and private subnets, NAT gateway, route tables | Network foundation for the cluster |
| `eks` | EKS cluster, managed node group, IAM roles, OIDC provider | The Kubernetes control plane and compute |
| `ecr` | Two container registries | Store the backend and frontend images |
| `irsa` | One IAM role per Kubernetes service account | Lets pods call AWS APIs with no static credentials |
| `rds` | Postgres instance, security group, a shadow secret | Managed database |
| `elasticache` | Redis cluster, security group | Managed session store |
| `cluster-addons` | Helm releases: `ingress-nginx`, `cert-manager`, `external-dns`, External Secrets Operator, `kube-prometheus-stack`, ArgoCD | Cluster-level services every workload depends on |
| `github-oidc` | GitHub OIDC provider, a narrowly-scoped IAM role | Lets CI push images with no stored AWS keys |

### Managed data stores, not in-cluster

RDS Postgres and ElastiCache Redis are both single-AZ and single-node. Both are reachable only from the EKS cluster's own security group.

RDS uses `manage_master_user_password`, so AWS Secrets Manager generates the password. It never appears in Terraform state, `tfvars`, or CLI output. That generated secret's name changes every time RDS is recreated, which a static Kubernetes manifest cannot reference reliably. To solve this, Terraform separately maintains a small, stably-named "shadow" secret that always mirrors the current password. This shadow secret is the one thing the application's `ExternalSecret` actually reads.

### Cluster add-ons, also via Terraform

`ingress-nginx`, `cert-manager`, `external-dns`, External Secrets Operator, `kube-prometheus-stack`, and ArgoCD are all installed as `helm_release` resources, in the same Terraform apply as the cluster itself. This keeps them on the same lifecycle as everything else.

ArgoCD is the one exception to "Terraform manages everything." Once installed, it takes over syncing the *application* manifests, `kubernetes/base` and `kubernetes/overlays/production`, from Git. Terraform never touches those.

### Secrets never committed

External Secrets Operator authenticates to AWS using IRSA, with no static credentials. It pulls everything the backend needs, the database password, the Auth0 application credentials, and the Cloudflare API token, from Secrets Manager into runtime Kubernetes `Secret` objects.

A handful of values must exist in Secrets Manager before any of this works: the Auth0 credentials and the Cloudflare token. These are seeded once, manually, by whoever operates the environment. They are deliberately not Terraform-managed, so no secret value ever appears in a `.tf` file or a state diff.

### DNS and TLS

`external-dns` and `cert-manager` both authenticate to Cloudflare using that same ESO-synced token. Certificates were validated against `letsencrypt-staging` first. Only once the full DNS01 challenge chain was proven working end to end did we switch to `letsencrypt-prod`. This avoids burning Let's Encrypt's production rate limits while iterating.

### CI/CD via GitHub Actions, GitOps pull-based sync

GitHub Actions authenticates to AWS through GitHub's own OIDC provider and a narrowly-scoped IAM role (the `github-oidc` module). No long-lived AWS keys are stored as repository secrets.

On every pull request touching `infrastructure/terraform/**`, Checkov scans the Terraform for misconfigurations. On every push to `main` touching `backend/` or `frontend/`, the pipeline builds both images, scans each with Trivy, blocking on any CRITICAL finding and reporting HIGH findings without blocking, pushes to ECR, and bot-commits the new image tags into `kubernetes/overlays/production`.

CI never runs `kubectl apply` itself. ArgoCD watches that same path and reconciles the cluster on its own schedule. This is the deliberate split: CI's job ends at "here is a new image and a new commit." Everything after that is ArgoCD's job.

## Consequences

**Positive**
- The entire stack, network, cluster, data, add-ons, and CI/CD trust, is reproducible from `terraform apply` plus one `kubectl apply -f` to register the ArgoCD `Application`. Nothing was clicked into existence by hand in a console.
- No AWS credential, database password, or third-party API token ever needs to exist in Git history, a CI log, or a Terraform diff.
- Cost is proportional to time spent running, not a fixed monthly floor, which suits a portfolio project.

**Negative**
- Single-AZ RDS and ElastiCache, plus a single EKS node group, mean this topology does not tolerate an availability-zone failure. This is an accepted trade-off for cost. It is not something to replicate for a real multi-tenant SaaS handling genuine patient data.
- The destroy/recreate cost model means the environment is not always available to demo on demand. Standing it back up takes around ten minutes, since EKS control plane creation is the slow step.
- A handful of Checkov findings, RDS Multi-AZ, deletion protection, enhanced monitoring, EKS control-plane logging, and similar, are deliberate trade-offs for cost reasons, not oversights.

**Next step:** triage the outstanding Checkov findings into "fix" versus "explicitly accept," and encode that decision as a documented skip list in `.github/workflows/pr-checks.yml`.
