# environments/production — root module
#
# Wires all modules together in dependency order:
#   vpc → eks → irsa (x2)
#              → ecr
#         → rds
#         → elasticache
#
# -----------------------------------------------------------------------------
# Networking
# -----------------------------------------------------------------------------

module "vpc" {
  source = "../../modules/vpc"

  project_name         = var.project_name
  cluster_name         = var.cluster_name
  vpc_cidr             = var.vpc_cidr
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
}

# -----------------------------------------------------------------------------
# EKS cluster + OIDC provider
# -----------------------------------------------------------------------------

module "eks" {
  source = "../../modules/eks"

  project_name       = var.project_name
  cluster_name       = var.cluster_name
  cluster_version    = var.cluster_version
  private_subnet_ids = module.vpc.private_subnet_ids
  node_instance_type = var.node_instance_type
  node_min_size      = var.node_min_size
  node_max_size      = var.node_max_size
  node_desired_size  = var.node_desired_size
}

# -----------------------------------------------------------------------------
# IRSA — one role per service account
# -----------------------------------------------------------------------------

module "irsa_backend" {
  source = "../../modules/irsa"

  cluster_name            = var.cluster_name
  cluster_oidc_issuer_url = module.eks.cluster_oidc_issuer_url
  oidc_provider_arn       = module.eks.oidc_provider_arn
  namespace               = "production"
  service_account_name    = "backend"
  policy_arns             = [] # add e.g. AmazonS3ReadOnlyAccess when needed
}

module "irsa_frontend" {
  source = "../../modules/irsa"

  cluster_name            = var.cluster_name
  cluster_oidc_issuer_url = module.eks.cluster_oidc_issuer_url
  oidc_provider_arn       = module.eks.oidc_provider_arn
  namespace               = "production"
  service_account_name    = "frontend"
  policy_arns             = []
}

# No irsa_ebs_csi module — RDS + ElastiCache are used instead of in-cluster
# storage, so no EBS CSI driver / PVCs are needed (docs/infra-plan.md Phase 2).

# -----------------------------------------------------------------------------
# ECR — container registries for frontend and backend images
# -----------------------------------------------------------------------------

module "ecr" {
  source = "../../modules/ecr"

  project_name = var.project_name
}

# -----------------------------------------------------------------------------
# RDS Postgres
# -----------------------------------------------------------------------------

module "rds" {
  source = "../../modules/rds"

  project_name                   = var.project_name
  vpc_id                         = module.vpc.vpc_id
  private_subnet_ids             = module.vpc.private_subnet_ids
  eks_cluster_security_group_id  = module.eks.cluster_security_group_id
  engine_version                 = var.db_engine_version
  instance_class                 = var.db_instance_class
  allocated_storage              = var.db_allocated_storage
  db_name                        = var.db_name
  master_username                = var.db_master_username
  backup_retention_period        = var.db_backup_retention_period
}

# -----------------------------------------------------------------------------
# ElastiCache Redis
# -----------------------------------------------------------------------------

module "elasticache" {
  source = "../../modules/elasticache"

  project_name                   = var.project_name
  vpc_id                         = module.vpc.vpc_id
  private_subnet_ids             = module.vpc.private_subnet_ids
  eks_cluster_security_group_id  = module.eks.cluster_security_group_id
  node_type                      = var.redis_node_type
  engine_version                 = var.redis_engine_version
}

# -----------------------------------------------------------------------------
# ESO IRSA role — scoped to only the secrets it needs to read
# -----------------------------------------------------------------------------

data "aws_caller_identity" "current" {}

resource "aws_iam_policy" "eso_secrets_read" {
  name = "${var.project_name}-eso-secrets-read"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
        Resource = [
          "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${var.cloudflare_secret_name}-*",
          # github-pat-* is forward-looking for Phase 6's ArgoCD repo access — harmless to allow now, not yet consumed.
          "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:github-pat-*",
          # Auth0 credentials — seeded manually (Phase 5), same pattern as cloudflare-api-token.
          "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${var.auth0_secret_name}-*",
          # Shadow DB credentials secret (below) — stable name, unlike the RDS-managed
          # secret's auto-generated name/ARN which changes every recreate.
          "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${var.db_credentials_secret_name}-*",
        ]
      }
    ]
  })
}

# -----------------------------------------------------------------------------
# Shadow DB credentials secret — RDS's manage_master_user_password generates a
# secret with a random name/ARN on every instance creation, which a static k8s
# ExternalSecret manifest can't reference reliably across destroy/recreate
# cycles. Terraform already knows the *current* RDS secret's value on every
# apply, so it mirrors just the password into a stably-named secret that the
# ExternalSecret can always reference the same way.
# -----------------------------------------------------------------------------

data "aws_secretsmanager_secret_version" "rds_master" {
  secret_id = module.rds.master_user_secret_arn
}

resource "aws_secretsmanager_secret" "db_credentials" {
  name = var.db_credentials_secret_name
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    password = jsondecode(data.aws_secretsmanager_secret_version.rds_master.secret_string).password
  })
}

module "irsa_eso" {
  source = "../../modules/irsa"

  cluster_name            = var.cluster_name
  cluster_oidc_issuer_url = module.eks.cluster_oidc_issuer_url
  oidc_provider_arn       = module.eks.oidc_provider_arn
  namespace               = "external-secrets"
  service_account_name    = "external-secrets"
  policy_arns             = [aws_iam_policy.eso_secrets_read.arn]
}

# -----------------------------------------------------------------------------
# Cluster add-ons — ingress-nginx, ESO, cert-manager, external-dns, monitoring
# -----------------------------------------------------------------------------

module "cluster_addons" {
  source = "../../modules/cluster-addons"

  project_name            = var.project_name
  cluster_name            = var.cluster_name
  aws_region              = var.aws_region
  eso_irsa_role_arn       = module.irsa_eso.role_arn
  cloudflare_zone_name    = var.cloudflare_zone_name
  cloudflare_secret_name  = var.cloudflare_secret_name
  letsencrypt_email       = var.letsencrypt_email

  depends_on = [module.eks]
}

# -----------------------------------------------------------------------------
# GitHub Actions OIDC — CI push access to ECR, no long-lived AWS keys
# -----------------------------------------------------------------------------

module "github_oidc" {
  source = "../../modules/github-oidc"

  project_name = var.project_name
  github_org   = "Mik3asg"
  github_repo  = "multi-tenant-care-management-saas"

  ecr_repository_arns = [
    "arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/care-management/backend",
    "arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/care-management/frontend",
  ]
}
