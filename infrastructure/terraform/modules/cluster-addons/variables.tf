variable "project_name" {
  description = "Project name used for naming/tagging resources"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name — used as the external-dns TXT registry owner ID"
  type        = string
}

variable "aws_region" {
  description = "AWS region — passed to the ClusterSecretStore's SecretsManager provider config"
  type        = string
}

variable "eso_irsa_role_arn" {
  description = "IAM role ARN for the external-secrets service account (IRSA)"
  type        = string
}

variable "cloudflare_zone_name" {
  description = "Cloudflare-managed domain (e.g. example.com) — external-dns domain filter, cert-manager DNS01 zone"
  type        = string
}

variable "cloudflare_secret_name" {
  description = "Name of the Secrets Manager secret holding the Cloudflare API token (seeded manually, not Terraform-managed — see docs/infra-plan.md Phase 4)"
  type        = string
}

variable "letsencrypt_email" {
  description = "Email address for Let's Encrypt ACME registration"
  type        = string
}
