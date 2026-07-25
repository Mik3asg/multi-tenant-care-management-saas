output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = module.eks.cluster_endpoint
}

output "cluster_certificate_authority_data" {
  description = "Base64-encoded certificate authority data for the cluster"
  value       = module.eks.cluster_certificate_authority_data
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "backend_repository_url" {
  description = "URL of the backend ECR repository"
  value       = module.ecr.backend_repository_url
}

output "frontend_repository_url" {
  description = "URL of the frontend ECR repository"
  value       = module.ecr.frontend_repository_url
}

output "irsa_backend_role_arn" {
  description = "IAM role ARN for the backend service account"
  value       = module.irsa_backend.role_arn
}

output "irsa_frontend_role_arn" {
  description = "IAM role ARN for the frontend service account"
  value       = module.irsa_frontend.role_arn
}

output "db_address" {
  description = "RDS instance hostname"
  value       = module.rds.db_address
}

output "db_port" {
  description = "RDS instance port"
  value       = module.rds.db_port
}

output "db_master_user_secret_arn" {
  description = "Secrets Manager ARN holding the RDS-managed master password"
  value       = module.rds.master_user_secret_arn
}

output "redis_address" {
  description = "ElastiCache Redis hostname"
  value       = module.elasticache.redis_address
}

output "redis_port" {
  description = "ElastiCache Redis port"
  value       = module.elasticache.redis_port
}

output "github_actions_role_arn" {
  description = "IAM role ARN for GitHub Actions — set as the AWS_ROLE_ARN secret in the GitHub repo"
  value       = module.github_oidc.role_arn
}
