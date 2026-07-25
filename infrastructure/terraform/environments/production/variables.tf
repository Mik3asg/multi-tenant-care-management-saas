variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-2"
}

variable "project_name" {
  description = "Project name used for naming resources"
  type        = string
  default     = "carecloudly"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS control plane"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets, one per AZ"
  type        = list(string)
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets, one per AZ"
  type        = list(string)
}

variable "node_instance_type" {
  description = "EC2 instance type for the managed node group"
  type        = string
}

variable "node_min_size" {
  description = "Minimum number of nodes in the managed node group"
  type        = number
}

variable "node_max_size" {
  description = "Maximum number of nodes in the managed node group"
  type        = number
}

variable "node_desired_size" {
  description = "Desired number of nodes in the managed node group"
  type        = number
}

variable "db_engine_version" {
  description = "Postgres engine version"
  type        = string
}

variable "db_instance_class" {
  description = "RDS instance class (burstable, e.g. db.t4g.micro)"
  type        = string
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
}

variable "db_name" {
  description = "Initial Postgres database name"
  type        = string
}

variable "db_master_username" {
  description = "RDS master username (password is RDS-managed via Secrets Manager)"
  type        = string
}

variable "db_backup_retention_period" {
  description = "RDS automated backup retention period in days"
  type        = number
}

variable "redis_node_type" {
  description = "ElastiCache node type (e.g. cache.t4g.micro)"
  type        = string
}

variable "redis_engine_version" {
  description = "Redis engine version"
  type        = string
}

variable "cloudflare_zone_name" {
  description = "Cloudflare-managed domain used by external-dns and cert-manager DNS01 (e.g. example.com)"
  type        = string
}

variable "cloudflare_secret_name" {
  description = "Name of the Secrets Manager secret holding the Cloudflare API token (seeded manually, not Terraform-managed)"
  type        = string
  default     = "cloudflare-api-token"
}

variable "letsencrypt_email" {
  description = "Email address for Let's Encrypt ACME registration"
  type        = string
}

variable "auth0_secret_name" {
  description = "Name of the Secrets Manager secret holding Auth0 credentials (seeded manually, not Terraform-managed)"
  type        = string
  default     = "auth0-credentials"
}

variable "db_credentials_secret_name" {
  description = "Stable name for the Terraform-managed shadow secret mirroring the RDS master password (see main.tf comment)"
  type        = string
  default     = "care-db-credentials"
}